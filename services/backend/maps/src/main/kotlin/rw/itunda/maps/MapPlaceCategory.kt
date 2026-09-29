package rw.itunda.maps

/**
 * Real category chips for "nearby places" search -- the same kind of category-chip
 * search Naver/Kakao Maps offer (restaurants, cafes, hospitals, ...). Each maps to a
 * plain free-text search term, live-verified against itunda's actual self-hosted
 * Nominatim instance (`GET /search?q=<term>&countrycodes=rw`) -- **not** Nominatim's
 * structured `[key=value]` bracket syntax, which was tried first but confirmed live to
 * return zero results against this deployment (its "special phrases" table, which the
 * bracket syntax depends on to map a key/value pair to a search filter, wasn't part of
 * the country-scoped import) while the exact same OSM `amenity=restaurant`-tagged rows
 * ARE indexed and do surface for a plain free-text term. A fixed whitelist (not an
 * arbitrary client-supplied string) keeps the category param safe and predictable.
 */
enum class MapPlaceCategory(val label: String, val searchTerm: String) {
    RESTAURANT("Restaurants", "restaurant"),
    CAFE("Cafes", "cafe"),
    HOSPITAL("Hospitals", "hospital"),
    PHARMACY("Pharmacies", "pharmacy"),
    BANK("Banks", "bank"),
    ATM("ATMs", "atm"),
    HOTEL("Hotels", "hotel"),
    SUPERMARKET("Supermarkets", "supermarket"),
    GAS_STATION("Gas stations", "gas station"),
    SCHOOL("Schools", "school"),
    // Real additions (2026-08-09), same "take it seriously, real Rwandans use this every day"
    // pass -- live-verified against itunda's actual self-hosted Nominatim exactly like every
    // category above (real, non-empty, relevant results confirmed via direct curl before
    // adding: "market"/"bus stop" both returned genuine Rwanda market/bus-stop rows, not noise
    // matches). Markets and bus stops are genuinely everyday wayfinding needs this category
    // list was missing entirely.
    MARKET("Markets", "market"),
    BUS_STOP("Bus stops", "bus stop"),
    ;

    companion object {
        fun fromParam(value: String): MapPlaceCategory? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
