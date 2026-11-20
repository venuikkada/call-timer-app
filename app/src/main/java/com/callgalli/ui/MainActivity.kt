package com.callgalli.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callgalli.R
import com.callgalli.container
import com.callgalli.telecom.CallMonitorService
import com.callgalli.ui.allowed.AlwaysAllowedScreen
import com.callgalli.ui.blocked.BlockedLogScreen
import com.callgalli.ui.blocked.NumberHistoryScreen
import com.callgalli.ui.contacts.ContactsScreen
import com.callgalli.ui.home.HomeScreen
import com.callgalli.ui.onboarding.OnboardingScreen
import com.callgalli.ui.settings.SettingsScreen
import com.callgalli.ui.theme.CallGalliTheme

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    CONTACTS("contacts", R.string.tab_contacts, Icons.Outlined.Contacts),
    BLOCKED("blocked", R.string.tab_blocked, Icons.Outlined.PhoneDisabled),
    ALLOWED("allowed", R.string.tab_allowed, Icons.Outlined.VerifiedUser),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        // Start (or keep) the always-on call watcher. Starting from the foreground is always allowed.
        if (c.settings.current.onboardingDone) CallMonitorService.start(this)

        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            CallGalliTheme(settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!settings.onboardingDone) {
                        OnboardingScreen(onDone = {
                            c.settings.setOnboardingDone(true)
                            CallMonitorService.start(this)
                        })
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            if (tab != Tab.HOME) TopAppBar(
                title = {
                    Text(
                        when {
                            tab != null -> stringResource(if (tab == Tab.HOME) R.string.app_name else tab.label)
                            else -> stringResource(R.string.history_title)
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (tab == null) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (tab != null) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { nav.goToTab(t) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    contentPadding = padding,
                    onOpenSettings = { nav.goToTab(Tab.SETTINGS) },
                    onOpenNumber = { nav.navigate("history/$it") },
                )
            }
            composable(Tab.CONTACTS.route) { ContactsScreen(padding, context.container.settings) }
            composable(Tab.BLOCKED.route) { BlockedLogScreen(padding, onOpenNumber = { nav.navigate("history/$it") }) }
            composable(Tab.ALLOWED.route) { AlwaysAllowedScreen(padding) }
            composable(Tab.SETTINGS.route) { SettingsScreen(padding) }
            composable("history/{number}", arguments = listOf(navArgument("number") { type = NavType.StringType })) { entry ->
                NumberHistoryScreen(entry.arguments?.getString("number").orEmpty(), padding)
            }
        }
    }
}

private fun NavHostController.goToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
