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

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- mirrors
// bank-mfe's MapView.tsx MAP_STYLE / Android's MapScreen.kt MAP_STYLE_JSON exactly (same
// source, same layer set, no text labels yet since that needs a separate self-hosted
// glyphs server). MLNMapView takes a styleURL, not an inline JSON string, so this is
// written to a temp file once and referenced by file:// URL below.
private let mapStyleJSON = """
{
  "version": 8,
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
      "paint": { "line-color": "#a08ccb", "line-width": 1, "line-dasharray": [2, 1] } }
  ]
}
"""

private func writeStyleFile() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("itunda-map-style.json")
    try? mapStyleJSON.write(to: url, atomically: true, encoding: .utf8)
    return url
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
    @State private var selectedPlace: PlaceSearchResultDto?
    @State private var route: RouteResultDto?
    @State private var routing = false
    @State private var error: String?
    @StateObject private var locationFetcher = LocationFetcher()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    TextField("Search a real place in Rwanda", text: $query)
                        .padding(10)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                    Button(action: { Task { await search() } }) {
                        Text(searching ? "…" : "Search").font(.subheadline).bold().foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(searching || query.trimmingCharacters(in: .whitespaces).isEmpty)
                    Button(action: { locationFetcher.requestLocation() }) {
                        Image(systemName: "location.fill").foregroundColor(IDS.Colors.brand)
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 8)

                if let results = searchResults {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 2) {
                            if results.isEmpty {
                                Text("No real places found for that search.").font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(8)
                            } else {
                                ForEach(Array(results.enumerated()), id: \.offset) { _, place in
                                    Text(place.displayName)
                                        .font(.caption)
                                        .foregroundColor(IDS.Colors.textPrimary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(10)
                                        .onTapGesture { selectPlace(place) }
                                }
                            }
                        }
                    }
                    .frame(maxHeight: 160)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                }

                if let error {
                    Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
                }

                MapLibreMapRepresentable(merchants: merchants, myLocation: locationFetcher.coordinate, destination: selectedPlace, routeGeometry: route?.geometry)
                    .ignoresSafeArea(edges: .bottom)

                if let place = selectedPlace {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(place.displayName).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let route {
                            Text("🚗 \(String(format: "%.1f", route.distanceKm)) km · \(Int(route.durationMinutes)) min by real road, via itunda's own self-hosted OSRM")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            Button(action: { Task { await getDirections() } }) {
                                Text(routing ? "Finding real route…" : "Directions")
                                    .font(.subheadline).bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                                    .background(IDS.Colors.brand).cornerRadius(12)
                            }
                            .disabled(routing)
                        }
                    }
                    .padding(IDS.Layout.screenHorizontal)
                    .background(IDS.Colors.card)
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
    }

    private func getDirections() async {
        guard let place = selectedPlace else { return }
        routing = true
        error = nil
        defer { routing = false }
        let origin = locationFetcher.coordinate ?? CLLocationCoordinate2D(latitude: rwandaCenterLat, longitude: rwandaCenterLng)
        do {
            route = try await NetworkClient.shared.getDirections(
                fromLat: origin.latitude, fromLng: origin.longitude, toLat: place.latitude, toLng: place.longitude,
            ).route
        } catch {
            self.error = "Could not find directions to this place."
        }
    }
}

private let routeSourceIdentifier = "itunda-route"
private let routeLayerIdentifier = "itunda-route-line"

private struct MapLibreMapRepresentable: UIViewRepresentable {
    let merchants: [ShoppingMerchantDto]
    let myLocation: CLLocationCoordinate2D?
    let destination: PlaceSearchResultDto?
    let routeGeometry: [[Double]]?

    private let myLocationAnnotationTitle = "itunda-my-location"

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: writeStyleFile())
        mapView.delegate = context.coordinator
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: rwandaCenterLat, longitude: rwandaCenterLng),
            zoomLevel: 12,
            animated: false,
        )
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
        // real convention for a location indicator.
        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            guard annotation.title == myLocationAnnotationTitle else { return nil }
            let identifier = "itunda-my-location-view"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) ?? MLNAnnotationView(reuseIdentifier: identifier)
            view.frame = CGRect(x: 0, y: 0, width: 18, height: 18)
            view.backgroundColor = UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 1.0)
            view.layer.cornerRadius = 9
            view.layer.borderColor = UIColor.white.cgColor
            view.layer.borderWidth = 3
            return view
        }
    }
}
