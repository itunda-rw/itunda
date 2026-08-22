package rw.itunda.core.network

// Real, named replacement for a bare `delay(300)` that was independently duplicated,
// unexplained, across 6 separate feature modules' own search-input debounce effects
// (Property/Marketplace/Shop/Jobs/Community/Eats) -- the exact real "magic number"
// smell Toss's own Frontend Fundamentals guide names directly (github.com/toss/
// frontend-fundamentals, magic-number-readability.md's own `delay(300)` example: "Is
// it waiting for the animation to complete? Is it waiting for the like to be
// reflected?" -- a bare literal forces every reader to guess). See
// docs/ARCHITECTURE_GUIDELINES.md §7 for the standing rule this codifies.
//
// Lives in core/network (not a single feature module) since every one of the 6 real
// call sites already depends on this module for NetworkClient itself, and a search-
// debounce constant is directly network-adjacent -- keeps this one real value in
// sync across every module without granting any feature module a forbidden
// cross-module import into a sibling.
const val SEARCH_DEBOUNCE_MS = 300L
