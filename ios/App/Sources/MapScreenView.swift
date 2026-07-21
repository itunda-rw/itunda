import SwiftUI
import MapLibre
import CoreLocation
import CoreDesignSystem

// Real itunda-hosted Rwanda coordinates -- Kigali, same default center every other real
// coordinate fixture in this codebase (backend tests, bank-mfe's MapView.tsx, Android's
// MapScreen.kt) uses.
private let rwandaCenterLat = -1.9441
private let rwandaCenterLng = 30.0619
private let tilesURL = "http://192.168.252.3:8090/rwanda/{z}/{x}/{y}.mvt"
// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. See bank-mfe's lib/maps.ts GLYPHS_URL doc comment for the
// full account (real pre-generated Noto Sans Regular/Bold glyph PBFs, served statically
// by nginx on itunda-dc-b, ~14MB RSS -- an order of magnitude lighter than OSRM/
// Nominatim despite being this host's fourth persistent private-cloud service).
private let glyphsURL = "http://192.168.252.3:8091/{fontstack}/{range}.pbf"

// Real bookmark-folder defaults/palette (2026-07-22) -- kept in sync by hand with
// MapsService.DEFAULT_BOOKMARK_FOLDER/DEFAULT_BOOKMARK_COLOR on the backend, same plain-
// literal convention as bank-mfe's/Android's own copies. A small fixed palette rather
// than a full color picker, matching this app's own design-system palette.
private let defaultBookmarkFolder = "Saved places"
private let bookmarkColorPalette = ["#F5A623", "#3182F6", "#8B5CF6", "#E53935", "#22B07D", "#4E5968"]

// Real hex-string -> Color parsing (2026-07-22) -- this codebase has no existing
// Color(hex:) helper (checked: `extension Color` in BenefitsShopAllScreens.swift only
// aliases IdsPalette constants), so this is a small, local, file-scoped parser rather
// than a new app-wide Color extension for one feature.
private func colorFromHex(_ hex: String) -> Color {
    var sanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
    if sanitized.hasPrefix("#") { sanitized.removeFirst() }
    guard sanitized.count == 6, let value = UInt64(sanitized, radix: 16) else {
        return Color(red: 0.961, green: 0.651, blue: 0.137) // the default star-yellow, same fallback as the star icon's own hardcoded color
    }
    return Color(
        red: Double((value >> 16) & 0xFF) / 255,
        green: Double((value >> 8) & 0xFF) / 255,
        blue: Double(value & 0xFF) / 255
    )
}

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- mirrors
// bank-mfe's MapView.tsx MAP_STYLE / Android's MapScreen.kt MAP_STYLE_JSON exactly (same
// source, same layer set, no text labels yet since that needs a separate self-hosted
// glyphs server). MLNMapView takes a styleURL, not an inline JSON string, so this is
// written to a temp file once and referenced by file:// URL below.
private let mapStyleJSON = """
{
  "version": 8,
  "glyphs": "\(glyphsURL)",
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["\(tilesURL)"], "minzoom": 0, "maxzoom": 14 }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f2efe9" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "#d8e8c8", "fill-opacity": 0.6 } },
    { "id": "park", "type": "fill", "source": "rwanda", "source-layer": "park",
      "paint": { "fill-color": "#c8e0b0", "fill-opacity": 0.5 } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "#a8d0e6" } },
    { "id": "landuse-residential", "type": "fill", "source": "rwanda", "source-layer": "landuse",
      "filter": ["==", ["get", "class"], "residential"],
      "paint": { "fill-color": "#e6e1d8", "fill-opacity": 0.5 } },
    { "id": "building", "type": "fill", "source": "rwanda", "source-layer": "building", "minzoom": 13,
      "paint": { "fill-color": "#dcd4c6", "fill-outline-color": "#c8bfae" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "#ffffff", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "#f5c96b", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } },
    { "id": "boundary", "type": "line", "source": "rwanda", "source-layer": "boundary",
      "filter": ["<=", ["get", "admin_level"], 4],
      "paint": { "line-color": "#a08ccb", "line-width": 1, "line-dasharray": [2, 1] } },
    { "id": "water-label", "type": "symbol", "source": "rwanda", "source-layer": "water_name", "minzoom": 7,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "#3d6e8f", "text-halo-color": "#ffffff", "text-halo-width": 1 } },
    { "id": "road-label", "type": "symbol", "source": "rwanda", "source-layer": "transportation_name", "minzoom": 12,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12,
        "symbol-placement": "line", "text-letter-spacing": 0.05 },
      "paint": { "text-color": "#6b5a2a", "text-halo-color": "#ffffff", "text-halo-width": 1.2 } },
    { "id": "poi-label", "type": "symbol", "source": "rwanda", "source-layer": "poi", "minzoom": 14,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 11 },
      "paint": { "text-color": "#5a5044", "text-halo-color": "#ffffff", "text-halo-width": 1 } },
    { "id": "place-label-minor", "type": "symbol", "source": "rwanda", "source-layer": "place", "minzoom": 10,
      "filter": ["!", ["match", ["get", "class"], ["city", "town"], true, false]],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "#3d3d3d", "text-halo-color": "#ffffff", "text-halo-width": 1.2 } },
    { "id": "place-label-major", "type": "symbol", "source": "rwanda", "source-layer": "place",
      "filter": ["match", ["get", "class"], ["city", "town"], true, false],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Bold"],
        "text-size": ["interpolate", ["linear"], ["zoom"], 4, 12, 10, 18] },
      "paint": { "text-color": "#1f1f1f", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
"""

