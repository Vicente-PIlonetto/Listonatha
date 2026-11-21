package dev.viko.listaanimes.data.api

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.okHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class ApiErrorKind { NETWORK, AUTH, RATE_LIMIT, SERVER, REJECTED }

class ApiException(val kind: ApiErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Mensagem para a tela: o que aconteceu e o que fazer. */
    val userMessage: String
        get() = when (kind) {
            ApiErrorKind.NETWORK -> dev.viko.listaanimes.i18n.t("Sem conexão com o AniList.")
            ApiErrorKind.AUTH -> dev.viko.listaanimes.i18n.t("Sua sessão expirou. Entre de novo.")
            ApiErrorKind.RATE_LIMIT -> dev.viko.listaanimes.i18n.t("O AniList pediu para esperar um minuto.")
            ApiErrorKind.SERVER -> dev.viko.listaanimes.i18n.t("O AniList não respondeu.")
            ApiErrorKind.REJECTED -> dev.viko.listaanimes.i18n.t("O AniList recusou a alteração: {0}", message)
        }
}

class AniListApi(
    tokenProvider: () -> String?,
    onUnauthorized: () -> Unit,
    val rateLimit: RateLimitState = RateLimitState(),
) {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val token = tokenProvider()
            val req = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "ListaAnimes/${dev.viko.listaanimes.BuildConfig.VERSION_NAME} (Android)")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            val resp = chain.proceed(req)
            if (token != null && (resp.code == 401 || (resp.code == 400 && resp.peekBody(2048).string().contains("Invalid token", true)))) {
                onUnauthorized()
            }
            resp
        })
        .addInterceptor(RateLimitInterceptor(rateLimit))
        .build()

    val apollo: ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .okHttpClient(okHttp)
        // Para ler a mensagem do AniList em respostas 400/404 (ex.: "Invalid token").
        .httpExposeErrorBody(true)
        .build()

    /** Executa e devolve os dados, ou lança [ApiException] com o tipo de erro. */
    suspend fun <D : Operation.Data> run(call: ApolloCall<D>): D {
        val response = call.execute()
        val ex = response.exception
        if (ex != null) throw classify(ex)
        val errors = response.errors.orEmpty()
        val data = response.data
        if (errors.isNotEmpty() && (data == null || call.operation is com.apollographql.apollo.api.Mutation<*>)) {
            val msg = errors.joinToString("; ") { it.message }
            val status = errors.firstNotNullOfOrNull { (it.extensions?.get("status") as? Number)?.toInt() }
            throw when {
                msg.contains("Invalid token", true) || msg.contains("Unauthorized", true) || status == 401 ->
                    ApiException(ApiErrorKind.AUTH, msg)
                status == 429 || msg.contains("Too Many Requests", true) -> ApiException(ApiErrorKind.RATE_LIMIT, msg)
                status != null && status >= 500 -> ApiException(ApiErrorKind.SERVER, msg)
                else -> ApiException(ApiErrorKind.REJECTED, msg)
            }
        }
        return data ?: throw ApiException(ApiErrorKind.SERVER, "Resposta vazia")
    }

    private fun classify(e: Throwable): ApiException = when (e) {
        is ApolloHttpException -> {
            val body = runCatching { e.body?.readUtf8() }.getOrNull().orEmpty()
            when {
                e.statusCode == 401 || body.contains("Invalid token", true) -> ApiException(ApiErrorKind.AUTH, "HTTP ${e.statusCode}", e)
                e.statusCode == 429 -> ApiException(ApiErrorKind.RATE_LIMIT, "HTTP 429", e)
                e.statusCode >= 500 -> ApiException(ApiErrorKind.SERVER, "HTTP ${e.statusCode}", e)
                else -> ApiException(ApiErrorKind.REJECTED, extractMessage(body) ?: "HTTP ${e.statusCode}", e)
            }
        }
        is ApolloNetworkException -> ApiException(ApiErrorKind.NETWORK, e.message ?: "rede", e)
        else -> ApiException(ApiErrorKind.NETWORK, e.message ?: e.toString(), e)
    }

    private fun extractMessage(body: String): String? =
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
}
