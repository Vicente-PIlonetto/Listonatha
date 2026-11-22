package dev.viko.listaanimes.ui.nav

import androidx.compose.animation.core.animateFloat
import dev.viko.listaanimes.i18n.t
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.auth.SessionState
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.ui.components.LocalListStatus
import dev.viko.listaanimes.ui.components.LocalNavAnimScope
import dev.viko.listaanimes.ui.components.LocalQuickStatus
import dev.viko.listaanimes.ui.components.LocalSharedScope
import dev.viko.listaanimes.ui.components.LocalViewerId
import dev.viko.listaanimes.ui.components.QuickStatusSheet
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.screens.ActivityScreen
import dev.viko.listaanimes.ui.screens.BrowseScreen
import dev.viko.listaanimes.ui.screens.DetailScreen
import dev.viko.listaanimes.ui.screens.DiscoverScreen
import dev.viko.listaanimes.ui.screens.FollowsScreen
import dev.viko.listaanimes.ui.screens.ForumScreen
import dev.viko.listaanimes.ui.screens.GenreTagScreen
import dev.viko.listaanimes.ui.screens.HistoryScreen
import dev.viko.listaanimes.ui.screens.HomeScreen
import dev.viko.listaanimes.ui.screens.ListScreen
import dev.viko.listaanimes.ui.screens.LoginScreen
import dev.viko.listaanimes.ui.screens.MyReviewsScreen
import dev.viko.listaanimes.ui.screens.NotificationsScreen
import dev.viko.listaanimes.ui.screens.ProfileScreen
import dev.viko.listaanimes.ui.screens.ReviewEditorScreen
import dev.viko.listaanimes.ui.screens.ReviewReaderScreen
import dev.viko.listaanimes.ui.screens.ScheduleScreen
import dev.viko.listaanimes.ui.screens.SearchScreen
import dev.viko.listaanimes.ui.screens.SeasonScreen
import dev.viko.listaanimes.ui.screens.SettingsScreen
import dev.viko.listaanimes.ui.screens.SocialScreen
import dev.viko.listaanimes.ui.screens.ThreadScreen
import dev.viko.listaanimes.ui.screens.UserSearchScreen
import dev.viko.listaanimes.ui.screens.YouScreen
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("sem container") }

