package com.itunda.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itunda.app.ui.screens.auth.AuthScreen
import com.itunda.app.ui.screens.benefits.BenefitsScreen
import com.itunda.app.ui.screens.bills.BillsScreen
import com.itunda.app.ui.screens.entire.EntireScreen
import com.itunda.app.ui.screens.home.HomeScreen
import com.itunda.app.ui.screens.loans.LoansScreen
import com.itunda.app.ui.screens.pay.PayScreen
import com.itunda.app.ui.screens.stocks.StocksScreen
import com.itunda.app.ui.screens.savings.SavingsScreen
import com.itunda.app.ui.screens.insurance.InsuranceScreen
import com.itunda.app.ui.screens.transactions.TransactionsScreen
import com.itunda.app.ui.screens.profile.ProfileScreen
import com.itunda.app.ui.screens.qr.QRScreen
import com.itunda.app.ui.screens.analytics.AnalyticsScreen
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.AuthViewModel

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Outlined.Home,                       Icons.Filled.Home,                       Screen.Home.route),
    BottomNavItem("Benefits", Icons.Outlined.CardGiftcard,               Icons.Filled.CardGiftcard,               Screen.Benefits.route),
    BottomNavItem("Transfer", Icons.AutoMirrored.Outlined.Send,          Icons.AutoMirrored.Filled.Send,          Screen.Pay.route),
    BottomNavItem("Invest",   Icons.AutoMirrored.Outlined.TrendingUp,    Icons.AutoMirrored.Filled.TrendingUp,    Screen.Stocks.route),
    BottomNavItem("All",      Icons.Outlined.GridView,                   Icons.Filled.GridView,                   Screen.Entire.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItundaNavGraph() {
    val navController   = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    ItundaTheme {
        if (authState.isLoggedIn) {
            val navBackStackEntry  by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar      = currentDestination?.route in bottomNavItems.map { it.route }

            Scaffold(
                containerColor = Background,
                bottomBar = {
                    if (showBottomBar) {
                        // ── Toss-exact bottom nav ──────────────────────────
                        // White bg · 1 dp hairline on top · no indicator pill
                        Column {
                            // Hairline divider (Toss signature)
                            Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                                drawLine(
                                    color       = Color(0xFFECEFF3),
                                    start       = Offset(0f, 0f),
                                    end         = Offset(size.width, 0f),
                                    strokeWidth = 1f
                                )
                            }
                            NavigationBar(
                                containerColor = Color.White,
                                tonalElevation = 0.dp,
                                modifier       = Modifier.height(63.dp)
                            ) {
                                bottomNavItems.forEach { item ->
                                    val selected = currentDestination?.hierarchy?.any {
                                        it.route == item.route
                                    } == true
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector        = if (selected) item.selectedIcon else item.icon,
                                                contentDescription = item.label,
                                                modifier           = Modifier.size(24.dp)
                                            )
                                        },
                                        label = {
                                            Text(item.label, fontSize = 10.sp)
                                        },
                                        selected = selected,
                                        onClick  = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState    = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor   = Primary,
                                            selectedTextColor   = Primary,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary,
                                            indicatorColor      = Color.Transparent  // no pill
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                NavHost(
                    navController      = navController,
                    startDestination   = Screen.Home.route,
                    modifier           = Modifier.padding(paddingValues),
                    enterTransition    = { fadeIn(animationSpec = tween(200)) },
                    exitTransition     = { fadeOut(animationSpec = tween(150)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition  = { fadeOut(animationSpec = tween(150)) }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onNavigateToPay          = { navController.navigate(Screen.Pay.route) },
                            onNavigateToBills        = { navController.navigate(Screen.Bills.route) },
                            onNavigateToStocks       = { navController.navigate(Screen.Stocks.route) },
                            onNavigateToLoans        = { navController.navigate(Screen.Loans.route) },
                            onNavigateToQR           = { navController.navigate(Screen.QR.route) },
                            onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                            onNavigateToAnalytics    = { navController.navigate(Screen.Analytics.route) },
                            onNavigateToSavings      = { navController.navigate(Screen.Savings.route) },
                            onNavigateToInsurance    = { navController.navigate(Screen.Insurance.route) },
                            onLogout = {
                                authViewModel.logout()
                                navController.navigate(Screen.Auth.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Pay.route)      { PayScreen() }
                    composable(Screen.Bills.route)    { BillsScreen() }
                    composable(Screen.Stocks.route)   { StocksScreen() }
                    composable(Screen.Loans.route)    { LoansScreen() }
                    composable(Screen.Benefits.route) { BenefitsScreen() }
                    composable(Screen.Entire.route) {
                        EntireScreen(
                            onNavigateToPay       = { navController.navigate(Screen.Pay.route) },
                            onNavigateToBills     = { navController.navigate(Screen.Bills.route) },
                            onNavigateToStocks    = { navController.navigate(Screen.Stocks.route) },
                            onNavigateToLoans     = { navController.navigate(Screen.Loans.route) },
                            onNavigateToSavings   = { navController.navigate(Screen.Savings.route) },
                            onNavigateToInsurance = { navController.navigate(Screen.Insurance.route) },
                            onNavigateToQR        = { navController.navigate(Screen.QR.route) },
                            onNavigateToProfile   = { navController.navigate(Screen.Profile.route) }
                        )
                    }
                    composable(Screen.Savings.route)      { SavingsScreen() }
                    composable(Screen.Insurance.route)    { InsuranceScreen() }
                    composable(Screen.Transactions.route) { TransactionsScreen() }
                    composable(Screen.QR.route)            { QRScreen() }
                    composable(Screen.Analytics.route)     { AnalyticsScreen() }
                    composable(Screen.Profile.route) {
                        ProfileScreen(
                            onLogout = {
                                authViewModel.logout()
                                navController.navigate(Screen.Auth.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }

        } else {
            NavHost(navController = navController, startDestination = Screen.Auth.route) {
                composable(Screen.Auth.route) {
                    AuthScreen(
                        authViewModel  = authViewModel,
                        onLoginSuccess = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Auth.route) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
