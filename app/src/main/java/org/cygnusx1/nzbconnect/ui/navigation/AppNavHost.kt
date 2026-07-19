package org.cygnusx1.nzbconnect.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.cygnusx1.nzbconnect.R
import org.cygnusx1.nzbconnect.ui.queue.QueueScreen
import org.cygnusx1.nzbconnect.ui.search.SearchScreen
import org.cygnusx1.nzbconnect.ui.settings.SettingsScreen

private enum class TopDest(val route: String, val label: String, @param:DrawableRes val icon: Int) {
    Search("search", "Search", R.drawable.ic_search),
    Queue("queue", "Downloads", R.drawable.ic_list),
    Settings("settings", "Settings", R.drawable.ic_settings),
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                TopDest.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(dest.icon), contentDescription = dest.label) },
                        label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { innerPadding ->
        // Only consume the bottom (navigation bar) inset here; each screen's own TopAppBar
        // handles the top (status bar) inset. Applying the full innerPadding would inset the
        // status bar twice and leave dead space above the title.
        NavHost(
            navController = navController,
            startDestination = TopDest.Search.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(TopDest.Search.route) { SearchScreen() }
            composable(TopDest.Queue.route) {
                QueueScreen(onNavigateToSettings = {
                    navController.navigate(TopDest.Settings.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(TopDest.Settings.route) { SettingsScreen() }
        }
    }
}
