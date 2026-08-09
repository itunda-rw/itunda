import SwiftUI
import MapLibre
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Real itunda-hosted Rwanda coordinates -- Kigali, same default center every other real
// coordinate fixture in this codebase (backend tests, bank-mfe's MapView.tsx, Android's
// MapScreen.kt) uses.
private let rwandaCenterLat = -1.9441
private let rwandaCenterLng = 30.0619
// Address corrected 2026-07-27: itunda-dc-b (192.168.252.3) was decommissioned; the
// surviving sole node is itunda-dc-a, 192.168.252.4. Still a LAN-only address, same as
// Android's own equivalent BuildConfig default -- no same-origin-relative-path trick
// exists for a native client the way bank-mfe's own fix used.
private let tilesURL = "http://192.168.252.4:8090/rwanda/{z}/{x}/{y}.mvt"
// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. See bank-mfe's lib/maps.ts GLYPHS_URL doc comment for the
// full account (real pre-generated Noto Sans Regular/Bold glyph PBFs, served statically
// by nginx, ~14MB RSS -- an order of magnitude lighter than OSRM/Nominatim despite being
// one more persistent private-cloud service).
private let glyphsURL = "http://192.168.252.4:8091/{fontstack}/{range}.pbf"

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
    "GAS_STATION": "⛽", "SCHOOL": "🏫", "ITUNDA_AGENT": "💰",
    "MARKET": "🧺", "BUS_STOP": "🚌",
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

    // Real map-tap infrastructure (2026-07-23) -- bridges SwiftUI state into the plain
    // NSObject Coordinator below, the same "class instance both sides can reach" role
    // `mapView` already plays here for zoom. `onSelectPlace` backs a real tap on a
    // merchant/nearby-place pin (MLNPointAnnotation already supports native tap-select
    // via `didSelect`, unlike Android's GeoJsonSource-backed circles, which need a real
    // queryRenderedFeatures call); `onMeasureTap`/`isMeasuring` back the ruler tool,
    // which needs a raw tap anywhere on the map, not just on an existing annotation.
    var onSelectPlace: ((PlaceSearchResultDto) -> Void)?
    var isMeasuring = false
    var onMeasureTap: ((CLLocationCoordinate2D) -> Void)?
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
    /// Optional authenticated handoff from another Itunda surface. It only seeds the
    /// existing self-hosted search field; all result fetching remains in `search()`.
    let initialSearchQuery: String?

    init(initialSearchQuery: String? = nil) {
        self.initialSearchQuery = initialSearchQuery?.trimmingCharacters(in: .whitespacesAndNewlines).prefix(160).description
    }

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
    @State private var itineraryStops: [PlaceSearchResultDto] = []
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
    @State private var moving = false
    // Real folder/color picker (2026-07-22) -- ported from bank-mfe's own real save-time
    // picker. `savingToFolder` holds whichever real place's picker is currently expanded
    // (nil = closed).
    @State private var savingToFolder: PlaceSearchResultDto?
    @State private var folderNameInput = defaultBookmarkFolder
    @State private var folderColorInput = bookmarkColorPalette[0]
    // Real "move to folder" (found 2026-07-22: NetworkClient.moveMapBookmark already
    // existed with zero UI calling it anywhere) -- movingBookmark holds whichever real
    // bookmark's move-picker is currently expanded (nil = closed).
    @State private var movingBookmark: MapBookmarkDto?
    @State private var moveFolderNameInput = ""
    @State private var moveFolderColorInput = bookmarkColorPalette[0]
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

    // Real distance-measurement (ruler) tool state (2026-07-23) -- ported from
    // bank-mfe's own real MapView.tsx. Plain (lat, lng) pairs in tap order, matching
    // Android's own identical port.
    @State private var measuring = false
    @State private var measurePoints: [(Double, Double)] = []
    @State private var lastMeasuredPlaceName: String?
    @State private var measureReverseGeocodeTask: Task<Void, Never>?

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
                        routeGeometry: route?.geometry, nearbyPlaces: categoryResults, measurePoints: measurePoints,
                        controller: mapController,
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
                                }.accessibilityLabel("Clear")
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
                                        EmptyStateView("No real places found for that search.")
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
                                    Text("Recent searches").font(.caption2).bold().foregroundColor(IDS.Colors.textTertiary)
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
                            }.accessibilityLabel("Zoom in")
                            Divider().frame(width: 44)
                            Button(action: { mapController.zoomOut() }) {
                                Image(systemName: "minus").foregroundColor(IdsPalette.gray900)
                                    .frame(width: 44, height: 44)
                            }.accessibilityLabel("Zoom out")
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
                        }.accessibilityLabel("Center on my location")

                        // Real distance-measurement (ruler) tool toggle (2026-07-23) --
                        // Naver/Kakao Maps' own real "measure distance" action, ported
                        // from bank-mfe's own real MapView.tsx.
                        Button(action: { toggleMeasuring() }) {
                            Text("📏")
                                .font(.system(size: 18))
                                .frame(width: 46, height: 46)
                                .background(measuring ? Color(red: 0.898, green: 0.224, blue: 0.208) : IdsPalette.white)
                                .clipShape(Circle())
                                .shadow(color: .black.opacity(0.14), radius: 8, y: 2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 16)
                    .padding(.bottom, peekHeight + 16)
                    .frame(maxHeight: .infinity, alignment: .bottom)

                    // Real distance-measurement (ruler) tool info badge (2026-07-23) --
                    // only shown while active, floats below the search chrome so it never
                    // fights the docked bottom sheet for space. Ported from bank-mfe's own
                    // real version.
                    if measuring {
                        HStack(spacing: 10) {
                            Text(
                                measurePoints.isEmpty ? "Tap the map to add 2–7 stops"
                                    : measurePoints.count == 1 ? "Add 1 more stop to route it"
                                    : "\(measurePoints.count) stops · \(String(format: "%.2f", measureTotalKm)) km straight-line"
                            )
                            .font(.caption).bold().foregroundColor(IdsPalette.gray900)
                            if let lastMeasuredPlaceName {
                                Text(lastMeasuredPlaceName).font(.caption2).foregroundColor(IDS.Colors.textTertiary).lineLimit(1)
                            }
                            if !measurePoints.isEmpty {
                                Button("Undo") { measurePoints.removeLast(); lastMeasuredPlaceName = nil }
                                    .font(.caption).bold().foregroundColor(IdsPalette.blue500)
                            }
                            if measurePoints.count >= 2 {
                                Button(routing ? "Routing…" : "Route itinerary") { Task { await routeMeasuredItinerary() } }
                                    .disabled(routing)
                                    .font(.caption).bold().foregroundColor(.white)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(IDS.Colors.brand).cornerRadius(999)
                            }
                            Button("Done") { measuring = false; mapController.isMeasuring = false; measurePoints = []; lastMeasuredPlaceName = nil }
                                .font(.caption).bold().foregroundColor(IDS.Colors.textTertiary)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                        .background(IdsPalette.white)
                        .cornerRadius(999)
                        .shadow(color: .black.opacity(0.14), radius: 8, y: 2)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 80)
                    }

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
                                    VStack(alignment: .leading, spacing: 6) {
                                        Button(itineraryStops.contains(where: { $0.latitude == place.latitude && $0.longitude == place.longitude }) ? "Already in itinerary" : "＋ Add stop to itinerary") {
                                            guard itineraryStops.count < 7, !itineraryStops.contains(where: { $0.latitude == place.latitude && $0.longitude == place.longitude }) else { return }
                                            itineraryStops.append(place)
                                        }
                                        .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                        .disabled(itineraryStops.count >= 7 || itineraryStops.contains(where: { $0.latitude == place.latitude && $0.longitude == place.longitude }))
                                        if itineraryStops.count >= 2 {
                                            Text("Itinerary: \(itineraryStops.map(\.displayName).joined(separator: " → "))").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                            Button(action: { Task { await getItineraryDirections() } }) { Text(routing ? "Routing itinerary…" : "Route \(itineraryStops.count) stops").font(.caption).bold().foregroundColor(.white).padding(.horizontal, 12).padding(.vertical, 8).background(IDS.Colors.brand).cornerRadius(10) }.disabled(routing)
                                        }
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
                                    let home = bookmarks.first { $0.folderName.caseInsensitiveCompare("Home") == .orderedSame }
                                    let work = bookmarks.first { $0.folderName.caseInsensitiveCompare("Work") == .orderedSame }
                                    if home != nil || work != nil {
                                        HStack(spacing: 8) {
                                            if let home { Button("⌂ Home") { selectAndRoute(PlaceSearchResultDto(displayName: home.displayName, latitude: home.latitude, longitude: home.longitude)) }.font(.caption).bold().padding(.horizontal, 12).padding(.vertical, 8).background(IDS.Colors.chipBackground).cornerRadius(999) }
                                            if let work { Button("▣ Work") { selectAndRoute(PlaceSearchResultDto(displayName: work.displayName, latitude: work.latitude, longitude: work.longitude)) }.font(.caption).bold().padding(.horizontal, 12).padding(.vertical, 8).background(IDS.Colors.chipBackground).cornerRadius(999) }
                                        }
                                    }
                                    if let activeCategory, let categoryResults {
                                        let label = mapNearbyCategories.first { $0.id == activeCategory }?.label.lowercased() ?? "places"
                                        if categoryResults.isEmpty {
                                            EmptyStateView("No real matches found nearby for \(label).")
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
                                        EmptyStateView("No saved places yet — tap ☆ on a place to save it.")
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
                                                    Spacer()
                                                    Text("Move")
                                                        .font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                                                        .onTapGesture {
                                                            if movingBookmark?.latitude == bookmark.latitude && movingBookmark?.longitude == bookmark.longitude {
                                                                movingBookmark = nil
                                                            } else {
                                                                movingBookmark = bookmark
                                                                moveFolderNameInput = bookmark.folderName
                                                                moveFolderColorInput = bookmark.color
                                                            }
                                                        }
                                                }
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .padding(.vertical, 6)
                                                .onTapGesture {
                                                    selectPlace(PlaceSearchResultDto(displayName: bookmark.displayName, latitude: bookmark.latitude, longitude: bookmark.longitude))
                                                }
                                                if movingBookmark?.latitude == bookmark.latitude && movingBookmark?.longitude == bookmark.longitude {
                                                    moveFolderPicker()
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
                        // Real map-tap infrastructure (2026-07-23) -- see MapController's
                        // own doc comment. Wired once here since mapController itself is a
                        // stable @StateObject instance for this view's whole lifetime.
                        mapController.onSelectPlace = selectPlace
                        mapController.onMeasureTap = { coordinate in
                            guard measurePoints.count < 7 else { return }
                            let newPoint = (coordinate.latitude, coordinate.longitude)
                            measurePoints.append(newPoint)
                            lastMeasuredPlaceName = "Finding area…"
                            measureReverseGeocodeTask?.cancel()
                            measureReverseGeocodeTask = Task {
                                let name = try? await NetworkClient.shared.reverseGeocode(lat: newPoint.0, lng: newPoint.1).placeName
                                if !Task.isCancelled { lastMeasuredPlaceName = name ?? nil }
                            }
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
        .task(id: initialSearchQuery) {
            guard let initialSearchQuery, !initialSearchQuery.isEmpty else { return }
            query = initialSearchQuery
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

    private func selectAndRoute(_ place: PlaceSearchResultDto) {
        selectPlace(place)
        Task { await getDirections(to: place) }
    }

    // mode defaults to the currently-selected travelMode (2026-07-22) -- called both by
    // the initial "Directions" tap and by the driving/walking toggle when a route is
    // already shown, mirroring bank-mfe's own applyRoute/handleGetDirections split.
    private func getDirections(mode: String? = nil, to destination: PlaceSearchResultDto? = nil) async {
        guard let place = destination ?? selectedPlace else { return }
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

    private func getItineraryDirections() async {
        guard itineraryStops.count >= 2 else { return }
        routing = true; error = nil; defer { routing = false }
        do {
            let response = try await NetworkClient.shared.getItineraryDirections(waypoints: itineraryStops.map { ItineraryWaypointRequest(latitude: $0.latitude, longitude: $0.longitude) }, mode: travelMode)
            route = response.route; routeAlternatives = nil; selectedRouteIndex = 0; showSteps = false
        } catch { self.error = "Could not find a route for this itinerary." }
    }

    // Real distance-measurement (ruler) tool (2026-07-23) -- ported from bank-mfe's own
    // real MapView.tsx toggleMeasuring/handleRouteItinerary. Genuinely distinct from
    // Directions: no road route, no OSRM call to enter/build it -- just the plain
    // straight-line distance between tapped points, until "Route itinerary" is tapped.
    private func toggleMeasuring() {
        measuring.toggle()
        mapController.isMeasuring = measuring
        measurePoints = []
        lastMeasuredPlaceName = nil
        itineraryStops = []
    }

    // Real straight-line distance -- the same Haversine great-circle formula
    // rw.itunda.core.geo.GeoUtils.haversineKm implements on the backend, kept as a
    // plain local function since a ruler tool needs to update live as a user taps, not
    // once per API call.
    private func haversineKm(_ lat1: Double, _ lng1: Double, _ lat2: Double, _ lng2: Double) -> Double {
        let r = 6371.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLng = (lng2 - lng1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private var measureTotalKm: Double {
        guard measurePoints.count > 1 else { return 0 }
        return zip(measurePoints, measurePoints.dropFirst()).reduce(0) { total, pair in
            total + haversineKm(pair.0.0, pair.0.1, pair.1.0, pair.1.1)
        }
    }

    // Converts the ruler's tapped points directly into a real driving/walking route via
    // the existing getItineraryDirections() -- it already treats itineraryStops as the
    // complete waypoint list with no implicit "from my location" prepend, exactly the
    // semantics bank-mfe's own handleRouteItinerary needs: the ruler's own first tapped
    // point IS the start.
    private func routeMeasuredItinerary() async {
        guard measurePoints.count >= 2, measurePoints.count <= 7 else { return }
        itineraryStops = measurePoints.map { PlaceSearchResultDto(displayName: "Measured point", latitude: $0.0, longitude: $0.1) }
        await getItineraryDirections()
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
                ForEach(["Home", "Work"], id: \.self) { preset in
                    Button(preset) { folderNameInput = preset }
                        .font(.caption).bold().foregroundColor(folderNameInput.caseInsensitiveCompare(preset) == .orderedSame ? .white : IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(folderNameInput.caseInsensitiveCompare(preset) == .orderedSame ? IDS.Colors.brand : IDS.Colors.chipBackground)
                        .cornerRadius(999)
                }
            }
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
            // Real itunda cash-agent discovery (item 158) -- distinct dedicated
            // endpoint, same real special-case Android's own MapsScreen.kt and
            // bank-mfe's MapView.tsx (item 157) already established for this category.
            if categoryId == "ITUNDA_AGENT" {
                let agents = try await NetworkClient.shared.searchNearbyAgents(lat: center.latitude, lng: center.longitude).agents
                categoryResults = agents.map { NearbyPlaceDto(displayName: $0.displayName, latitude: $0.latitude, longitude: $0.longitude, distanceKm: $0.distanceKm) }
            } else {
                categoryResults = try await NetworkClient.shared.searchNearbyPlaces(category: categoryId, lat: center.latitude, lng: center.longitude).places
            }
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

    // Real "move to folder" (found 2026-07-22: NetworkClient.moveMapBookmark already
    // existed with zero UI calling it anywhere).
    private func confirmMoveBookmark() async {
        guard let target = movingBookmark else { return }
        moving = true
        error = nil
        defer { moving = false }
        do {
            let updated = try await NetworkClient.shared.moveMapBookmark(
                latitude: target.latitude, longitude: target.longitude,
                folderName: moveFolderNameInput, color: moveFolderColorInput
            ).bookmark
            bookmarks = try await NetworkClient.shared.getMyMapBookmarks().bookmarks
            _ = updated
            movingBookmark = nil
        } catch {
            self.error = "Could not move this bookmark."
        }
    }

    @ViewBuilder
    private func moveFolderPicker() -> some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField("Folder name", text: $moveFolderNameInput)
                .font(.caption)
                .padding(8)
                .background(IdsPalette.white)
                .cornerRadius(8)
            HStack(spacing: 6) {
                ForEach(bookmarkColorPalette, id: \.self) { hex in
                    let active = moveFolderColorInput == hex
                    Circle()
                        .fill(colorFromHex(hex))
                        .frame(width: 22, height: 22)
                        .overlay(Circle().stroke(IDS.Colors.textPrimary, lineWidth: active ? 2 : 0))
                        .onTapGesture { moveFolderColorInput = hex }
                }
            }
            Button(action: { Task { await confirmMoveBookmark() } }) {
                Text(moving ? "Saving…" : "Save")
                    .font(.subheadline).bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                    .background(IDS.Colors.brand).cornerRadius(8)
            }
        }
        .padding(8)
        .background(IDS.Colors.chipBackground)
        .cornerRadius(8)
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
// Real distance-measurement (ruler) tool (2026-07-23) -- same real GeoJSON-source +
// line-layer technique as the route above, dashed and a deliberately different color
// so a real OSRM road route and a plain straight-line measurement are never visually
// confused. See Android's MapsScreen.kt for the field-for-field mirror.
private let measureSourceIdentifier = "itunda-measure"
private let measureLineLayerIdentifier = "itunda-measure-line"
private let measurePointsLayerIdentifier = "itunda-measure-points"

private let nearbyAnnotationTitlePrefix = "itunda-nearby:"

private struct MapLibreMapRepresentable: UIViewRepresentable {
    let merchants: [ShoppingMerchantDto]
    let myLocation: CLLocationCoordinate2D?
    let destination: PlaceSearchResultDto?
    let routeGeometry: [[Double]]?
    let nearbyPlaces: [NearbyPlaceDto]?
    let measurePoints: [(Double, Double)]
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
        // Real map-tap infrastructure (2026-07-23) -- the ruler tool needs a raw tap
        // anywhere on the map, not just on an existing annotation (unlike merchant/
        // nearby-pin selection, which MLNPointAnnotation already supports natively via
        // `didSelect` below). `shouldRecognizeSimultaneously` lets this coexist with
        // MLNMapView's own built-in pan/zoom/annotation-select gestures rather than
        // stealing them.
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        tap.delegate = context.coordinator
        mapView.addGestureRecognizer(tap)
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
        context.coordinator.pendingMeasurePoints = measurePoints
        context.coordinator.applyMeasure(to: mapView)
    }

    func makeCoordinator() -> Coordinator { Coordinator(myLocationAnnotationTitle: myLocationAnnotationTitle, controller: controller) }

    final class Coordinator: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
        let myLocationAnnotationTitle: String
        let controller: MapController
        var pendingRouteGeometry: [[Double]]?
        var pendingMeasurePoints: [(Double, Double)] = []
        init(myLocationAnnotationTitle: String, controller: MapController) {
            self.myLocationAnnotationTitle = myLocationAnnotationTitle
            self.controller = controller
        }

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

            // Real distance-measurement (ruler) tool source/layers (2026-07-23).
            let measureSource = MLNShapeSource(identifier: measureSourceIdentifier, shape: nil, options: nil)
            style.addSource(measureSource)
            let measureLine = MLNLineStyleLayer(identifier: measureLineLayerIdentifier, source: measureSource)
            measureLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.898, green: 0.224, blue: 0.208, alpha: 1))
            measureLine.lineWidth = NSExpression(forConstantValue: 3)
            measureLine.lineDashPattern = NSExpression(forConstantValue: [2, 1.5])
            measureLine.lineCap = NSExpression(forConstantValue: "round")
            measureLine.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(measureLine)
            let measurePointsLayer = MLNCircleStyleLayer(identifier: measurePointsLayerIdentifier, source: measureSource)
            measurePointsLayer.circleRadius = NSExpression(forConstantValue: 6)
            measurePointsLayer.circleColor = NSExpression(forConstantValue: UIColor(red: 0.898, green: 0.224, blue: 0.208, alpha: 1))
            measurePointsLayer.circleStrokeWidth = NSExpression(forConstantValue: 2)
            measurePointsLayer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            style.addLayer(measurePointsLayer)
            applyMeasure(to: mapView)
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

        // Real distance-measurement (ruler) tool -- keeps the measure GeoJSON source (a
        // dot per tapped point, a dashed line once there are 2+) in sync with real
        // tapped points, matching the real route-drawing technique above.
        func applyMeasure(to mapView: MLNMapView) {
            guard let style = mapView.style, let source = style.source(withIdentifier: measureSourceIdentifier) as? MLNShapeSource else { return }
            guard !pendingMeasurePoints.isEmpty else {
                source.shape = nil
                return
            }
            let coordinates = pendingMeasurePoints.map { CLLocationCoordinate2D(latitude: $0.0, longitude: $0.1) }
            var shapes: [MLNShape] = coordinates.map { coordinate in
                let point = MLNPointFeature()
                point.coordinate = coordinate
                return point
            }
            if coordinates.count > 1 {
                shapes.append(MLNPolylineFeature(coordinates: coordinates, count: UInt(coordinates.count)))
            }
            source.shape = MLNShapeCollectionFeature(shapes: shapes)
        }

        // Real map-tap infrastructure (2026-07-23) -- see MapController's own doc
        // comment. Only acts while the ruler tool is active; otherwise this is a no-op
        // and MLNMapView's own built-in gestures (pan/zoom/annotation-select) handle the
        // tap as normal.
        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard controller.isMeasuring, let mapView = gesture.view as? MLNMapView else { return }
            let point = gesture.location(in: mapView)
            let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
            controller.onMeasureTap?(coordinate)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            true
        }

        // Real map-tap-to-select infrastructure (2026-07-23) -- a merchant/nearby-place
        // pin is a real MLNPointAnnotation, which MapLibre iOS already supports tapping
        // natively (unlike Android's GeoJsonSource-backed circles, which need a real
        // queryRenderedFeatures call to identify what was tapped). Routes through to the
        // same selectPlace the search results/bookmarks already use.
        func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
            guard let title = annotation.title ?? nil, title != myLocationAnnotationTitle else { return }
            let displayName = title.hasPrefix(nearbyAnnotationTitlePrefix) ? String(title.dropFirst(nearbyAnnotationTitlePrefix.count)) : title
            controller.onSelectPlace?(PlaceSearchResultDto(displayName: displayName, latitude: annotation.coordinate.latitude, longitude: annotation.coordinate.longitude))
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
