package com.stockflip.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * Appens nya skal: tre flikar (Bevakningar · Marknad · Inställningar) och en detaljrutt.
 * Skärmarna injiceras som slots så att varje fas kan koppla in sin skärm utan att röra navigeringen.
 */
@Composable
fun AppShell(
    watchlist: @Composable () -> Unit,
    market: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    stockDetail: @Composable (symbol: String, onBack: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevelRoute(currentRoute)) {
                AppNavigationBar(currentRoute) { tab -> navController.navigateToTab(tab) }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WATCHLIST,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WATCHLIST) { watchlist() }
            composable(Routes.MARKET) { market() }
            composable(Routes.SETTINGS) { settings() }
            composable(
                route = Routes.STOCK_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_SYMBOL) { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString(Routes.ARG_SYMBOL).orEmpty()
                stockDetail(symbol) { navController.popBackStack() }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(currentRoute: String?, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        TopLevelTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Byter flik och bevarar respektive flikes tillstånd (standardmönstret för bottom nav). */
fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
