package com.healthhand.admin

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.ui.bookings.BookingsScreen
import com.healthhand.admin.ui.catalog.CatalogScreen
import com.healthhand.admin.ui.login.LoginScreen
import com.healthhand.admin.ui.stats.StatsScreen

object Routes {
    const val LOGIN = "login"
    const val BOOKINGS = "bookings"
    const val STATS = "stats"
    const val CATALOG = "catalog"
}

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem(Routes.BOOKINGS, R.string.nav_bookings, Icons.Filled.Book),
    NavItem(Routes.STATS, R.string.nav_stats, Icons.Filled.BarChart),
    NavItem(Routes.CATALOG, R.string.nav_catalog, Icons.Filled.Folder),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val forceLogout by ApiClient.forceLogout.collectAsStateWithLifecycle()
    val tokenStore = remember { ApiClient.tokenStore() }
    val hasToken = remember { tokenStore.getToken() != null }

    val startDest = if (hasToken) Routes.BOOKINGS else Routes.LOGIN

    LaunchedEffect(forceLogout) {
        if (forceLogout) {
            tokenStore.clearToken()
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            ApiClient.resetForcedLogout()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.BOOKINGS, Routes.STATS, Routes.CATALOG)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            Routes.BOOKINGS -> stringResource(R.string.bookings_title)
                            Routes.STATS -> stringResource(R.string.stats_title)
                            Routes.CATALOG -> stringResource(R.string.catalog_title)
                            else -> stringResource(R.string.app_name)
                        }
                    )
                },
                actions = {
                    if (showBottomBar) {
                        androidx.compose.material3.TextButton(onClick = {
                            tokenStore.clearToken()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }) {
                            Text(stringResource(R.string.logout))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoggedIn = {
                    navController.navigate(Routes.BOOKINGS) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                })
            }
            composable(Routes.BOOKINGS) { BookingsScreen(contentPadding = padding) }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.CATALOG) { CatalogScreen() }
        }
    }
}