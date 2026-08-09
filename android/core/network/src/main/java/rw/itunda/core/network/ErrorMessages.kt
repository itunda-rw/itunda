package rw.itunda.core.network

import retrofit2.HttpException

// Relocated 2026-07-22 from app/ui/SuperAppTabs.kt while extracting Marketplace into
// :features:marketplace:impl -- this is a pure HttpException-to-message mapper with no
// Compose/UI dependency, shared by every feature area (Hood, Invest, Maps, Shop, Eats),
// so it moves next to NetworkClient itself rather than into the design-system module.
//
// Real backend-message pass-through (2026-08-10) -- the exact gap this function's own
// 2026-07-19 comment named ("A full message pass-through would need a broader
// networking-layer change") was actually closed the very next day for the P2P transfer
// flow specifically (see MainViewModel.backendErrorMessage's apiErrorMessage(e) call),
// but this far-more-widely-shared sibling was never updated to match -- every real,
// specific backend error message this app has written since then (bump-cooldown
// countdowns, price-offer validation, review-eligibility checks, etc.) was still being
// silently discarded in favor of a generic bucket for every caller of this function.
// Same fallback order as backendErrorMessage: the real message when the backend sent
// one, the honest generic bucket only when it didn't.
fun superAppErrorMessage(e: HttpException): String = apiErrorMessage(e) ?: when (e.code()) {
    400 -> "Please check what you entered and try again."
    401, 403 -> "You don't have access to do that."
    404 -> "That couldn't be found."
    409 -> "That's already been done, or is being processed."
    422 -> "Insufficient funds for this order."
    429 -> "Too many attempts -- please wait a moment and try again."
    else -> "Something went wrong. Please try again."
}
