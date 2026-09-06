import Foundation

// Small, module-wide helpers extracted out of EatsScreen.swift (2026-09-06, Eats
// product-completeness pass) once that file crossed the file-size-lint 500-line
// guideline for the first time.

// eatsGoodPointOptions/eatsGoodPointLabels -- FeatureMaps already has its own real
// local copy of this exact list (MapPlaceDetailPanel.swift, since MapsPlaceDetailService
// reuses EatsReviewService's rating/tag data on the backend), but a Feature module
// can't import another Feature's impl module directly (scripts/ios-silo-boundary-
// check.py), only its Interface -- and promoting this to FeatureMapsInterface would be
// a backwards home for genuinely Eats-owned vocabulary. Same real "each
// platform/module independently re-implements what it needs" convention
// MapPlaceDetailPanel.swift's own copy (itself ported from Android's
// MapPlaceDetailExtraTabs.kt) already established -- matches
// EatsReviewService.EATS_GOOD_POINTS on the backend exactly.
let eatsGoodPointOptions: [(String, String)] = [
    ("GREAT_FOOD", "🍽️ Great food"), ("GREAT_DESSERT", "🍰 Great dessert"), ("NICE_INTERIOR", "🛋️ Nice interior"),
    ("GREAT_DRINKS", "🥤 Great drinks"), ("GOOD_FOR_CONVERSATION", "💬 Good for conversation"),
]
let eatsGoodPointLabels: [String: String] = Dictionary(uniqueKeysWithValues: eatsGoodPointOptions)

// TalkScreen.errorMessage (App-only, 23 other real callers) isn't reachable from a
// Feature module, so this Feature keeps its own local copy -- same real precedent
// FeatureMy's own MyTabView.swift already established. Internal (not private), since
// 6 files across this module call it.
func errorMessage(_ statusCode: Int) -> String {
    switch statusCode {
    case 400: return "Please check what you entered and try again."
    case 401, 403: return "You don't have access to do that."
    case 404: return "That couldn't be found."
    case 409: return "That's already been done, or is being processed."
    case 422: return "Insufficient funds for this order."
    case 429: return "Too many attempts -- please wait a moment and try again."
    default: return "Something went wrong. Please try again."
    }
}