/** Navegação usada pelas telas. */
class Nav(private val c: NavHostController) {
    fun back() { c.popBackStack() }
    fun detail(id: Int, coverKey: String? = null) = c.navigate("detail/$id?k=${Uri.encode(coverKey.orEmpty())}")
    fun list(kind: MediaKind) = c.navigate("list/${kind.name}")
    fun search(kind: MediaKind) = c.navigate("search/${kind.name}")
    fun browse(preset: String, kind: MediaKind, country: String? = null, formats: List<String> = emptyList()) =
        c.navigate("browse/$preset/${kind.name}?country=${country.orEmpty()}&formats=${formats.joinToString(",")}")
    fun season(next: Boolean = false) = c.navigate("season?next=${if (next) 1 else 0}")
    fun genres(kind: MediaKind, genre: String? = null, tag: String? = null) =
        c.navigate("genres/${kind.name}?genre=${Uri.encode(genre.orEmpty())}&tag=${Uri.encode(tag.orEmpty())}")
    fun character(id: Int) = c.navigate("character/$id")
    fun reviewEditor(mediaId: Int) = c.navigate("review/$mediaId")
    fun reviewRead(id: Int) = c.navigate("reviewRead/$id")
    fun myReviews() = c.navigate("myReviews")
    fun history() = c.navigate("history")
    fun notifications() = c.navigate("notifications")
    fun settings(section: String? = null) = c.navigate(if (section == null) "settings" else "settings/$section")
    fun profile(id: Int) = c.navigate("profile?id=$id")
    fun profileByName(name: String) = c.navigate("profile?name=${Uri.encode(name)}")
    fun activity(id: Int) = c.navigate("activity/$id")
    fun follows(userId: Int, followers: Boolean) = c.navigate("follows/$userId/$followers")
    fun userSearch() = c.navigate("users")
    fun stats(userId: Int) = c.navigate("stats/$userId")
    fun forum(mediaId: Int? = null, title: String? = null) =
        c.navigate("forum?media=${mediaId ?: -1}&title=${Uri.encode(title.orEmpty())}")
    fun thread(id: Int) = c.navigate("thread/$id")
    fun customize() = c.navigate("customize")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", dev.viko.listaanimes.i18n.tc("tab", "Início"), Icons.Outlined.Home, Icons.Filled.Home),
    Tab("schedule", t("Agenda"), Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday),
    Tab("discover", t("Descobrir"), Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab("social", t("Social"), Icons.Outlined.Forum, Icons.Filled.Forum),
    Tab("you", t("Você"), Icons.Outlined.Person, Icons.Filled.Person),
)
private val tabRoutes = tabs.map { it.route }.toSet()

private fun isTab(e: NavBackStackEntry) = e.destination.route in tabRoutes

private const val TAB_MS = 240

// Transições: entre abas, só fade; para dentro, desliza com mola; voltar, o contrário.
private val slideSpring = spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset(1, 1))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTab(initialState) && isTab(targetState)) {
        // Entre abas a animação é feita no próprio conteúdo (veja screen()), sem camadas extras:
        // aqui só mantém a transição viva pelo tempo da animação.
        slideInVertically(tween(TAB_MS)) { 0 }
    }
    else slideInHorizontally(slideSpring) { it / 3 } + fadeIn(tween(200))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (isTab(initialState) && isTab(targetState)) slideOutVertically(tween(TAB_MS)) { 0 }
    else slideOutHorizontally(slideSpring) { -it / 8 } + fadeOut(tween(180))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(slideSpring) { -it / 8 } + fadeIn(tween(200))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(slideSpring) { it / 3 } + fadeOut(tween(160))

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(openMedia: Int?, onMediaOpened: () -> Unit) {
    val c = LocalContainer.current
    val session by c.auth.state.collectAsStateWithLifecycle()
    if (session == SessionState.LOGGED_OUT) {
        LoginScreen()
        return
    }
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val back by controller.currentBackStackEntryAsState()
    val route = back?.destination?.route
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val statusMap by remember(c) { c.lists.observeAll().map { l -> l.associate { it.entry.mediaId to it.entry.status } } }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val viewerId by c.settings.viewerId.collectAsStateWithLifecycle(initialValue = null)
    var quick by remember { mutableStateOf<Media?>(null) }
    // Obra recém-marcada como completa, esperando (ou não) uma nota.
    var rateAfter by remember { mutableStateOf<Media?>(null) }
    // Nota pelo toque longo (obra já na lista).
    var scoreFor by remember { mutableStateOf<Media?>(null) }
    val settingsNow = dev.viko.listaanimes.ui.components.LocalSettings.current

    // Links do AniList (em reviews, atividades e fórum) abrem dentro do app.
    val defaultUri = LocalUriHandler.current
    val uriHandler = remember(nav, defaultUri) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val m = Regex("""^https?://(?:www\.)?anilist\.co/(anime|manga|user|activity|forum/thread|character)/([^/?#]+)""").find(uri)
                if (m != null) {
                    val (kind, value) = m.destructured
                    val id = value.toIntOrNull()
                    when {
                        (kind == "anime" || kind == "manga") && id != null -> return nav.detail(id)
                        kind == "user" -> return nav.profileByName(Uri.decode(value))
                        kind == "activity" && id != null -> return nav.activity(id)
                        kind == "forum/thread" && id != null -> return nav.thread(id)
                        kind == "character" && id != null -> return nav.character(id)
                    }
                }
                runCatching { defaultUri.openUri(uri) }
            }
        }
    }

    LaunchedEffect(openMedia) {
        if (openMedia != null) { nav.detail(openMedia); onMediaOpened() }
    }

    CompositionLocalProvider(
        LocalListStatus provides statusMap,
        LocalViewerId provides viewerId,
        LocalQuickStatus provides { m: Media -> quick = m },
        dev.viko.listaanimes.ui.components.LocalScoreSaver provides { id: Int, raw: Int, sp: Int? ->
            scope.launch { c.db.entries().get(id)?.let { c.lists.save(it.copy(scoreRaw = raw), sp) } }
        },
        LocalUriHandler provides uriHandler,
    ) {
        // A barra de baixo fica por cima do conteúdo: animar a barra não força remedir a tela.
        // As abas reservam o espaço dela com um padding fixo (veja screen()).
        Box(Modifier.fillMaxSize().background(p.background)) {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedScope provides this) {
                    NavHost(
                        controller, startDestination = "home",
                        enterTransition = { enter() }, exitTransition = { exit() },
                        popEnterTransition = { popEnter() }, popExitTransition = { popExit() },
                    ) { graph(nav) }
                }
            }
                AnimatedVisibility(
                    visible = route == null || route in tabRoutes,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
                ) {
                    Column {
                        ThinDivider()
                        NavigationBar(containerColor = p.background, tonalElevation = 0.dp) {
                            tabs.forEach { t ->
                                val selected = route == t.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        controller.navigate(t.route) {
                                            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        // Ícone pula ao ser escolhido.
                                        val scale = remember { androidx.compose.animation.core.Animatable(1f) }
                                        LaunchedEffect(selected) {
                                            if (selected) { scale.snapTo(0.7f); scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f)) }
                                        }
                                        androidx.compose.animation.Crossfade(selected, label = "tab", modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) { s ->
                                            Icon(if (s) t.selectedIcon else t.icon, null, tint = if (s) p.accent else p.textTertiary)
                                        }
                                    },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = p.accent, selectedTextColor = p.accent,
                                        unselectedIconColor = p.textTertiary, unselectedTextColor = p.textTertiary,
                                        indicatorColor = p.accent.copy(alpha = 0.16f),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

        quick?.let { m ->
            val current = statusMap[m.id]
            QuickStatusSheet(
                media = m, current = current,
                onDismiss = { quick = null },
                onPick = { st ->
                    quick = null
                    scope.launch {
                        c.lists.add(m, st)
                        val scored = (c.db.entries().get(m.id)?.scoreRaw ?: 0) > 0
                        if (st == dev.viko.listaanimes.domain.ListStatus.COMPLETED && current != st && !scored && settingsNow.askScoreOnComplete) rateAfter = m
                    }
                },
                onRemove = { quick = null; scope.launch { c.lists.remove(m.id) } },
                onOpen = { quick = null; nav.detail(m.id) },
                onScore = { quick = null; scoreFor = m },
            )
        }
        scoreFor?.let { m ->
            val row by remember(m.id) { c.lists.observeEntry(m.id) }.collectAsStateWithLifecycle(initialValue = null)
            val e = row?.entry
            if (e != null) dev.viko.listaanimes.ui.components.ScorePickerSheet(
                initialRaw = e.scoreRaw, initialSpecial = row?.special?.position, system = settingsNow.primaryScore,
                subtitle = m.title(settingsNow.titleLanguage),
                onDismiss = { scoreFor = null },
                onSave = { picked -> scoreFor = null; scope.launch { c.lists.save(e.copy(scoreRaw = picked.raw), picked.special) } },
            )
        }
        rateAfter?.let { m ->
            dev.viko.listaanimes.ui.components.ScorePickerSheet(
                initialRaw = 0, initialSpecial = null, system = settingsNow.primaryScore,
                title = t("Que nota você dá?"),
                subtitle = m.title(settingsNow.titleLanguage) + t(" foi para Completo."),
                skipLabel = t("Agora não"),
                onDismiss = { rateAfter = null },
                onSave = { picked ->
                    rateAfter = null
                    scope.launch { c.db.entries().get(m.id)?.let { c.lists.save(it.copy(scoreRaw = picked.raw), picked.special) } }
                },
            )
        }
    }
}

