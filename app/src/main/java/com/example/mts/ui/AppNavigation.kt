package com.example.mts.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.example.mts.R
import com.example.mts.SmartPantryApp
import com.example.mts.ui.auth.ForgotScreen
import com.example.mts.ui.auth.LoginScreen
import com.example.mts.ui.auth.RegisterScreen
import com.example.mts.ui.common.LocalSnackbar
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.discover.DiscoverScreen
import com.example.mts.ui.discover.GenerateScreen
import com.example.mts.ui.expiry.ExpiryScreen
import com.example.mts.ui.expiry.HistoryScreen
import com.example.mts.ui.favorites.FavoritesScreen
import com.example.mts.ui.home.HomeScreen
import com.example.mts.ui.pantry.IngredientFormScreen
import com.example.mts.ui.pantry.PantryScreen
import com.example.mts.ui.planner.MealPickerScreen
import com.example.mts.ui.planner.PlannerScreen
import com.example.mts.ui.profile.PreferencesScreen
import com.example.mts.ui.profile.ProfileScreen
import com.example.mts.ui.recipe.CookingScreen
import com.example.mts.ui.recipe.RecipeScreen
import com.example.mts.ui.scan.ScanScreen
import com.example.mts.ui.shopping.ShoppingScreen
import com.example.mts.ui.theme.Leaf
import com.example.mts.ui.theme.LeafSoft
import com.example.mts.work.ReminderWorker

private object Route {
    const val HOME = "home"
    const val PANTRY = "pantry"
    const val DISCOVER = "discover?q={q}&ingredients={ingredients}"
    const val PLANNER = "planner"
    const val PROFILE = "profile"

    fun discover(query: String = "", ingredients: List<String> = emptyList()) =
        "discover?q=${Uri.encode(query)}&ingredients=${Uri.encode(ingredients.joinToString(","))}"
}

private enum class Tab(val route: String, val target: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME(Route.HOME, Route.HOME, R.string.nav_home, Icons.Filled.Home),
    PANTRY(Route.PANTRY, Route.PANTRY, R.string.nav_pantry, Icons.Filled.Inventory2),
    DISCOVER(Route.DISCOVER, Route.discover(), R.string.nav_discover, Icons.Filled.Explore),
    PLANNER(Route.PLANNER, Route.PLANNER, R.string.nav_planner, Icons.Filled.CalendarMonth),
    PROFILE(Route.PROFILE, Route.PROFILE, R.string.nav_profile, Icons.Filled.Person),
}

private fun NavHostController.switchTab(target: String) = navigate(target) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = target != Route.discover()
}

@Composable
fun SmartPantryRoot() {
    val app = LocalContext.current.applicationContext as SmartPantryApp
    val signedIn by app.tokens.isSignedIn.collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        // Screens lay out their own insets, so the snackbar floats above them.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (signedIn) {
                null -> LoadingState()
                false -> AuthFlow()
                true -> MainFlow()
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp),
            )
        }
    }
}

@Composable
private fun AuthFlow() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "login") {
        composable("login") {
            LoginScreen(onRegister = { nav.navigate("register") }, onForgot = { nav.navigate("forgot") })
        }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
        composable("forgot") { ForgotScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainFlow() {
    val context = LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        ReminderWorker.schedule(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.target) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Leaf,
                                selectedTextColor = Leaf,
                                indicatorColor = LeafSoft,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val isTab = Tab.entries.any { it.route == route }
        val tabInsets = if (isTab) Modifier.statusBarsPadding() else Modifier
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).then(tabInsets)) {
            NavHost(
                nav,
                startDestination = Route.HOME,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                val openRecipe = { key: String -> nav.navigate("recipe/$key") }
                val findRecipes = { names: List<String> -> nav.switchTab(Route.discover(ingredients = names)) }

                composable(Route.HOME) {
                    HomeScreen(
                        onSearch = { nav.switchTab(Route.discover(query = it)) },
                        onScan = { nav.navigate("scan") },
                        onAddManual = { nav.navigate("ingredient") },
                        onPantry = { nav.switchTab(Route.PANTRY) },
                        onRecipe = openRecipe,
                        onExpiry = { nav.navigate("expiry") },
                        onShopping = { nav.navigate("shopping") },
                    )
                }
                composable(Route.PANTRY) {
                    PantryScreen(
                        onAdd = { nav.navigate("ingredient") },
                        onEdit = { nav.navigate("ingredient?itemId=$it") },
                        onFindRecipes = findRecipes,
                    )
                }
                composable(
                    Route.DISCOVER,
                    arguments = listOf(
                        navArgument("q") { type = NavType.StringType; defaultValue = "" },
                        navArgument("ingredients") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { backStack ->
                    DiscoverScreen(
                        initialQuery = backStack.arguments?.getString("q").orEmpty(),
                        initialIngredients = backStack.arguments?.getString("ingredients").orEmpty()
                            .split(',').filter { it.isNotBlank() },
                        onRecipe = openRecipe,
                        onGenerate = { nav.navigate("generate") },
                    )
                }
                composable(Route.PLANNER) {
                    PlannerScreen(
                        onPick = { date, type -> nav.navigate("pick/$date/$type") },
                        onRecipe = openRecipe,
                    )
                }
                composable(Route.PROFILE) {
                    ProfileScreen(
                        onPreferences = { nav.navigate("preferences") },
                        onFavorites = { nav.navigate("favorites") },
                        onShopping = { nav.navigate("shopping") },
                        onHistory = { nav.navigate("history") },
                        onExpiry = { nav.navigate("expiry") },
                    )
                }
                composable("scan") {
                    ScanScreen(
                        onBack = { nav.popBackStack() },
                        onDone = { nav.popBackStack() },
                        onFindRecipes = findRecipes,
                    )
                }
                composable(
                    "ingredient?itemId={itemId}",
                    arguments = listOf(
                        navArgument("itemId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { IngredientFormScreen(onDone = { nav.popBackStack() }) }
                composable("generate") {
                    GenerateScreen(onBack = { nav.popBackStack() }, onRecipe = openRecipe)
                }
                composable(
                    "recipe/{key}",
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                ) {
                    RecipeScreen(onBack = { nav.popBackStack() }, onCook = { nav.navigate("cook/$it") })
                }
                composable(
                    "cook/{key}",
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                ) { CookingScreen(onExit = { nav.popBackStack() }) }
                composable("favorites") { FavoritesScreen(onBack = { nav.popBackStack() }, onRecipe = openRecipe) }
                composable("shopping") { ShoppingScreen(onBack = { nav.popBackStack() }) }
                composable("expiry") { ExpiryScreen(onBack = { nav.popBackStack() }, onFindRecipes = findRecipes) }
                composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
                composable("preferences") { PreferencesScreen(onBack = { nav.popBackStack() }) }
                composable(
                    "pick/{date}/{mealType}",
                    arguments = listOf(
                        navArgument("date") { type = NavType.StringType },
                        navArgument("mealType") { type = NavType.StringType },
                    ),
                ) {
                    MealPickerScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                }
            }
        }
    }
}
