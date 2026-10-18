package com.gonzaroman.acornix.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gonzaroman.acornix.ui.tools.ControlHelpScreen
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ui.apps.AppsScreen
import com.gonzaroman.acornix.ui.assistant.AssistantScreen
import com.gonzaroman.acornix.ui.create.CreateScreen
import com.gonzaroman.acornix.ui.data.DataScreen
import com.gonzaroman.acornix.ui.editor.EditorScreen
import com.gonzaroman.acornix.ui.home.HomeScreen
import com.gonzaroman.acornix.ui.onboarding.OnboardingScreen
import com.gonzaroman.acornix.ui.settings.SettingsScreen
import com.gonzaroman.acornix.ui.share.ShareCardScreen
import com.gonzaroman.acornix.ui.tools.HealthScreen
import com.gonzaroman.acornix.ui.tools.HelpScreen
import com.gonzaroman.acornix.ui.tools.ImportExportScreen
import com.gonzaroman.acornix.ui.tools.StudioScreen
import com.gonzaroman.acornix.ui.versions.VersionsHubScreen
import com.gonzaroman.acornix.ui.versions.VersionsScreen

object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val ASSISTANT = "assistant?control={control}"
    const val SETTINGS = "settings"
    const val CREATE = "create?kind={kind}&improve={improve}"
    const val EDITOR = "editor/{id}"
    const val VERSIONS = "versions/{id}"
    const val VERSIONS_HUB = "versions"
    const val IMPORT_EXPORT = "import"
    const val HEALTH = "health"
    const val HELP = "help"
    const val CONTROL_HELP = "control_help"
    const val STUDIO = "studio"
    const val ONBOARDING = "onboarding"
    const val DATA = "data/{id}"
    const val SHARE = "share/{id}"

    fun assistant(control: Boolean = false) = "assistant?control=$control"
    fun create(feature: Boolean = false) = "create?kind=${if (feature) "feature" else "app"}"
    fun improve(appId: String) = "create?improve=${Uri.encode(appId)}"
    fun editor(appId: String) = "editor/${Uri.encode(appId)}"
    fun versions(appId: String) = "versions/${Uri.encode(appId)}"
    fun data(appId: String) = "data/${Uri.encode(appId)}"
    fun share(appId: String) = "share/${Uri.encode(appId)}"
}

private data class Tab(val route: String, val base: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "home", R.string.tab_home, Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.APPS, "apps", R.string.tab_apps, Icons.Outlined.Widgets, Icons.Rounded.Widgets),
    Tab(Routes.assistant(), "assistant", R.string.tab_assistant, Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    Tab(Routes.SETTINGS, "settings", R.string.tab_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/** Things other apps hand to Acornix (share sheet, notification taps). */
data class ExternalRequest(val openAssistant: Boolean = false, val sharedText: String? = null, val sharedImage: Uri? = null)

@Composable
fun AcornixApp(
    startOnboarding: Boolean,
    external: ExternalRequest?,
    onExternalConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val tabBase = route.substringBefore('?')
    val showBar = TABS.any { it.base == tabBase }

    LaunchedEffect(startOnboarding) {
        if (startOnboarding) nav.navigate(Routes.ONBOARDING) { launchSingleTop = true }
    }

    LaunchedEffect(external) {
        if (external != null && (external.openAssistant || external.sharedText != null || external.sharedImage != null)) {
            nav.navigate(Routes.assistant()) { launchSingleTop = true }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    TABS.forEach { tab ->
                        val selected = tab.base == tabBase
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                        onControlHelp = { nav.navigate(Routes.CONTROL_HELP) },
                    )
                }
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.APPS) { AppsScreen(nav) }
                composable(
                    Routes.ASSISTANT,
                    arguments = listOf(navArgument("control") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    AssistantScreen(
                        controlMode = entry.arguments?.getBoolean("control") ?: false,
                        external = external,
                        onExternalConsumed = onExternalConsumed,
                        onOpenSettings = { nav.navigateTab(Routes.SETTINGS) },
                        onOpenControlHelp = { nav.navigate(Routes.CONTROL_HELP) },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(
                    Routes.CREATE,
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType; defaultValue = "app" },
                        navArgument("improve") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    CreateScreen(
                        nav = nav,
                        feature = entry.arguments?.getString("kind") == "feature",
                        improveAppId = entry.arguments?.getString("improve"),
                    )
                }
                composable(Routes.EDITOR) { entry -> EditorScreen(nav, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VERSIONS) { entry -> VersionsScreen(nav, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VERSIONS_HUB) { VersionsHubScreen(nav) }
                composable(Routes.DATA) { entry -> DataScreen(nav, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.SHARE) { entry -> ShareCardScreen(nav, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.IMPORT_EXPORT) { ImportExportScreen(nav) }
                composable(Routes.HEALTH) { HealthScreen(nav) }
                composable(Routes.HELP) { HelpScreen(nav) }
                composable(Routes.CONTROL_HELP) { ControlHelpScreen(nav) }
                composable(Routes.STUDIO) { StudioScreen(nav) }
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