/** Cada destino recebe o AnimatedVisibilityScope para as capas compartilhadas. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimScope provides this) {
        if (route in tabRoutes) {
            // Troca de aba: a nova sobe um pouco e aparece. A opacidade é aplicada direto no desenho
            // (ModulateAlpha), sem copiar a tela inteira para uma camada; é bem mais leve na GPU.
            val v by transition.animateFloat(
                transitionSpec = { tween(TAB_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing) }, label = "tab",
            ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            // Espaço fixo da barra de baixo (80dp + barra de navegação do sistema).
            Box(
                Modifier.fillMaxSize().padding(bottom = 81.dp).navigationBarsPadding().graphicsLayer {
                    alpha = v
                    translationY = (1f - v) * 18.dp.toPx()
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                },
            ) { content(entry) }
        } else content(entry)
    }
}

private fun NavGraphBuilder.graph(nav: Nav) {
    screen("home") { HomeScreen(nav) }
    screen("schedule") { ScheduleScreen(nav) }
    screen("discover") { DiscoverScreen(nav) }
    screen("social") { SocialScreen(nav) }
    screen("you") { YouScreen(nav) }
    screen("list/{kind}") { ListScreen(nav, MediaKind.valueOf(it.arguments!!.getString("kind")!!)) }
    screen("detail/{id}?k={k}", listOf(
        navArgument("id") { type = NavType.IntType },
        navArgument("k") { type = NavType.StringType; defaultValue = "" },
    )) { DetailScreen(nav, it.arguments!!.getInt("id"), it.arguments!!.getString("k")?.takeIf { k -> k.isNotBlank() }) }
    screen("search/{kind}") { SearchScreen(nav, MediaKind.valueOf(it.arguments!!.getString("kind")!!)) }
    screen(
        "browse/{preset}/{kind}?country={country}&formats={formats}",
        listOf(
            navArgument("country") { type = NavType.StringType; defaultValue = "" },
            navArgument("formats") { type = NavType.StringType; defaultValue = "" },
        ),
    ) {
        val a = it.arguments!!
        BrowseScreen(
            nav, a.getString("preset")!!, MediaKind.valueOf(a.getString("kind")!!),
            a.getString("country")?.takeIf { s -> s.isNotBlank() },
            a.getString("formats").orEmpty().split(",").filter { s -> s.isNotBlank() },
        )
    }
    screen("season?next={next}", listOf(navArgument("next") { type = NavType.IntType; defaultValue = 0 })) {
        SeasonScreen(nav, it.arguments!!.getInt("next") == 1)
    }
    screen("genres/{kind}?genre={genre}&tag={tag}", listOf(
        navArgument("genre") { type = NavType.StringType; defaultValue = "" },
        navArgument("tag") { type = NavType.StringType; defaultValue = "" },
    )) {
        val a = it.arguments!!
        GenreTagScreen(nav, MediaKind.valueOf(a.getString("kind")!!), a.getString("genre")?.takeIf { g -> g.isNotBlank() }, a.getString("tag")?.takeIf { t -> t.isNotBlank() })
    }
    screen("character/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
        dev.viko.listaanimes.ui.screens.CharacterScreen(nav, it.arguments!!.getInt("id"))
    }
    screen("review/{mediaId}", listOf(navArgument("mediaId") { type = NavType.IntType })) { ReviewEditorScreen(nav, it.arguments!!.getInt("mediaId")) }
    screen("reviewRead/{id}", listOf(navArgument("id") { type = NavType.IntType })) { ReviewReaderScreen(nav, it.arguments!!.getInt("id")) }
    screen("myReviews") { MyReviewsScreen(nav) }
    screen("history") { HistoryScreen(nav) }
    screen("notifications") { NotificationsScreen(nav) }
    screen("settings") { SettingsScreen(nav) }
    screen("settings/{section}") { SettingsScreen(nav, it.arguments?.getString("section")) }
    screen("profile?id={id}&name={name}", listOf(
        navArgument("id") { type = NavType.IntType; defaultValue = -1 },
        navArgument("name") { type = NavType.StringType; defaultValue = "" },
    )) {
        val id = it.arguments!!.getInt("id").takeIf { v -> v > 0 }
        val name = it.arguments!!.getString("name")?.takeIf { v -> v.isNotBlank() }
        ProfileScreen(nav, id, name)
    }
    screen("activity/{id}", listOf(navArgument("id") { type = NavType.IntType })) { ActivityScreen(nav, it.arguments!!.getInt("id")) }
    screen("follows/{userId}/{followers}", listOf(
        navArgument("userId") { type = NavType.IntType },
        navArgument("followers") { type = NavType.BoolType },
    )) { FollowsScreen(nav, it.arguments!!.getInt("userId"), it.arguments!!.getBoolean("followers")) }
    screen("users") { UserSearchScreen(nav) }
    screen("stats/{userId}", listOf(navArgument("userId") { type = NavType.IntType })) {
        dev.viko.listaanimes.ui.screens.StatsScreen(nav, it.arguments!!.getInt("userId"))
    }
    screen("customize") { dev.viko.listaanimes.ui.screens.CustomizeProfileScreen(nav) }
    screen("forum?media={media}&title={title}", listOf(
        navArgument("media") { type = NavType.IntType; defaultValue = -1 },
        navArgument("title") { type = NavType.StringType; defaultValue = "" },
    )) {
        ForumScreen(nav, it.arguments!!.getInt("media").takeIf { v -> v > 0 }, it.arguments!!.getString("title")?.takeIf { v -> v.isNotBlank() })
    }
    screen("thread/{id}", listOf(navArgument("id") { type = NavType.IntType })) { ThreadScreen(nav, it.arguments!!.getInt("id")) }
}

/** Abre o login do AniList no navegador. */
fun startLogin(context: android.content.Context, c: AppContainer) {
    context.startActivity(Intent(Intent.ACTION_VIEW, c.auth.authorizeUrl()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
