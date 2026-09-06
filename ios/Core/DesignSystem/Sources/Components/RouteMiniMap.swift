import SwiftUI
import MapLibre
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// A real, compact, non-interactive drawn-route map -- reuses the exact same self-hosted
// OSRM directions itunda's own Maps feature already exposes (see MapScreenView.swift and
// the backend rw.itunda.maps.MapsService), so Eats/Marketplace get a real route +
// distance/duration instead of just a distance number, without duplicating any routing
// logic. Straight port of bank-mfe's RouteMiniMap.tsx / Android's RouteMiniMap.kt (item 8
// on the Maps "100%" roadmap).
// Address corrected 2026-07-27: itunda-dc-b (192.168.252.3) was decommissioned; the
// surviving sole node is itunda-dc-a, 192.168.252.4 -- see MapScreenView.swift's own note.
private let routeMiniTilesURL = "http://192.168.252.4:8090/rwanda/{z}/{x}/{y}.mvt"
private let routeMiniStyleJSON = """
{
  "version": 8,
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["\(routeMiniTilesURL)"], "minzoom": 0, "maxzoom": 14 },
    "route": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f2efe9" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "#d8e8c8", "fill-opacity": 0.6 } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "#a8d0e6" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "#ffffff", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "#f5c96b", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } }
  ]
}
"""

private func writeRouteMiniStyleFile() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("itunda-route-mini-style.json")
    try? routeMiniStyleJSON.write(to: url, atomically: true, encoding: .utf8)
    return url
}

public struct RouteMiniMap: View {
    let fromLat: Double
    let fromLng: Double
    let toLat: Double
    let toLng: Double
    let fromLabel: String
    let toLabel: String

    @State private var route: RouteResultDto?
    @State private var error: String?
    @State private var loading = true

    public init(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, fromLabel: String, toLabel: String) {
        self.fromLat = fromLat
        self.fromLng = fromLng
        self.toLat = toLat
        self.toLng = toLng
        self.fromLabel = fromLabel
        self.toLabel = toLabel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            RouteMiniMapRepresentable(
                fromLat: fromLat, fromLng: fromLng, toLat: toLat, toLng: toLng, routeGeometry: route?.geometry,
            )
            .frame(height: 160)
            .cornerRadius(12)
            if loading {
                Text("Finding the real road route…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if let route {
                Text("🚗 \(String(format: "%.1f", route.distanceKm)) km · \(Int(route.durationMinutes)) min by real road, via itunda's own self-hosted OSRM")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .task(id: "\(fromLat),\(fromLng),\(toLat),\(toLng)") {
            loading = true
            error = nil
            do {
                route = try await NetworkClient.shared.getDirections(fromLat: fromLat, fromLng: fromLng, toLat: toLat, toLng: toLng).route
            } catch {
                self.error = "Could not find a real route between these two points."
            }
            loading = false
        }
    }
}

private struct RouteMiniMapRepresentable: UIViewRepresentable {
    let fromLat: Double
    let fromLng: Double
    let toLat: Double
    let toLng: Double
    let routeGeometry: [[Double]]?

    private static let routeSourceIdentifier = "itunda-route-mini"
    private static let routeLayerIdentifier = "itunda-route-mini-line"
    private static let fromAnnotationTitle = "itunda-route-mini-from"
    private static let toAnnotationTitle = "itunda-route-mini-to"

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: writeRouteMiniStyleFile())
        mapView.delegate = context.coordinator
        mapView.isUserInteractionEnabled = false
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: (fromLat + toLat) / 2, longitude: (fromLng + toLng) / 2),
            zoomLevel: 12,
            animated: false,
        )
        let from = MLNPointAnnotation()
        from.coordinate = CLLocationCoordinate2D(latitude: fromLat, longitude: fromLng)
        from.title = Self.fromAnnotationTitle
        let to = MLNPointAnnotation()
        to.coordinate = CLLocationCoordinate2D(latitude: toLat, longitude: toLng)
        to.title = Self.toAnnotationTitle
        mapView.addAnnotations([from, to])
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.pendingRouteGeometry = routeGeometry
        context.coordinator.applyRoute(to: mapView)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var pendingRouteGeometry: [[Double]]?

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            let source = MLNShapeSource(identifier: RouteMiniMapRepresentable.routeSourceIdentifier, shape: nil, options: nil)
            style.addSource(source)
            let layer = MLNLineStyleLayer(identifier: RouteMiniMapRepresentable.routeLayerIdentifier, source: source)
            layer.lineColor = NSExpression(forConstantValue: UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 0.9))
            layer.lineWidth = NSExpression(forConstantValue: 5)
            layer.lineCap = NSExpression(forConstantValue: "round")
            layer.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(layer)
            applyRoute(to: mapView)
        }

        func applyRoute(to mapView: MLNMapView) {
            guard let style = mapView.style,
                  let source = style.source(withIdentifier: RouteMiniMapRepresentable.routeSourceIdentifier) as? MLNShapeSource
            else { return }
            guard let geometry = pendingRouteGeometry, !geometry.isEmpty else {
                source.shape = nil
                return
            }
            let coordinates = geometry.map { CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) }
            source.shape = MLNPolylineFeature(coordinates: coordinates, count: UInt(coordinates.count))
            var bounds = MLNCoordinateBounds(sw: coordinates[0], ne: coordinates[0])
            for c in coordinates { bounds = MLNCoordinateBoundsExtend(bounds, c) }
            mapView.setVisibleCoordinateBounds(bounds, edgePadding: UIEdgeInsets(top: 24, left: 24, bottom: 24, right: 24), animated: true, completionHandler: nil)
        }

        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            let title = annotation.title ?? nil
            let identifier: String
            let color: UIColor
            let size: CGFloat
            if title == RouteMiniMapRepresentable.fromAnnotationTitle {
                identifier = "itunda-route-mini-from-view"
                color = UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 1.0)
                size = 16
            } else if title == RouteMiniMapRepresentable.toAnnotationTitle {
                identifier = "itunda-route-mini-to-view"
                color = UIColor(red: 0.898, green: 0.224, blue: 0.208, alpha: 1.0)
                size = 18
            } else {
                return nil
            }
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) ?? MLNAnnotationView(reuseIdentifier: identifier)
            view.frame = CGRect(x: 0, y: 0, width: size, height: size)
            view.backgroundColor = color
            view.layer.cornerRadius = size / 2
            view.layer.borderColor = UIColor.white.cgColor
            view.layer.borderWidth = 2
            return view
        }

        func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool { false }
    }
}

// Real GeoJSON coordinate-bounds helper -- MapLibre iOS's MLNCoordinateBounds has no
// built-in "extend by point" (unlike the JS SDK's LngLatBounds.extend), so this is a
// small, self-contained equivalent.
private func MLNCoordinateBoundsExtend(_ bounds: MLNCoordinateBounds, _ coordinate: CLLocationCoordinate2D) -> MLNCoordinateBounds {
    MLNCoordinateBounds(
        sw: CLLocationCoordinate2D(latitude: min(bounds.sw.latitude, coordinate.latitude), longitude: min(bounds.sw.longitude, coordinate.longitude)),
        ne: CLLocationCoordinate2D(latitude: max(bounds.ne.latitude, coordinate.latitude), longitude: max(bounds.ne.longitude, coordinate.longitude)),
    )
}
