package rw.itunda.app.ui

import androidx.compose.runtime.Composable
import rw.itunda.feature.wealth.impl.InvestScreen as WealthInvestScreen

/**
 * Compatibility entry point for the app shell.
 *
 * The canonical investing UI lives in :features:wealth:impl.
 */
@Composable
fun InvestScreen(onBack: () -> Unit) {
    WealthInvestScreen(onBack = onBack)
}
