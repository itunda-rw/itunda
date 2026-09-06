import SwiftUI
import MapLibre
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Real live delivery tracking (item 183, 2026-07-29) -- "the defining 'watch your order
// arrive' moment every real Coupang Eats/Uber Eats-style app has," per
// EatsOrderService.getRiderLocation's own doc comment (backend real since 2026-07-19).
// bank-mfe's LiveRiderMap.tsx has had this since 2026-07-20; Android got its own port
// the same session (item 182, LiveRiderMiniMap.kt). Straight port of both: draws the
// restaurant->delivery route once (same self-hosted OSRM directions RouteMiniMap.swift
// already established) and polls the real rider position every 5s, moving a real
// annotation on top.
private let liveMiniTilesURL = "http://192.168.252.4:8090/rwanda/{z}/{x}/{y}.mvt"
private let liveMiniStyleJSON = """
{
  "version": 8,
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["\(liveMiniTilesURL)"], "minzoom": 0, "maxzoom": 14 },
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

private func writeLiveMiniStyleFile() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("itunda-live-rider-mini-style.json")
    try? liveMiniStyleJSON.write(to: url, atomically: true, encoding: .utf8)
    return url
}

// A real, honest "how long ago" label from the rider's own last real location push --
// never disguised as instantaneous, since the rider app only pushes every real 15s.
private func liveRiderTimeAgo(_ iso: String) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return "" }
    let seconds = max(0, Int(Date().timeIntervalSince(date)))
    if seconds < 5 { return "just now" }
    if seconds < 60 { return "\(seconds)s ago" }
    return "\(seconds / 60)m ago"
}

struct LiveRiderMiniMap: View {
    let orderId: String
    let fromLat: Double
    let fromLng: Double
    let toLat: Double
    let toLng: Double
    let fromLabel: String
    let toLabel: String

    @State private var route: RouteResultDto?
    @State private var location: RiderLocationDto?
    @State private var available: Bool?
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            LiveRiderMiniMapRepresentable(
                fromLat: fromLat, fromLng: fromLng, toLat: toLat, toLng: toLng,
                routeGeometry: route?.geometry, riderLocation: location
            )
            .frame(height: 200)
            .cornerRadius(12)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if available == false {
                Text("Waiting for your rider's real location…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if let location {
                Text("🛵 Rider location updated \(liveRiderTimeAgo(location.updatedAt))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .task(id: "\(fromLat),\(fromLng),\(toLat),\(toLng)") {
            do {
                route = try await NetworkClient.shared.getDirections(fromLat: fromLat, fromLng: fromLng, toLat: toLat, toLng: toLng).route
            } catch {
                // Real, non-critical -- the live rider marker below is the actual point
                // of this view; a missing route line just means slightly less context.
            }
        }
        .task(id: orderId) {
            while !Task.isCancelled {
                do {
                    let res = try await NetworkClient.shared.getEatsRiderLocation(orderId)
                    available = res.available
                    error = nil
                    if let loc = res.location { location = loc }
                } catch {
                    self.error = "Could not load your rider's location."
                }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }
}

private struct LiveRiderMiniMapRepresentable: UIViewRepresentable {
    let fromLat: Double
    let fromLng: Double
    let toLat: Double
    let toLng: Double
    let routeGeometry: [[Double]]?
    let riderLocation: RiderLocationDto?

    private static let routeSourceIdentifier = "itunda-live-rider-mini"
    private static let routeLayerIdentifier = "itunda-live-rider-mini-line"
    private static let fromAnnotationTitle = "itunda-live-rider-mini-from"
    private static let toAnnotationTitle = "itunda-live-rider-mini-to"
    private static let riderAnnotationTitle = "itunda-live-rider-mini-rider"

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: writeLiveMiniStyleFile())
        mapView.delegate = context.coordinator
        mapView.isUserInteractionEnabled = false
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: (fromLat + toLat) / 2, longitude: (fromLng + toLng) / 2),
            zoomLevel: 13,
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
        context.coordinator.applyRiderLocation(riderLocation, to: mapView)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var pendingRouteGeometry: [[Double]]?
        private var riderAnnotation: MLNPointAnnotation?

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            let source = MLNShapeSource(identifier: LiveRiderMiniMapRepresentable.routeSourceIdentifier, shape: nil, options: nil)
            style.addSource(source)
            let layer = MLNLineStyleLayer(identifier: LiveRiderMiniMapRepresentable.routeLayerIdentifier, source: source)
            layer.lineColor = NSExpression(forConstantValue: UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 0.9))
            layer.lineWidth = NSExpression(forConstantValue: 5)
            layer.lineCap = NSExpression(forConstantValue: "round")
            layer.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(layer)
            applyRoute(to: mapView)
        }

        func applyRoute(to mapView: MLNMapView) {
            guard let style = mapView.style,
                  let source = style.source(withIdentifier: LiveRiderMiniMapRepresentable.routeSourceIdentifier) as? MLNShapeSource
            else { return }
            guard let geometry = pendingRouteGeometry, !geometry.isEmpty else {
                source.shape = nil
                return
            }
            let coordinates = geometry.map { CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) }
            source.shape = MLNPolylineFeature(coordinates: coordinates, count: UInt(coordinates.count))
        }

        // Real independent rider marker, updated on each 5s poll -- separate from the
        // route above since the route doesn't change tick-to-tick, only the rider's
        // own position does.
        func applyRiderLocation(_ location: RiderLocationDto?, to mapView: MLNMapView) {
            guard let location else { return }
            let coordinate = CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude)
            if let riderAnnotation {
                riderAnnotation.coordinate = coordinate
            } else {
                let annotation = MLNPointAnnotation()
                annotation.coordinate = coordinate
                annotation.title = LiveRiderMiniMapRepresentable.riderAnnotationTitle
                riderAnnotation = annotation
                mapView.addAnnotation(annotation)
            }
        }

        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            let title = annotation.title ?? nil
            if title == LiveRiderMiniMapRepresentable.riderAnnotationTitle {
                let identifier = "itunda-live-rider-mini-rider-view"
                let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) ?? MLNAnnotationView(reuseIdentifier: identifier)
                view.frame = CGRect(x: 0, y: 0, width: 28, height: 28)
                view.backgroundColor = .clear
                let label = UILabel(frame: view.bounds)
                label.text = "🛵"
                label.font = .systemFont(ofSize: 22)
                label.textAlignment = .center
                view.subviews.forEach { $0.removeFromSuperview() }
                view.addSubview(label)
                return view
            }
            let identifier: String
            let color: UIColor
            let size: CGFloat
            if title == LiveRiderMiniMapRepresentable.fromAnnotationTitle {
                identifier = "itunda-live-rider-mini-from-view"
                color = UIColor(red: 0.19, green: 0.51, blue: 0.96, alpha: 1.0)
                size = 16
            } else if title == LiveRiderMiniMapRepresentable.toAnnotationTitle {
                identifier = "itunda-live-rider-mini-to-view"
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
