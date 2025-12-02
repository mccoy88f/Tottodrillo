package com.crocdb.friends.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crocdb.friends.domain.model.Rom
import com.crocdb.friends.presentation.explore.ExploreScreen
import com.crocdb.friends.presentation.home.HomeScreen
import com.crocdb.friends.presentation.search.SearchFiltersBottomSheet
import com.crocdb.friends.presentation.search.SearchScreen

/**
 * Sealed class per le route di navigazione
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Explore : Screen("explore")
    data object RomDetail : Screen("rom_detail/{romSlug}") {
        fun createRoute(romSlug: String) = "rom_detail/$romSlug"
    }
    data object PlatformRoms : Screen("platform_roms/{platformCode}") {
        fun createRoute(platformCode: String) = "platform_roms/$platformCode"
    }
}

/**
 * Navigation graph principale
 */
@Composable
fun CrocdbNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    var showFilters by remember { mutableStateOf(false) }

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
                    navController.navigate(Screen.PlatformRoms.createRoute(platformCode))
                },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                },
                onShowFilters = { showFilters = true }
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
                    navController.navigate(Screen.PlatformRoms.createRoute(platformCode))
                }
            )
        }

        // TODO: Implementare le altre schermate
        // - RomDetailScreen
        // - PlatformRomsScreen
        // - FavoritesScreen
        // - DownloadsScreen
    }
}
