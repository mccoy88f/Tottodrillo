package com.crocdb.friends.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.crocdb.friends.presentation.detail.RomDetailRoute
import com.crocdb.friends.presentation.explore.ExploreScreen
import com.crocdb.friends.presentation.home.HomeScreen
import com.crocdb.friends.presentation.search.SearchFiltersBottomSheet
import com.crocdb.friends.presentation.search.SearchScreen
import com.crocdb.friends.presentation.settings.DownloadSettingsScreen

/**
 * Sealed class per le route di navigazione
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search") {
        fun createRoute(platformCode: String? = null) = if (platformCode != null) {
            "search/platform/$platformCode"
        } else {
            "search"
        }
    }
    
    data object SearchWithPlatform : Screen("search/platform/{platformCode}") {
        fun createRoute(platformCode: String) = "search/platform/$platformCode"
    }
    data object Explore : Screen("explore")
    data object Settings : Screen("settings")
    data object RomDetail : Screen("rom_detail/{romSlug}") {
        fun createRoute(romSlug: String) = "rom_detail/$romSlug"
    }
}

/**
 * Navigation graph principale
 */
@Composable
fun CrocdbNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    initialRomSlug: String? = null,
    onOpenDownloadFolderPicker: () -> Unit = {},
    onRequestExtraction: (String, String, String) -> Unit = { _, _, _ -> } // archivePath, romTitle, romSlug
) {
    var showFilters by remember { mutableStateOf(false) }
    
    // Naviga alla ROM se l'app è stata aperta da una notifica
    LaunchedEffect(initialRomSlug) {
        initialRomSlug?.let { slug ->
            android.util.Log.d("NavGraph", "📱 Navigazione automatica a ROM: $slug")
            navController.navigate(Screen.RomDetail.createRoute(slug)) {
                // Pulisci lo stack di navigazione fino alla home
                popUpTo(Screen.Home.route) {
                    inclusive = false
                }
                // Evita duplicati
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToExplore = {
                    navController.navigate(Screen.Explore.route)
                },
                onNavigateToPlatform = { platformCode ->
                    navController.navigate(Screen.Search.createRoute(platformCode))
                },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                },
                onShowFilters = { showFilters = true },
                initialPlatformCode = null
            )

            if (showFilters) {
                SearchFiltersBottomSheet(
                    onDismiss = { showFilters = false }
                )
            }
        }
        
        composable(
            route = Screen.SearchWithPlatform.route,
            arguments = listOf(
                navArgument("platformCode") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val platformCode = backStackEntry.arguments?.getString("platformCode") ?: return@composable
            
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                },
                onShowFilters = { showFilters = true },
                initialPlatformCode = platformCode
            )

            if (showFilters) {
                SearchFiltersBottomSheet(
                    onDismiss = { showFilters = false }
                )
            }
        }

        composable(Screen.Explore.route) {
            ExploreScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlatform = { platformCode ->
                    navController.navigate(Screen.Search.createRoute(platformCode))
                }
            )
        }

        composable(
            route = Screen.RomDetail.route,
            arguments = listOf(
                navArgument("romSlug") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val romSlug = backStackEntry.arguments?.getString("romSlug") ?: return@composable

                    RomDetailRoute(
                        romSlug = romSlug,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPlatform = { platformCode ->
                            navController.navigate(Screen.Search.createRoute(platformCode))
                        },
                        onRequestExtraction = { archivePath, romTitle, romSlug ->
                            onRequestExtraction(archivePath, romTitle, romSlug)
                        }
                    )
        }

        composable(Screen.Settings.route) {
            DownloadSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectFolder = {
                    onOpenDownloadFolderPicker()
                }
            )
        }
    }
}