private func writeStyleFile() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("itunda-map-style.json")
    try? mapStyleJSON.write(to: url, atomically: true, encoding: .utf8)
    return url
}

// Real per-category glyphs for the chip row (2026-07-21) -- mirrors Android's
// MAP_CATEGORY_ICONS / bank-mfe's CATEGORY_ICONS exactly, same client-side-only
// convention: no icon field on the backend's category model, plain emoji, never sent
// back to the server.
private let mapCategoryIcons: [String: String] = [
    "RESTAURANT": "🍽️", "CAFE": "☕", "HOSPITAL": "🏥", "PHARMACY": "💊",
    "BANK": "🏦", "ATM": "🏧", "HOTEL": "🏨", "SUPERMARKET": "🛒",
    "GAS_STATION": "⛽", "SCHOOL": "🏫",
]

/// Real, minimal handle onto the live `MLNMapView` (2026-07-21) -- SwiftUI's
/// `UIViewRepresentable` doesn't otherwise expose the underlying UIKit view to sibling
/// SwiftUI controls, so the new floating zoom +/- buttons (below, mirroring Android's
/// MapScreen.kt / bank-mfe's MapView.tsx own zoom control) need this thin bridge to call
/// `setZoomLevel` on the real map instance.
private final class MapController: ObservableObject {
    weak var mapView: MLNMapView?
    func zoomIn() { mapView.map { $0.setZoomLevel($0.zoomLevel + 1, animated: true) } }
    func zoomOut() { mapView.map { $0.setZoomLevel($0.zoomLevel - 1, animated: true) } }
}

/// Real "my location" via Apple's own CLLocationManager, runtime-permission-gated,
/// never assumed granted -- see MapScreenView's own doc comment for why this was added
/// 2026-07-19 alongside search/directions.
private final class LocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var errorMessage: String?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
    }

    func requestLocation() {
        errorMessage = nil
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .denied || status == .restricted {
            errorMessage = "Location permission was denied."
        } else {
            manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        coordinate = locations.last?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        errorMessage = "Could not access your real location right now."
    }
}

