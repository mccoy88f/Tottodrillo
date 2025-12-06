package com.tottodrillo.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tottodrillo.presentation.detail.RomDetailRoute
import com.tottodrillo.presentation.explore.ExploreScreen
import com.tottodrillo.presentation.home.HomeScreen
import com.tottodrillo.presentation.search.SearchFiltersBottomSheet
import com.tottodrillo.presentation.search.SearchScreen
import com.tottodrillo.presentation.settings.DownloadSettingsScreen

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
    data object Sources : Screen("sources")
    data object NoSources : Screen("no_sources")
    data object RomDetail : Screen("rom_detail/{romSlug}") {
        fun createRoute(romSlug: String) = "rom_detail/$romSlug"
    }
}

/**
 * Helper per gestire la navigazione indietro in modo sicuro
 * Se lo stack è vuoto o siamo già alla home, naviga alla home invece di lasciare una schermata vuota
 */
private fun NavHostController.safePopBackStack() {
    // Controlla se siamo già alla home
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute == Screen.Home.route) {
        // Siamo già alla home, non fare nulla
        return
    }
    
    // Prova a fare pop dello stack
    val popped = popBackStack()
    if (!popped) {
        // Lo stack è vuoto, naviga alla home
        navigate(Screen.Home.route) {
            // Pulisci tutto lo stack e naviga alla home
            popUpTo(0) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
}

/**
 * Navigation graph principale
 */
@Composable
fun TottodrilloNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    initialRomSlug: String? = null,
    onOpenDownloadFolderPicker: () -> Unit = {},
    onOpenEsDeFolderPicker: () -> Unit = {},
    onRequestStoragePermission: () -> Unit = {},
    onRequestExtraction: (String, String, String, String) -> Unit = { _, _, _, _ -> }, // archivePath, romTitle, romSlug, platformCode
    onInstallSource: () -> Unit = {},
    onSourcesStateChanged: () -> Unit = {},
    onHomeRefresh: () -> Unit = {},
    homeRefreshKey: Int = 0
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
            // Key per forzare il refresh della home quando cambiano le sorgenti
            // Usa direttamente homeRefreshKey passato da MainActivity
            var localHomeRefreshKey by remember { mutableStateOf(0) }
            
            // Incrementa la key quando si naviga alla home
            LaunchedEffect(navController.currentBackStackEntry?.id) {
                localHomeRefreshKey++
            }
            
            // Incrementa la key quando cambia homeRefreshKey (passato da MainActivity)
            // Questo viene triggerato quando si attiva/disattiva una sorgente
            LaunchedEffect(homeRefreshKey) {
                if (homeRefreshKey > 0) {
                    // Usa sempre il valore più alto per assicurarsi che il refresh venga sempre fatto
                    if (homeRefreshKey > localHomeRefreshKey) {
                        localHomeRefreshKey = homeRefreshKey
                        android.util.Log.d("NavGraph", "🔄 homeRefreshKey cambiato a $homeRefreshKey, aggiorno localHomeRefreshKey a $localHomeRefreshKey")
                    }
                }
            }
            
            HomeScreen(
                refreshKey = localHomeRefreshKey,
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
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                },
                onShowFilters = { showFilters = true },
                initialPlatformCode = null,
                refreshKey = homeRefreshKey
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
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToRomDetail = { romSlug ->
                    navController.navigate(Screen.RomDetail.createRoute(romSlug))
                },
                onShowFilters = { showFilters = true },
                initialPlatformCode = platformCode,
                refreshKey = homeRefreshKey
            )

            if (showFilters) {
                SearchFiltersBottomSheet(
                    onDismiss = { showFilters = false }
                )
            }
        }

        composable(Screen.Explore.route) {
            ExploreScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToPlatform = { platformCode ->
                    navController.navigate(Screen.Search.createRoute(platformCode))
                },
                refreshKey = homeRefreshKey
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
                        onNavigateBack = { navController.safePopBackStack() },
                        onNavigateToPlatform = { platformCode ->
                            navController.navigate(Screen.Search.createRoute(platformCode))
                        },
                        onRequestExtraction = { archivePath, romTitle, romSlug, platformCode ->
                            onRequestExtraction(archivePath, romTitle, romSlug, platformCode)
                        }
                    )
        }

        composable(Screen.Settings.route) {
            val scope = rememberCoroutineScope()
            
            DownloadSettingsScreen(
                onNavigateBack = { 
                    // Notifica che si sta tornando indietro (potrebbero essere cambiate le sorgenti)
                    // Delay per assicurarsi che le modifiche siano state salvate su disco
                    scope.launch {
                        kotlinx.coroutines.delay(500)
                        onSourcesStateChanged()
                    }
                    navController.safePopBackStack() 
                },
                onSelectFolder = {
                    onOpenDownloadFolderPicker()
                },
                onSelectEsDeFolder = {
                    onOpenEsDeFolderPicker()
                },
                onRequestStoragePermission = {
                    onRequestStoragePermission()
                },
                onInstallSource = {
                    onInstallSource()
                },
                onSourcesChanged = {
                    // Notifica immediatamente quando cambiano le sorgenti
                    // Questo viene chiamato quando si attiva/disattiva una sorgente
                    android.util.Log.d("NavGraph", "🔄 onSourcesChanged ricevuto, avvio coroutine")
                    scope.launch {
                        // Delay più lungo per assicurarsi che il salvataggio sia completato
                        kotlinx.coroutines.delay(500)
                        android.util.Log.d("NavGraph", "🔄 onSourcesChanged chiamato, notifico cambio stato")
                        onSourcesStateChanged() // Ricontrolla lo stato delle sorgenti
                        // Forza anche il refresh della home
                        android.util.Log.d("NavGraph", "🔄 Chiamo onHomeRefresh()")
                        onHomeRefresh() // Incrementa homeRefreshTrigger
                        android.util.Log.d("NavGraph", "🔄 Sorgenti cambiate, forzo refresh home")
                    }
                }
            )
        }
    }
}
