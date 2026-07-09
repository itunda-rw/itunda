package com.itunda.app.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Home : Screen("home")
    data object Pay : Screen("pay")
    data object Bills : Screen("bills")
    data object Stocks : Screen("stocks")
    data object Loans : Screen("loans")
    data object Benefits : Screen("benefits")
    data object Entire : Screen("entire")
    data object Savings : Screen("savings")
    data object Insurance : Screen("insurance")
    data object Transactions : Screen("transactions")
    data object Profile : Screen("profile")
    data object QR : Screen("qr")
    data object Analytics : Screen("analytics")
}
