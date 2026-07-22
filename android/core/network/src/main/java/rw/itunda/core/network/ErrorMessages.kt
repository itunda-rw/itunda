package rw.itunda.core.network

import retrofit2.HttpException

// Relocated 2026-07-22 from app/ui/SuperAppTabs.kt while extracting Marketplace into
// :features:marketplace:impl -- this is a pure HttpException-to-message mapper with no
// Compose/UI dependency, shared by every feature area (Hood, Invest, Maps), so it moves
// next to NetworkClient itself rather than into the design-system module.
fun superAppErrorMessage(e: HttpException): String = when (e.code()) {
    // Real 400 case (found in a 2026-07-19 UX-copy sweep, prompted by the new price-offer
    // flow's own-offer/invalid-amount validation errors): a real, user-actionable input
    // problem was falling into the generic "Something went wrong" bucket below, unlike
    // bank-mfe which already surfaces the real backend validation message directly.
    // A full message pass-through would need a broader networking-layer change (Retrofit's
    // HttpException doesn't carry a typed body here) -- this generic-but-honest bucket
    // closes the gap for every existing 400 across the app, not just price offers.
    400 -> "Please check what you entered and try again."
    401, 403 -> "You don't have access to do that."
    404 -> "That couldn't be found."
    409 -> "That's already been done, or is being processed."
    422 -> "Insufficient funds for this order."
    429 -> "Too many attempts -- please wait a moment and try again."
    else -> "Something went wrong. Please try again."
}
