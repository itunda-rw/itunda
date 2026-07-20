package rw.itunda.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Real spacing/shape token set (2026-07-11) -- closes part of the gap
 * `docs/ARCHITECTURE.md`'s backlog names: "the rest of the token surface (spacing
 * scale, elevation, component shapes) matching the real documented TDS, not just
 * colors." Android's design system had `IdsColors`/`IdsSemanticColors`/
 * `IdsTypography`, but no spacing/shape layer at all -- every screen (mainly
 * `ItundaAppScreen.kt`) used raw `.dp` literals directly, unlike iOS's
 * `IDS.Layout`, which this file mirrors the naming convention of for cross-platform
 * consistency.
 *
 * Values here are Android's own real, already-established norms -- audited from
 * `ItundaAppScreen.kt`'s actual, already-in-use spacing (`grep`-counted frequency,
 * not invented): `20.dp`/`16.dp` screen padding appears identically at 4 separate
 * tab root call sites, `24.dp` is the single most common card corner radius (7
 * occurrences). This intentionally does not force Android's numbers to match iOS's
 * `IDS.Layout` 1:1 -- the two were tuned independently against the same reference
 * screenshots and mostly converge, but claiming exact parity where the real,
 * shipped code doesn't have it would be dishonest. This first pass wires the
 * highest-value, safest, most-repeated call sites (screen-level padding); a full
 * sweep of every remaining magic number in `ItundaAppScreen.kt` is real, separate,
 * larger follow-up scope, not done here.
 */
object IdsLayout {
    val screenHorizontal = 20.dp
    val screenVertical = 16.dp
    val sectionGap = 24.dp
    val cardGap = 16.dp
    val rowGap = 14.dp
    val inlineGap = 12.dp
    val tightGap = 8.dp

    val cardCornerRadius = 24.dp
    val sectionCornerRadius = 16.dp
    val chipCornerRadius = 20.dp
    val iconCornerRadius = 12.dp

    // Real fix (2026-07-13) for the other half of the "elevation" gap this file's
    // own header comment already named as open: Ids.colors.shadow existed but had
    // zero call sites anywhere in the Android app (confirmed via a repo-wide
    // design-token audit) -- every Card(...) in ItundaAppScreen.kt rendered
    // perfectly flat, unlike iOS's BankView.swift, which does apply a real shadow
    // (IDS.Colors.shadow, radius 8-10). A modest, subtle default -- Material3's
    // ElevatedCard convention starts at 1dp; 2dp gives a real, visible-but-subtle
    // lift without inventing an arbitrary large number.
    val cardElevation = 2.dp

    // Established this session (2026-07-11) fixing a real WCAG/Material touch-target
    // gap -- see docs/ACCESSIBILITY.md §3. Centralized here now rather than left as a
    // magic number local to TopIconButton.
    val minTouchTarget = 48.dp
}
