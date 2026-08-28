import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Extracted from MapScreenView.swift (2026-08-28, itunda Maps redesign) -- travelModeToggle/
// busResultsView/routeAlternativesPicker were already their own @ViewBuilder funcs inside
// MapScreenView.swift for a real reason (inlined, the combined nesting made the Swift
// type-checker time out -- "unable to type-check this expression in reasonable time"). This
// pass adds BIKING + real Kigali GTFS-scheduled TRANSIT (kept deliberately separate from the
// existing intercity-bus tab, see transitResults' own doc comment) on top of that same
// surface, and MapScreenView.swift is already at its frozen file-size-lint baseline with zero
// slack, so this pulls the whole directions block into its own file rather than growing the
// original functions in place -- same "values in, callbacks out" shape Android's own
// MapRoutePlanningView.kt extraction already uses for the identical UI.
struct MapRoutePlanningView: View {
    let placeName: String
    let route: RouteResultDto?
    @Binding var travelMode: String
    let routing: Bool
    let busSearching: Bool
    let busTrips: [BusTripDto]?
    let transitSearching: Bool
    let transitJourneys: [TransitJourneyDto]?
    let routeAlternatives: [RouteResultDto]?
    let selectedRouteIndex: Int
    @Binding var showSteps: Bool
    let onFetchDirections: (String) -> Void
    let onSearchBus: () -> Void
    let onSearchTransit: () -> Void
    let onGetDirections: () -> Void
    let onSelectAlternative: (Int, RouteResultDto) -> Void

    private static let modes: [(String, String)] = [
        ("DRIVING", "🚗 Driving"), ("WALKING", "🚶 Walking"), ("BIKING", "🚴 Bike"),
        ("BUS", "🚌 Intercity bus"), ("TRANSIT", "🚏 City transit"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            modeToggle
            if travelMode == "BUS" {
                busResults
            } else if travelMode == "TRANSIT" {
                transitResults
            } else if let route {
                routeInfo(route)
            } else {
                Button(action: onGetDirections) {
                    Text(routing ? "Finding real route…" : "Directions")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(12)
                }
                .disabled(routing)
            }
        }
    }

    @ViewBuilder
    private var modeToggle: some View {
        HStack(spacing: 6) {
            ForEach(Self.modes, id: \.0) { mode, label in
                let active = travelMode == mode
                Button(action: {
                    guard mode != travelMode else { return }
                    if mode == "BUS" {
                        travelMode = "BUS"
                        onSearchBus()
                    } else if mode == "TRANSIT" {
                        travelMode = "TRANSIT"
                        onSearchTransit()
                    } else if route != nil {
                        onFetchDirections(mode)
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
                .disabled(routing || busSearching || transitSearching)
            }
        }
    }

    @ViewBuilder
    private func routeInfo(_ route: RouteResultDto) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(travelModeIcon) \(String(format: "%.1f", route.distanceKm)) km · \(Int(route.durationMinutes)) min by real road, via itunda's own self-hosted OSRM")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let alternatives = routeAlternatives, alternatives.count > 1 {
                alternativesPicker(alternatives)
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
    }

    private var travelModeIcon: String {
        travelMode == "DRIVING" ? "🚗" : travelMode == "BIKING" ? "🚴" : "🚶"
    }

    @ViewBuilder
    private func alternativesPicker(_ alternatives: [RouteResultDto]) -> some View {
        HStack(spacing: 6) {
            ForEach(Array(alternatives.enumerated()), id: \.offset) { i, alt in
                let active = selectedRouteIndex == i
                let altLabel = "Route \(i + 1) · \(String(format: "%.1f", alt.distanceKm))km · \(Int(alt.durationMinutes))min"
                Button(action: { onSelectAlternative(i, alt) }) {
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

    @ViewBuilder
    private var busResults: some View {
        VStack(alignment: .leading, spacing: 8) {
            if busSearching {
                Text("Searching real scheduled trips to \(placeName)…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            } else if let trips = busTrips, !trips.isEmpty {
                ForEach(trips) { trip in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(trip.origin) → \(trip.destination)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("Scheduled · \(String(trip.departureTime.prefix(16)).replacingOccurrences(of: "T", with: " ")) · \(trip.availableSeats) seat(s) left · \(String(format: "%.0f", trip.farePerSeat)) RWF/seat")
                            .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(red: 0.949, green: 0.957, blue: 0.965))
                    .cornerRadius(10)
                }
            } else {
                Text("No scheduled bus trips found to \(placeName) right now.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
    }

    // Real Kigali GTFS-scheduled transit journeys (2026-08-28, itunda Maps redesign) --
    // deliberately separate from busResults above (that's itunda's own real intercity
    // coach marketplace; this is real published-schedule city transit). Honest schedule-
    // based labeling, no live-tracking claim, matching busResults' own discipline.
    @ViewBuilder
    private var transitResults: some View {
        VStack(alignment: .leading, spacing: 8) {
            if transitSearching {
                Text("Searching real scheduled transit…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            } else if let journeys = transitJourneys, !journeys.isEmpty {
                ForEach(Array(journeys.enumerated()), id: \.offset) { _, journey in
                    let routeLabel = journey.route.shortName ?? journey.route.longName ?? "Transit"
                    VStack(alignment: .leading, spacing: 2) {
                        Text("🚶 \(String(format: "%.1f", journey.walkToOriginStopKm)) km → 🚌 \(routeLabel) → 🚶 \(String(format: "%.1f", journey.walkFromDestinationStopKm)) km")
                            .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("\(journey.originStop.name) → \(journey.destinationStop.name) · Scheduled \(formatSecondsAfterMidnight(journey.departureSecondsAfterMidnight))–\(formatSecondsAfterMidnight(journey.arrivalSecondsAfterMidnight))")
                            .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(red: 0.949, green: 0.957, blue: 0.965))
                    .cornerRadius(10)
                }
            } else {
                Text("No scheduled transit journey found for this trip right now.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
    }
}

private func formatSecondsAfterMidnight(_ seconds: Int) -> String {
    let h = (seconds / 3600) % 24
    let m = (seconds % 3600) / 60
    return String(format: "%02d:%02d", h, m)
}
