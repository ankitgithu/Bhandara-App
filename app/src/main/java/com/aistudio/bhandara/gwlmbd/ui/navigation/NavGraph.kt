package com.aistudio.bhandara.gwlmbd.ui.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aistudio.bhandara.gwlmbd.R
import com.aistudio.bhandara.gwlmbd.data.auth.AuthManager
import com.aistudio.bhandara.gwlmbd.data.model.AppUpdateConfig
import com.aistudio.bhandara.gwlmbd.data.preferences.AppPreferences
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.AppUpdateDialog
import com.aistudio.bhandara.gwlmbd.ui.components.CitySelectorDialog
import com.aistudio.bhandara.gwlmbd.ui.components.LoginPromptDialog
import com.aistudio.bhandara.gwlmbd.ui.components.TelegramJoinDialog
import com.aistudio.bhandara.gwlmbd.util.AppUpdateManager
import kotlinx.coroutines.launch
import com.aistudio.bhandara.gwlmbd.ui.screens.add.AddBhandaraScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.add.AddBhandaraViewModel
import com.aistudio.bhandara.gwlmbd.ui.screens.admin.AdminScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.detail.BhandaraDetailScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.home.HomeScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.map.LocationPickerScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.meme.AddMemeScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.profile.ProfileScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.saved.SavedBhandarasScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.search.SearchScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.settings.SettingsScreen
import com.aistudio.bhandara.gwlmbd.ui.screens.welcome.WelcomeScreen
import com.aistudio.bhandara.gwlmbd.ui.theme.DeepRed

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem("nav_home", "होम", Icons.Filled.Home, Icons.Outlined.Home)
    object Search : BottomNavItem("nav_search", "खोज", Icons.Filled.Search, Icons.Outlined.Search)
    object Saved : BottomNavItem("nav_saved", "सेव किए", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder)
    object Profile : BottomNavItem("nav_profile", "प्रोफ़ाइल", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun BhandaraNavGraph(
    preferences: AppPreferences,
    authManager: AuthManager,
    repository: BhandaraRepository,
    onLoginRequested: () -> Unit,
    onLogoutRequested: () -> Unit,
    onThemeChanged: (String) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val currentUser by authManager.currentUser.collectAsState()
    val isGuest = currentUser == null

    // Scoped AddBhandaraViewModel retains form state across all navigation transitions!
    val addBhandaraViewModel: AddBhandaraViewModel = viewModel()

    val startDestination = "welcome"

    var showCitySelectorDialog by remember { mutableStateOf(false) }
    var showLoginPromptDialog by remember { mutableStateOf(false) }
    var startupUpdateConfig by remember { mutableStateOf<AppUpdateConfig?>(null) }

    // Telegram popup state after location selection (shown once per app launch session)
    var showTelegramDialog by remember { mutableStateOf(false) }
    var hasShownTelegramPopupThisSession by remember { mutableStateOf(false) }

    // Safe startup update checker (fires once on start, checks 24h cooldown unless mandatory)
    LaunchedEffect(Unit) {
        try {
            val config = repository.getAppUpdateConfig() ?: return@LaunchedEffect
            val installedCode = AppUpdateManager.getInstalledVersionCode(context)

            if (config.latestVersionCode > installedCode) {
                val isMandatory = config.isMandatory || installedCode < config.minimumSupportedVersionCode
                if (isMandatory) {
                    startupUpdateConfig = config.copy(isMandatory = true)
                } else {
                    val now = System.currentTimeMillis()
                    val lastCheck = preferences.lastUpdateCheckTime
                    val dismissedVersion = preferences.lastDismissedUpdateVersionCode
                    val oneDayMillis = 24 * 60 * 60 * 1000L

                    val shouldPrompt = (dismissedVersion != config.latestVersionCode) || (now - lastCheck >= oneDayMillis)
                    if (shouldPrompt) {
                        preferences.lastUpdateCheckTime = now
                        startupUpdateConfig = config
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NavGraph", "Startup update check error: ${e.message}")
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome Screen (Location Selection)
        composable("welcome") {
            WelcomeScreen(
                initialCity = preferences.selectedCity,
                onCitySelected = { city ->
                    try {
                        preferences.selectedCity = city
                        preferences.isWelcomeCompleted = true
                        if (!hasShownTelegramPopupThisSession) {
                            hasShownTelegramPopupThisSession = true
                            showTelegramDialog = true
                        } else {
                            navController.navigate("main_container") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NavGraph", "nav welcome to main_container failed: ${e.message}")
                    }
                }
            )
        }

        // Main App Container with Bottom Navigation
        composable("main_container") {
            val bottomNavController = rememberNavController()
            val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val items = listOf(
                BottomNavItem.Home,
                BottomNavItem.Search,
                BottomNavItem.Saved,
                BottomNavItem.Profile
            )

            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = DeepRed
                    ) {
                        items.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        try {
                                            bottomNavController.navigate(item.route) {
                                                popUpTo(bottomNavController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        } catch (e: Exception) {
                                            Log.e("NavGraph", "bottomNav navigate failed: ${e.message}")
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepRed,
                                    selectedTextColor = DeepRed,
                                    indicatorColor = DeepRed.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = bottomNavController,
                    startDestination = BottomNavItem.Home.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(BottomNavItem.Home.route) {
                        HomeScreen(
                            selectedCity = preferences.selectedCity,
                            onCityChangeRequested = { showCitySelectorDialog = true },
                            onCityQuickFilter = { newCity ->
                                preferences.selectedCity = newCity
                            },
                            onSearchClick = {
                                try {
                                    bottomNavController.navigate(BottomNavItem.Search.route)
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to search failed: ${e.message}")
                                }
                            },
                            onBhandaraClick = { bhandaraId ->
                                try {
                                    navController.navigate("detail/$bhandaraId")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to detail failed: ${e.message}")
                                }
                            },
                            onAddBhandaraClick = {
                                try {
                                    navController.navigate("add_bhandara")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to add_bhandara failed: ${e.message}")
                                }
                            },
                            onAddMemeClick = null,
                            isGuest = isGuest,
                            repository = repository,
                            currentTheme = preferences.themeSetting,
                            onThemeToggle = {
                                val nextTheme = if (preferences.themeSetting == "dark") "light" else "dark"
                                preferences.themeSetting = nextTheme
                                onThemeChanged(nextTheme)
                            }
                        )
                    }

                    composable(BottomNavItem.Search.route) {
                        SearchScreen(
                            currentCity = preferences.selectedCity,
                            onBackClick = {
                                try {
                                    bottomNavController.navigate(BottomNavItem.Home.route)
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav back to home failed: ${e.message}")
                                }
                            },
                            onBhandaraClick = { bhandaraId ->
                                try {
                                    navController.navigate("detail/$bhandaraId")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to detail failed: ${e.message}")
                                }
                            },
                            repository = repository
                        )
                    }

                    composable(BottomNavItem.Saved.route) {
                        SavedBhandarasScreen(
                            currentUser = currentUser,
                            isGuest = isGuest,
                            onLoginClick = onLoginRequested,
                            onBhandaraClick = { bhandaraId ->
                                try {
                                    navController.navigate("detail/$bhandaraId")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to detail failed: ${e.message}")
                                }
                            },
                            repository = repository
                        )
                    }

                    composable(BottomNavItem.Profile.route) {
                        ProfileScreen(
                            currentUser = currentUser,
                            isGuest = isGuest,
                            onLoginClick = onLoginRequested,
                            onLogoutClick = onLogoutRequested,
                            onSettingsClick = {
                                try {
                                    navController.navigate("settings")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to settings failed: ${e.message}")
                                }
                            },
                            onAdminClick = {
                                try {
                                    navController.navigate("admin")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to admin failed: ${e.message}")
                                }
                            },
                            onBhandaraClick = { bhandaraId ->
                                try {
                                    navController.navigate("detail/$bhandaraId")
                                } catch (e: Exception) {
                                    Log.e("NavGraph", "nav to detail failed: ${e.message}")
                                }
                            },
                            repository = repository
                        )
                    }
                }
            }
        }

        // Detail Screen
        composable(
            route = "detail/{bhandaraId}",
            arguments = listOf(navArgument("bhandaraId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bhandaraId = backStackEntry.arguments?.getString("bhandaraId") ?: ""
            BhandaraDetailScreen(
                bhandaraId = bhandaraId,
                currentUser = currentUser,
                isGuest = isGuest,
                onBackClick = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack failed: ${e.message}")
                    }
                },
                onRequireLogin = onLoginRequested,
                repository = repository
            )
        }

        // Add Bhandara Screen (Form state completely preserved in ViewModel!)
        composable("add_bhandara") {
            AddBhandaraScreen(
                viewModel = addBhandaraViewModel,
                currentUser = currentUser,
                onBackClick = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack failed: ${e.message}")
                    }
                },
                onOpenMapPicker = { _, _ ->
                    try {
                        navController.navigate("map_picker")
                    } catch (e: Exception) {
                        Log.e("NavGraph", "nav to map_picker failed: ${e.message}")
                    }
                },
                repository = repository,
                onSuccessSubmitted = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack failed: ${e.message}")
                    }
                }
            )
        }

        // Add Meme Screen (Admin Only Guard)
        composable("add_meme") {
            val user = currentUser
            if (user == null || !user.isAdmin()) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            } else {
                AddMemeScreen(
                    currentUser = user,
                    repository = repository,
                    onBackClick = {
                        try {
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Log.e("NavGraph", "popBackStack failed: ${e.message}")
                        }
                    },
                    onMemeAddedSuccess = {
                        try {
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Log.e("NavGraph", "popBackStack failed: ${e.message}")
                        }
                    }
                )
            }
        }

        // Dedicated Fullscreen Map Location Picker (Satellite default!)
        composable("map_picker") {
            LocationPickerScreen(
                initialLatitude = if (addBhandaraViewModel.latitude != 0.0) addBhandaraViewModel.latitude else 26.2183,
                initialLongitude = if (addBhandaraViewModel.longitude != 0.0) addBhandaraViewModel.longitude else 78.1828,
                onLocationConfirmed = { lat, lng, addr, areaName ->
                    try {
                        addBhandaraViewModel.setLocation(lat, lng, addr, areaName)
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack from map failed: ${e.message}")
                    }
                },
                onBackClick = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack from map failed: ${e.message}")
                    }
                }
            )
        }

        // Admin Panel (Protected)
        composable("admin") {
            AdminScreen(
                currentUser = currentUser,
                onBackClick = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack from admin failed: ${e.message}")
                    }
                },
                repository = repository
            )
        }

        // Settings Screen
        composable("settings") {
            SettingsScreen(
                preferences = preferences,
                currentUser = currentUser,
                isGuest = isGuest,
                onBackClick = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "popBackStack from settings failed: ${e.message}")
                    }
                },
                onLoginClick = onLoginRequested,
                onLogoutClick = onLogoutRequested,
                onThemeChanged = onThemeChanged,
                onCityChanged = { newCity ->
                    preferences.selectedCity = newCity
                },
                repository = repository
            )
        }
    }

    if (showCitySelectorDialog) {
        CitySelectorDialog(
            selectedCity = preferences.selectedCity,
            onCitySelected = { newCity ->
                preferences.selectedCity = newCity
            },
            onDismiss = { showCitySelectorDialog = false }
        )
    }

    if (showLoginPromptDialog) {
        LoginPromptDialog(
            onLoginClick = {
                showLoginPromptDialog = false
                onLoginRequested()
            },
            onContinueWithoutLogin = {
                showLoginPromptDialog = false
                try {
                    navController.navigate("add_bhandara")
                } catch (e: Exception) {
                    Log.e("NavGraph", "nav to add_bhandara failed: ${e.message}")
                }
            },
            onDismiss = { showLoginPromptDialog = false }
        )
    }

    if (startupUpdateConfig != null) {
        AppUpdateDialog(
            config = startupUpdateConfig!!,
            currentVersionName = AppUpdateManager.getInstalledVersionName(context),
            onDismiss = {
                preferences.lastDismissedUpdateVersionCode = startupUpdateConfig!!.latestVersionCode
                preferences.lastUpdateCheckTime = System.currentTimeMillis()
                startupUpdateConfig = null
            }
        )
    }

    // Optional Telegram Channel Join Popup (Shown after location selection, once per session)
    if (showTelegramDialog) {
        TelegramJoinDialog(
            onJoinClick = {
                showTelegramDialog = false
                try {
                    val telegramUrl = context.getString(R.string.telegram_channel_url)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("NavGraph", "Could not open telegram URL: ${e.message}")
                }
                try {
                    navController.navigate("main_container") {
                        popUpTo("welcome") { inclusive = true }
                    }
                } catch (e: Exception) {
                    Log.e("NavGraph", "nav to main_container failed: ${e.message}")
                }
            },
            onDismiss = {
                showTelegramDialog = false
                try {
                    navController.navigate("main_container") {
                        popUpTo("welcome") { inclusive = true }
                    }
                } catch (e: Exception) {
                    Log.e("NavGraph", "nav to main_container failed: ${e.message}")
                }
            }
        )
    }
}
