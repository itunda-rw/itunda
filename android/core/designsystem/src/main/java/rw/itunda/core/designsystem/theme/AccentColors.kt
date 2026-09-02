package rw.itunda.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// itunda's own category-accent colors for list-row icon badges (Bank hub product
// catalog, ledger transaction-type icons, etc.) -- NOT sourced from Toss's own TDS
// palette (see IdsColors.kt's own doc comment for that real, sourced scale); these
// are itunda-specific per-category distinguishers, same honest "not claimed as
// Toss-sourced" framing this codebase uses elsewhere for its own inventions.
//
// Promoted here from :app's ItundaAppScreen.kt (2026-09-02, Banking Feature-module
// decomposition slice 3) so both :app (LedgerFormatting.kt/BucketDetailScreen.kt)
// and :features:banking:impl (BankHubScreen, moving in this same slice) share one
// real definition instead of two independently-drifting copies.
val AccentIndigo = Color(0xFF7472F4)
val AccentTeal = Color(0xFF14AE85)
val AccentPurple = Color(0xFF7C5CFC)
val AccentOrange = Color(0xFFF2A93B)
