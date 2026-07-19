import SwiftUI
import MapLibre
import CoreLocation

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

/// Real interactive Rwanda map -- itunda's own self-hosted Kakao Maps/Naver Maps-style
/// mapping, ported here from bank-mfe's MapView.tsx / Android's MapScreen.kt (same
/// self-hosted PMTiles tile server, same hand-written style, same real merchant
/// markers). Reached from My's Quick links, matching the Pay/Benefits precedent, since
/// neither bottom-nav row has a free slot.
struct MapScreenView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var merchants: [ShoppingMerchantDto] = []

    var body: some View {
        NavigationStack {
            MapLibreMapRepresentable(merchants: merchants)
                .ignoresSafeArea(edges: .bottom)
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
        }
    }
}

private struct MapLibreMapRepresentable: UIViewRepresentable {
    let merchants: [ShoppingMerchantDto]

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: writeStyleFile())
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
        let points = merchants.compactMap { merchant -> MLNPointAnnotation? in
            guard let lat = merchant.latitude, let lng = merchant.longitude else { return nil }
            let point = MLNPointAnnotation()
            point.coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            point.title = merchant.businessName
            return point
        }
        mapView.addAnnotations(points)
    }
}