/// Real interactive Rwanda map -- itunda's own self-hosted Kakao Maps/Naver Maps-style
/// mapping, ported here from bank-mfe's MapView.tsx / Android's MapScreen.kt (same
/// self-hosted PMTiles tile server, same hand-written style, same real merchant
/// markers). Reached from My's Quick links, matching the Pay/Benefits precedent, since
/// neither bottom-nav row has a free slot.
///
/// Real search + directions + "my location" added 2026-07-19, at the user's direct
/// request ("make sure our maps is fully 100% like naver maps/kakao maps for rwanda") --
/// ported field-for-field from bank-mfe's/Android's own same-day builds: search backed
/// by itunda's self-hosted Nominatim (via the new general-purpose `rw.itunda.maps`
/// module), a real blue dot via `CLLocationManager`, and real turn-by-turn-capable
/// directions drawing the actual road-following route via itunda's self-hosted OSRM.
struct MapScreenView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var merchants: [ShoppingMerchantDto] = []
    @State private var query = ""
    @State private var searchResults: [PlaceSearchResultDto]?
    @State private var searching = false
    // Real search-as-you-type autocomplete + recent-searches (2026-07-22) -- ported from
    // bank-mfe's own real debounced live-search. `searchTask` is cancelled and replaced
    // on every keystroke (see the `.onChange(of: query)` below) so a slower stale
    // keystroke's response can never overwrite a newer one's results.
    @State private var searchTask: Task<Void, Never>?
    @State private var recentSearches: [PlaceSearchResultDto] = RecentMapSearchesStore.shared.getAll()
    @FocusState private var searchFocused: Bool
    @State private var selectedPlace: PlaceSearchResultDto?
    @State private var route: RouteResultDto?
    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment on the network client. Often just a single-element array -- OSRM
    // itself decides whether a real alternative exists for a given trip.
    @State private var routeAlternatives: [RouteResultDto]?
    @State private var selectedRouteIndex = 0
    // Real driving/walking toggle (2026-07-22) -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this reaches.
    @State private var travelMode = "DRIVING"
    @State private var showSteps = false
    @State private var routing = false
    @State private var error: String?
    @State private var activeCategory: String?
    @State private var categoryLoading = false
    @State private var categoryResults: [NearbyPlaceDto]?
    @State private var bookmarks: [MapBookmarkDto] = []
    @State private var bookmarking = false
    // Real folder/color picker (2026-07-22) -- ported from bank-mfe's own real save-time
    // picker. `savingToFolder` holds whichever real place's picker is currently expanded
    // (nil = closed).
    @State private var savingToFolder: PlaceSearchResultDto?
    @State private var folderNameInput = defaultBookmarkFolder
    @State private var folderColorInput = bookmarkColorPalette[0]
    @StateObject private var locationFetcher = LocationFetcher()

    // Real draggable peek/half/full bottom sheet (2026-07-21) -- see
    // docs/DESIGN_REFERENCES.md section 1, recommendation 1. SwiftUI has no built-in
    // "persistent, non-modal, 3-detent" sheet primitive (`.sheet` +
    // `.presentationDetents` only applies to a *presented* sheet, not an
    // always-visible panel docked over content already on screen) -- hand-rolled here
    // via a plain `DragGesture` + an absolute Y offset, snapping to the nearest of
    // three real anchors on release, mirroring the same anchor-based approach
    // Android's `MapScreen.kt` uses via `AnchoredDraggableState`. `nil` until the
    // first `GeometryReader` pass supplies a real screen height to anchor against.
    @State private var sheetY: CGFloat?
    @State private var sheetSettledY: CGFloat = 0
    @StateObject private var mapController = MapController()

    private func isBookmarked(_ place: PlaceSearchResultDto) -> Bool {
        bookmarks.contains { $0.latitude == place.latitude && $0.longitude == place.longitude }
    }

    var body: some View {
        NavigationStack {
            // Real full-bleed map with floating overlays (2026-07-21) -- brings iOS to
            // parity with Android's own `MapScreen.kt` restructure (commit 48ad768):
            // was a plain `VStack` stacking search -> chips -> results -> map ->
            // details/bookmarks in normal document flow, which could squeeze the map
            // to a sliver once a place was selected. Real Naver Map/Kakao Map always
            // keep the map full-screen and float search/details panels on top of it.
            GeometryReader { geo in
                // Real anchors for the draggable sheet below (peek/half/full), mirroring
                // Android's own `MapScreen.kt` anchors exactly (same real fractions/gap).
                let peekHeight: CGFloat = 150
                let fullTopGap: CGFloat = 100
                let peekAnchorY = geo.size.height - peekHeight
                let halfAnchorY = geo.size.height * 0.55
                let fullAnchorY = fullTopGap
                let currentSheetY = sheetY ?? peekAnchorY

                ZStack(alignment: .top) {
                    MapLibreMapRepresentable(
                        merchants: merchants, myLocation: locationFetcher.coordinate, destination: selectedPlace,
                        routeGeometry: route?.geometry, nearbyPlaces: categoryResults, controller: mapController,
                    )
                        .ignoresSafeArea()

                    // Real floating chrome (2026-07-21 redesign, mirrors Android's
                    // MapScreen.kt / bank-mfe's MapView.tsx) -- previously one flat,
                    // edge-to-edge `IDS.Colors.background` panel that read as a fixed
                    // toolbar. Now the search pill and chip row are their own
                    // individually-shadowed, deliberately theme-independent white
                    // surfaces (using the static `IdsPalette`, not the theme-reactive
                    // `IDS.Colors`, for exactly the reason found live in bank-mfe's own
                    // dark-mode verification pass: `IDS.Colors.textPrimary` resolves to
                    // white in dark mode, which would be invisible on a hardcoded white
                    // card) with real map visible between them.
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 6) {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(searching ? IdsPalette.gray400 : IdsPalette.blue500)
                            TextField("Search a real place in Rwanda", text: $query)
                                .foregroundColor(IdsPalette.gray900)
                                .focused($searchFocused)
                                .onSubmit { Task { await search() } }
                                .onChange(of: query) { newValue in
                                    searchTask?.cancel()
                                    let trimmed = newValue.trimmingCharacters(in: .whitespaces)
                                    guard trimmed.count >= 2 else {
                                        searchResults = nil
                                        return
                                    }
                                    searchTask = Task {
                                        try? await Task.sleep(nanoseconds: 350_000_000)
                                        guard !Task.isCancelled else { return }
                                        await search()
                                    }
                                }
                            if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                                Button(action: { query = ""; searchResults = nil }) {
                                    Image(systemName: "xmark.circle.fill").foregroundColor(IdsPalette.gray400)
                                }
                            }
                        }
                        .padding(.vertical, 10).padding(.horizontal, 14)
                        .background(IdsPalette.white)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.14), radius: 8, y: 2)

                        // Real category-chip "nearby places" search (Naver/Kakao's own
                        // convention) -- mirrors bank-mfe's MapView.tsx / Android's
                        // MapScreen.kt chip row, now with a per-category emoji glyph.
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(mapNearbyCategories) { category in
                                    let active = activeCategory == category.id
                                    Button(action: { Task { await searchNearbyCategory(category.id) } }) {
                                        HStack(spacing: 4) {
                                            Text(mapCategoryIcons[category.id] ?? "📍")
                                            Text(active && categoryLoading ? "…" : category.label)
                                        }
                                        .font(.caption).bold()
                                        .foregroundColor(active ? .white : IdsPalette.gray700)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(active ? Color(red: 0.545, green: 0.361, blue: 0.965) : IdsPalette.white)
                                        .clipShape(Capsule())
                                        .shadow(color: .black.opacity(active ? 0.28 : 0.1), radius: active ? 4 : 3, y: 1)
                                    }
                                    .disabled(categoryLoading && !active)
                                }
                            }
                        }

                        if searchResults != nil || error != nil {
                            VStack(alignment: .leading, spacing: 2) {
                                if let results = searchResults {
                                    if results.isEmpty {
                                        Text("No real places found for that search.").font(.caption).foregroundColor(IdsPalette.gray500).padding(8)
                                    } else {
                                        ForEach(Array(results.enumerated()), id: \.offset) { _, place in
                                            Text(place.displayName)
                                                .font(.caption)
                                                .foregroundColor(IdsPalette.gray900)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .padding(10)
                                                .onTapGesture {
                                                    recentSearches = RecentMapSearchesStore.shared.add(place)
                                                    selectPlace(place)
                                                }
                                        }
                                    }
                                }
                                if let error {
                                    Text(error).font(.caption).foregroundColor(.red).padding(8)
                                }
                            }
                            .frame(maxHeight: 160)
                            .background(IdsPalette.white)
                            .cornerRadius(14)
                            .shadow(color: .black.opacity(0.14), radius: 8, y: 2)
                        }

                        // Real recent-searches list (2026-07-22) -- only shown while the
                        // search box is focused and empty, same real Naver/Kakao Maps
                        // convention bank-mfe's own version already follows.
                        if searchFocused && query.trimmingCharacters(in: .whitespaces).isEmpty && !recentSearches.isEmpty {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text("Recent searches").font(.caption2).bold().foregroundColor(IdsPalette.gray500)
                                    Spacer()
                                    Button(action: { RecentMapSearchesStore.shared.clear(); recentSearches = [] }) {
                                        Text("Clear").font(.caption2).bold().foregroundColor(IdsPalette.blue500)
                                    }
                                }
                                .padding(.horizontal, 10).padding(.top, 8)
                                ForEach(Array(recentSearches.enumerated()), id: \.offset) { _, place in
                                    Text("🕐 \(place.displayName)")
                                        .font(.caption)
                                        .foregroundColor(IdsPalette.gray900)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(10)
                                        .onTapGesture {
                                            recentSearches = RecentMapSearchesStore.shared.add(place)
                                            selectPlace(place)
                                        }
                                }
                            }
                            .frame(maxHeight: 160)
                            .background(IdsPalette.white)
                            .cornerRadius(14)
                            .shadow(color: .black.opacity(0.14), radius: 8, y: 2)
                        }
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .padding(.top, 12)

                    // Real floating right-side controls (2026-07-21) -- zoom +/- and a
                    // dedicated "locate me" button, matching the standard Google
                    // Maps/Naver Map/Kakao Map convention of a vertical control stack on
                    // the right, distinct from the search bar (which previously carried
                    // the locate button inline). Mirrors Android's/bank-mfe's own control
                    // stack exactly. Anchored above the sheet's own peek height.
                    VStack(spacing: 10) {
                        VStack(spacing: 0) {
                            Button(action: { mapController.zoomIn() }) {
                                Image(systemName: "plus").foregroundColor(IdsPalette.gray900)
                                    .frame(width: 44, height: 44)
                            }
                            Divider().frame(width: 44)
                            Button(action: { mapController.zoomOut() }) {
                                Image(systemName: "minus").foregroundColor(IdsPalette.gray900)
                                    .frame(width: 44, height: 44)
                            }
                        }
                        .background(IdsPalette.white)
                        .cornerRadius(14)
                        .shadow(color: .black.opacity(0.14), radius: 8, y: 2)

                        Button(action: { locationFetcher.requestLocation() }) {
                            Image(systemName: "location.fill")
                                .foregroundColor(IdsPalette.blue500)
                                .frame(width: 46, height: 46)
                                .background(IdsPalette.white)
                                .clipShape(Circle())
                                .shadow(color: .black.opacity(0.14), radius: 8, y: 2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 16)
                    .padding(.bottom, peekHeight + 16)
                    .frame(maxHeight: .infinity, alignment: .bottom)

                    // Real draggable peek/half/full bottom sheet (2026-07-21) -- a
                    // persistent, non-modal panel docked over the map. `DragGesture`
                    // updates `sheetY` live; on release it snaps to whichever of the
                    // three real anchors above is closest, the same anchor-based model
                    // Android's `MapScreen.kt` implements via `AnchoredDraggableState`.
                    VStack(spacing: 0) {
                        Capsule()
                            .fill(IDS.Colors.textSecondary.opacity(0.4))
                            .frame(width: 36, height: 4)
                            .padding(.top, 10)
                            .padding(.bottom, 6)

                        ScrollView {
                            VStack(alignment: .leading, spacing: 8) {
                                if let place = selectedPlace {
                                    HStack(alignment: .top) {
                                        Text(place.displayName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        Spacer()
                                        // Real "share this place" (2026-07-22) -- ported
                                        // from bank-mfe's own real Web Share/clipboard
                                        // action. Plain name+coordinate text via the
                                        // native share sheet, not a link into itunda's own
                                        // domain -- there's no public per-place page a
                                        // recipient outside this app could open.
                                        ShareLink(item: "\(place.displayName) (\(String(format: "%.6f", place.latitude)), \(String(format: "%.6f", place.longitude)))") {
                                            Text("📤").font(.body)
                                        }
                                        .padding(.trailing, 4)
                                        Button(action: { Task { await toggleBookmark(place) } }) {
                                            Text(isBookmarked(place) ? "★" : "☆")
                                                .font(.title3)
                                                .foregroundColor(isBookmarked(place) ? Color(red: 0.961, green: 0.651, blue: 0.137) : IDS.Colors.textSecondary)
                                        }
                                        .disabled(bookmarking)
                                    }
                                    // Real folder/color picker (2026-07-22) -- only
                                    // expanded for the place actually being saved right
                                    // now. Its own @ViewBuilder function, not inlined --
                                    // same type-checker-timeout lesson as
                                    // travelModeToggle()/routeAlternativesPicker(_:) below.
                                    if let savingToFolder, savingToFolder.latitude == place.latitude, savingToFolder.longitude == place.longitude {
                                        folderPicker()
                                    }
                                    // Real driving/walking mode toggle (2026-07-22) --
                                    // same real Naver/Kakao Maps convention of picking a
                                    // travel mode before/after a route is drawn. Extracted
                                    // into its own @ViewBuilder function (not inlined) --
                                    // inlined here, the combined nesting made the Swift
                                    // type-checker time out ("unable to type-check this
                                    // expression in reasonable time").
                                    travelModeToggle()
                                    if let route {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("\(travelMode == "DRIVING" ? "🚗" : "🚶") \(String(format: "%.1f", route.distanceKm)) km · \(Int(route.durationMinutes)) min by real road, via itunda's own self-hosted OSRM")
                                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                            // Real alternative-route picker (2026-07-22) --
                                            // only shown when OSRM genuinely offered more
                                            // than one real route for this trip. Same
                                            // type-checker-timeout reasoning as above for
                                            // why this is its own function, not inlined.
                                            if let alternatives = routeAlternatives, alternatives.count > 1 {
                                                routeAlternativesPicker(alternatives)
                                            }
                                            if !route.steps.isEmpty {
                                                Button(action: { showSteps.toggle() }) {
                                                    Text(showSteps ? "Hide turn-by-turn directions" : "Show turn-by-turn directions (\(route.steps.count) steps)")
                                                        .font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                                                }
                                                if showSteps {
                                                    VStack(alignment: .leading, spacing: 4) {
                                                        ForEach(Array(route.steps.enumerated()), id: \.offset) { i, step in
                                                            Text("\(i + 1). \(step.instruction)" + (step.distanceMeters >= 10 ? " (\(Int(step.distanceMeters)) m)" : ""))
                                                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                                        }
                                                    }
                                                    .padding(.top, 4)
                                                }
                                            }
                                        }
                                    } else {
                                        Button(action: { Task { await getDirections() } }) {
                                            Text(routing ? "Finding real route…" : "Directions")
                                                .font(.subheadline).bold().foregroundColor(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                .background(IDS.Colors.brand).cornerRadius(12)
                                        }
                                        .disabled(routing)
                                    }
                                } else {
                                    // Real default "around me" state (2026-07-21) --
                                    // Naver Map's own Smart Around sheet keeps a
                                    // non-modal panel permanently docked with real
                                    // curated content even before any search, rather
                                    // than only appearing once a place is selected.
                                    // itunda has no editorial "today's pick" feed to
                                    // curate, so this surfaces real data it already
                                    // has: the active category's real results, a real
                                    // merchant count, and real saved places.
                                    Text("Around you").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                    if let activeCategory, let categoryResults {
                                        let label = mapNearbyCategories.first { $0.id == activeCategory }?.label.lowercased() ?? "places"
                                        if categoryResults.isEmpty {
                                            Text("No real matches found nearby for \(label).").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        } else {
                                            ForEach(Array(categoryResults.enumerated()), id: \.offset) { _, place in
                                                Text("\(place.displayName) · \(String(format: "%.1f", place.distanceKm)) km")
                                                    .font(.caption)
                                                    .foregroundColor(IDS.Colors.textPrimary)
                                                    .frame(maxWidth: .infinity, alignment: .leading)
                                                    .padding(.vertical, 6)
                                                    .onTapGesture {
                                                        selectPlace(PlaceSearchResultDto(displayName: place.displayName, latitude: place.latitude, longitude: place.longitude))
                                                    }
                                            }
                                        }
                                    } else {
                                        Text(merchants.isEmpty
                                            ? "Search a real place or pick a category above to explore Rwanda."
                                            : "\(merchants.count) real merchant\(merchants.count == 1 ? "" : "s") on the map. Search a place or pick a category above to explore.")
                                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }

                                    Text("★ Your saved places").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary).padding(.top, 8)
                                    if bookmarks.isEmpty {
                                        Text("No saved places yet -- tap ☆ on a place to save it.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    } else {
                                        let folders = bookmarksByFolder
                                        ForEach(folders, id: \.0) { folderName, folderBookmarks in
                                            if folders.count > 1 {
                                                Text(folderName).font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary).padding(.top, 4)
                                            }
                                            ForEach(folderBookmarks) { bookmark in
                                                HStack(spacing: 6) {
                                                    Circle().fill(colorFromHex(bookmark.color)).frame(width: 8, height: 8)
                                                    Text(bookmark.displayName)
                                                        .font(.caption).foregroundColor(IDS.Colors.textPrimary)
                                                }
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .padding(.vertical, 6)
                                                .onTapGesture {
                                                    selectPlace(PlaceSearchResultDto(displayName: bookmark.displayName, latitude: bookmark.latitude, longitude: bookmark.longitude))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                            .padding(.bottom, 24)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: geo.size.height, alignment: .top)
                    .background(IDS.Colors.card)
                    .cornerRadius(20)
                    .offset(y: currentSheetY)
                    .gesture(
                        DragGesture()
                            .onChanged { value in
                                let proposed = sheetSettledY + value.translation.height
                                sheetY = min(peekAnchorY, max(fullAnchorY, proposed))
                            }
                            .onEnded { value in
                                let proposed = sheetSettledY + value.translation.height
                                let candidates = [fullAnchorY, halfAnchorY, peekAnchorY]
                                let nearest = candidates.min(by: { abs($0 - proposed) < abs($1 - proposed) }) ?? peekAnchorY
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { sheetY = nearest }
                                sheetSettledY = nearest
                            },
                    )
                    .onAppear {
                        if sheetY == nil {
                            sheetY = peekAnchorY
                            sheetSettledY = peekAnchorY
                        }
                    }
                    // A newly-selected place should be immediately visible without a
                    // manual drag -- expands to Half; clearing the selection relaxes
                    // back to Peek instead of staying pinned open over an empty card.
                    .onChange(of: selectedPlace?.displayName) { _ in
                        let target = selectedPlace != nil ? halfAnchorY : peekAnchorY
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { sheetY = target }
                        sheetSettledY = target
                    }
                }
            }
            .navigationTitle("Map")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .task {
                do {
                    let response = try await NetworkClient.shared.getShoppingMerchants()
                    merchants = response.merchants.filter { $0.latitude != nil && $0.longitude != nil }
                } catch {
                    // Honest partial failure -- the base map still renders even if the
                    // real merchant overlay fails to load, never a blank screen for a
                    // real infra hiccup.
                }
            }
            .task {
                do {
                    bookmarks = try await NetworkClient.shared.getMyMapBookmarks().bookmarks
                } catch {
                    // Honest partial failure -- bookmarks are a real-nice-to-have, never
                    // block the rest of the Maps feature set from loading.
                }
            }
            .onChange(of: locationFetcher.errorMessage) { newValue in
                if let newValue { error = newValue }
            }
        }
    }

    private func search() async {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        searching = true
        error = nil
        defer { searching = false }
        do {
            searchResults = try await NetworkClient.shared.searchPlaces(query: trimmed).results
        } catch {
            self.error = "Could not search for that place."
        }
    }

    private func selectPlace(_ place: PlaceSearchResultDto) {
        selectedPlace = place
        searchResults = nil
        route = nil
        routeAlternatives = nil
        selectedRouteIndex = 0
        showSteps = false
        savingToFolder = nil
    }

    // mode defaults to the currently-selected travelMode (2026-07-22) -- called both by
    // the initial "Directions" tap and by the driving/walking toggle when a route is
    // already shown, mirroring bank-mfe's own applyRoute/handleGetDirections split.
    private func getDirections(mode: String? = nil) async {
        guard let place = selectedPlace else { return }
        let requestedMode = mode ?? travelMode
        routing = true
        error = nil
        defer { routing = false }
        let origin = locationFetcher.coordinate ?? CLLocationCoordinate2D(latitude: rwandaCenterLat, longitude: rwandaCenterLng)
        do {
            let response = try await NetworkClient.shared.getDirectionsAlternatives(
                fromLat: origin.latitude, fromLng: origin.longitude, toLat: place.latitude, toLng: place.longitude, mode: requestedMode,
            )
            travelMode = requestedMode
            routeAlternatives = response.routes
            selectedRouteIndex = 0
            route = response.routes.first
            showSteps = false
        } catch {
            self.error = "Could not find directions to this place."
        }
    }

    // Real folder/color picker (2026-07-22) -- ported from bank-mfe's own real save-time
    // picker. Its own @ViewBuilder function from the start, learning from the
    // travelModeToggle()/routeAlternativesPicker(_:) type-checker-timeout lesson below.
    @ViewBuilder
    private func folderPicker() -> some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField("Folder name (e.g. Favorites)", text: $folderNameInput)
                .font(.caption)
                .padding(8)
                .background(IdsPalette.white)
                .cornerRadius(8)
            HStack(spacing: 6) {
                ForEach(bookmarkColorPalette, id: \.self) { hex in
                    let active = folderColorInput == hex
                    Circle()
                        .fill(colorFromHex(hex))
                        .frame(width: 22, height: 22)
                        .overlay(Circle().stroke(IDS.Colors.textPrimary, lineWidth: active ? 2 : 0))
                        .onTapGesture { folderColorInput = hex }
                }
            }
            HStack(spacing: 6) {
                Button(action: { Task { await confirmSaveToFolder() } }) {
                    Text(bookmarking ? "Saving…" : "Save")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background(IDS.Colors.brand).cornerRadius(8)
                }
                .disabled(bookmarking)
                Button(action: { savingToFolder = nil }) {
                    Text("Cancel").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                }
                .disabled(bookmarking)
            }
        }
        .padding(8)
        .background(Color(red: 0.976, green: 0.980, blue: 0.988))
        .cornerRadius(8)
    }

    // Real driving/walking mode toggle (2026-07-22) -- extracted into its own
    // @ViewBuilder function (see the call site's own comment for why: inlined directly
    // into the surrounding view hierarchy, the combined nesting made the Swift
    // type-checker time out).
    @ViewBuilder
    private func travelModeToggle() -> some View {
        HStack(spacing: 6) {
            ForEach([("DRIVING", "🚗 Driving"), ("WALKING", "🚶 Walking")], id: \.0) { mode, label in
                let active = travelMode == mode
                Button(action: {
                    guard mode != travelMode else { return }
                    if route != nil {
                        Task { await getDirections(mode: mode) }
                    } else {
                        travelMode = mode
                    }
                }) {
                    Text(label)
                        .font(.caption2).bold()
                        .foregroundColor(active ? .white : IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                        .background(active ? IDS.Colors.brand : Color(red: 0.949, green: 0.957, blue: 0.965))
                        .cornerRadius(8)
                }
                .disabled(routing)
            }
        }
    }

    // Real alternative-route picker (2026-07-22) -- see travelModeToggle's own comment
    // for why this is its own function rather than inlined.
    @ViewBuilder
    private func routeAlternativesPicker(_ alternatives: [RouteResultDto]) -> some View {
        HStack(spacing: 6) {
            ForEach(Array(alternatives.enumerated()), id: \.offset) { i, alt in
                let active = selectedRouteIndex == i
                let altKm: String = String(format: "%.1f", alt.distanceKm)
                let altMin: Int = Int(alt.durationMinutes)
                let altLabel: String = "Route \(i + 1) · \(altKm)km · \(altMin)min"
                Button(action: {
                    selectedRouteIndex = i
                    route = alt
                }) {
                    Text(altLabel)
                        .font(.caption2).bold()
                        .foregroundColor(active ? .white : IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 5)
                        .background(active ? IDS.Colors.brand : Color(red: 0.949, green: 0.957, blue: 0.965))
                        .cornerRadius(8)
                }
            }
        }
        .padding(.top, 6)
    }

    private func searchNearbyCategory(_ categoryId: String) async {
        if activeCategory == categoryId {
            activeCategory = nil
            categoryResults = nil
            return
        }
        let center = locationFetcher.coordinate ?? CLLocationCoordinate2D(latitude: rwandaCenterLat, longitude: rwandaCenterLng)
        activeCategory = categoryId
        categoryLoading = true
        error = nil
        defer { categoryLoading = false }
        do {
            categoryResults = try await NetworkClient.shared.searchNearbyPlaces(category: categoryId, lat: center.latitude, lng: center.longitude).places
        } catch {
            self.error = "Could not search nearby places."
            activeCategory = nil
        }
    }

    private func toggleBookmark(_ place: PlaceSearchResultDto) async {
        if isBookmarked(place) {
            bookmarking = true
            error = nil
            defer { bookmarking = false }
            do {
                _ = try await NetworkClient.shared.removeMapBookmark(latitude: place.latitude, longitude: place.longitude)
                bookmarks.removeAll { $0.latitude == place.latitude && $0.longitude == place.longitude }
            } catch {
                self.error = "Could not remove this place."
            }
            return
        }
        // Real folder/color picker (2026-07-22) -- opens inline rather than saving
        // straight to the default folder, defaulting to whichever real folder was used
        // last (ported from bank-mfe's own real save-time picker).
        folderNameInput = bookmarks.first?.folderName ?? defaultBookmarkFolder
        folderColorInput = bookmarks.first?.color ?? bookmarkColorPalette[0]
        savingToFolder = place
    }

    private func confirmSaveToFolder() async {
        guard let place = savingToFolder else { return }
        bookmarking = true
        error = nil
        defer { bookmarking = false }
        do {
            let saved = try await NetworkClient.shared.addMapBookmark(
                displayName: place.displayName, latitude: place.latitude, longitude: place.longitude,
                folderName: folderNameInput, color: folderColorInput
            ).bookmark
            bookmarks.insert(saved, at: 0)
            savingToFolder = nil
        } catch {
            self.error = "Could not save this place."
        }
    }

    // Real "My Places" folder grouping (2026-07-22) -- ported from bank-mfe's own real
    // grouping. Preserves `bookmarks`' own createdAt-desc encounter order (a folder's
    // position here is simply wherever its most-recently-saved place falls), not a
    // separate alphabetic re-sort.
    private var bookmarksByFolder: [(String, [MapBookmarkDto])] {
        var order: [String] = []
        var groups: [String: [MapBookmarkDto]] = [:]
        for bookmark in bookmarks {
            if groups[bookmark.folderName] == nil {
                order.append(bookmark.folderName)
                groups[bookmark.folderName] = []
            }
            groups[bookmark.folderName]?.append(bookmark)
        }
        return order.map { ($0, groups[$0] ?? []) }
    }
}

private let routeSourceIdentifier = "itunda-route"
private let routeLayerIdentifier = "itunda-route-line"

private let nearbyAnnotationTitlePrefix = "itunda-nearby:"

private struct MapLibreMapRepresentable: UIViewRepresentable {
    let merchants: [ShoppingMerchantDto]
    let myLocation: CLLocationCoordinate2D?
    let destination: PlaceSearchResultDto?
    let routeGeometry: [[Double]]?
    let nearbyPlaces: [NearbyPlaceDto]?
    let controller: MapController

    private let myLocationAnnotationTitle = "itunda-my-location"

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: writeStyleFile())
        mapView.delegate = context.coordinator
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: rwandaCenterLat, longitude: rwandaCenterLng),
            zoomLevel: 12,
            animated: false,
        )
        controller.mapView = mapView
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        if let existing = mapView.annotations {
            mapView.removeAnnotations(existing)
        }
        var points = merchants.compactMap { merchant -> MLNPointAnnotation? in
            guard let lat = merchant.latitude, let lng = merchant.longitude else { return nil }
            let point = MLNPointAnnotation()
            point.coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            point.title = merchant.businessName
            return point
        }
        if let destination {
            let point = MLNPointAnnotation()
            point.coordinate = CLLocationCoordinate2D(latitude: destination.latitude, longitude: destination.longitude)
            point.title = destination.displayName
            points.append(point)
            mapView.setCenter(point.coordinate, zoomLevel: 15, animated: true)
        }
        if let myLocation {
            let point = MLNPointAnnotation()
            point.coordinate = myLocation
            point.title = myLocationAnnotationTitle
            points.append(point)
        }
        // Real "nearby places" category-search markers (2026-07-19) -- a distinct
        // violet color, same as bank-mfe's MapView.tsx category chips, tagged via a
        // sentinel title prefix so the coordinator's viewFor annotation can style them.
        for place in nearbyPlaces ?? [] {
            let point = MLNPointAnnotation()
            point.coordinate = CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
            point.title = nearbyAnnotationTitlePrefix + place.displayName
            points.append(point)
        }
        mapView.addAnnotations(points)
        context.coordinator.pendingRouteGeometry = routeGeometry
        context.coordinator.applyRoute(to: mapView)
    }

    func makeCoordinator() -> Coordinator { Coordinator(myLocationAnnotationTitle: myLocationAnnotationTitle) }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        let myLocationAnnotationTitle: String
        var pendingRouteGeometry: [[Double]]?
        init(myLocationAnnotationTitle: String) { self.myLocationAnnotationTitle = myLocationAnnotationTitle }

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            // Real drawn route (2026-07-19) -- same real GeoJSON-source + line-layer
            // technique bank-mfe's MapView.tsx / Android's MapScreen.kt already use, not
            // the legacy MLNOverlay path (this MapLibre release doesn't expose an
            // MLNOverlayRenderer type to style one with).
            let source = MLNShapeSource(identifier: routeSourceIdentifier, shape: nil, options: nil)
            style.addSource(source)
            let layer = MLNLineStyleLayer(identifier: routeLayerIdentifier, source: source)
            layer.lineColor = NSExpression(forConstantValue: UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 0.9))
            layer.lineWidth = NSExpression(forConstantValue: 5)
            layer.lineCap = NSExpression(forConstantValue: "round")
            layer.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(layer)
            applyRoute(to: mapView)
        }

        func applyRoute(to mapView: MLNMapView) {
            guard let style = mapView.style, let source = style.source(withIdentifier: routeSourceIdentifier) as? MLNShapeSource else { return }
            guard let geometry = pendingRouteGeometry, !geometry.isEmpty else {
                source.shape = nil
                return
            }
            let coordinates = geometry.map { CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) }
            source.shape = MLNPolylineFeature(coordinates: coordinates, count: UInt(coordinates.count))
        }

        // Real distinct "my location" blue dot, styled differently from the default red
        // pin used for merchants/search destinations -- matches Naver/Kakao Maps' own
        // real convention for a location indicator. Real "nearby places" category-search
        // markers get their own distinct violet dot, same as bank-mfe/Android.
        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            let title = annotation.title ?? nil
            if title == myLocationAnnotationTitle {
                let identifier = "itunda-my-location-view"
                let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) ?? MLNAnnotationView(reuseIdentifier: identifier)
                view.frame = CGRect(x: 0, y: 0, width: 18, height: 18)
                view.backgroundColor = UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 1.0)
                view.layer.cornerRadius = 9
                view.layer.borderColor = UIColor.white.cgColor
                view.layer.borderWidth = 3
                return view
            }
            if let title, title.hasPrefix(nearbyAnnotationTitlePrefix) {
                let identifier = "itunda-nearby-view"
                let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) ?? MLNAnnotationView(reuseIdentifier: identifier)
                view.frame = CGRect(x: 0, y: 0, width: 14, height: 14)
                view.backgroundColor = UIColor(red: 0.545, green: 0.361, blue: 0.965, alpha: 1.0)
                view.layer.cornerRadius = 7
                view.layer.borderColor = UIColor.white.cgColor
                view.layer.borderWidth = 2
                return view
            }
            return nil
        }

        // Nearby-place markers carry a sentinel-prefixed title (not real display text,
        // just a tag the annotation-view lookup above keys off), so their callout is
        // suppressed rather than showing that raw prefix to a real user.
        func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool {
            !(annotation.title.flatMap { $0 }?.hasPrefix(nearbyAnnotationTitlePrefix) ?? false)
        }
    }
}
