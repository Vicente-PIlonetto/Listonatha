import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.apollo)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val anilistClientId: String = localProps.getProperty("ANILIST_CLIENT_ID", "").trim()

android {
    namespace = "dev.viko.listaanimes"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.viko.listaanimes"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        resourceConfigurations += listOf("pt-rBR", "pt")
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"$anilistClientId\"")
        // Links opcionais da página Sobre (local.properties): repositório e apoio ao projeto.
        buildConfigField("String", "REPO_URL", "\"${localProps.getProperty("REPO_URL", "").trim()}\"")
        buildConfigField("String", "DONATE_URL", "\"${localProps.getProperty("DONATE_URL", "").trim()}\"")
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
        manifestPlaceholders["authScheme"] = "listaanimes"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Uso pessoal: assina o release com a chave de debug para facilitar a instalação.
            signingConfig = signingConfigs.getByName("debug")
            // Celulares são ARM; tirar x86 corta as bibliotecas nativas do ML Kit pela metade.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Nome do APK: ListaAnimes-<versão>-release.apk / ListaAnimes-<versão>-debug.apk
base.archivesName.set("Listonatha-${android.defaultConfig.versionName}")

apollo {
    service("anilist") {
        packageName.set("dev.viko.listaanimes.graphql")
        srcDir("src/main/graphql")
        mapScalarToKotlinString("CountryCode")
        mapScalarToKotlinLong("FuzzyDateInt")
        mapScalar("Json", "kotlin.Any", "com.apollographql.apollo.api.AnyAdapter")
        introspection {
            endpointUrl.set("https://graphql.anilist.co")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.mlkit.translate)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.apollo.runtime)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
