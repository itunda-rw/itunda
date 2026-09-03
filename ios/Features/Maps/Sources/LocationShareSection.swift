import SwiftUI
import CoreDesignSystem
import CoreNetwork

private func formatShareExpiry(_ iso: String) -> String {
    let parser = ISO8601DateFormatter()
    parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = parser.date(from: iso) ?? {
        parser.formatOptions = [.withInternetDateTime]
        return parser.date(from: iso)
    }() ?? Date()
    let formatter = DateFormatter()
    formatter.dateFormat = "h:mm a"
    return formatter.string(from: date)
}

/// Real Kakao Map-style "친구위치" (Friend Location) live location sharing -- ported from
/// bank-mfe/maps-mfe (2026-09-03), a real gap: the backend (LiveLocationShareService,
/// MapsController's /location-share routes) and web already had this fully wired, native
/// clients had zero UI or network calls for it. A real, moving position shared with one
/// specific person for a bounded window, distinct from the static bookmark-folder
/// share/subscribe already ported above it in the saved-places sheet. "Live" means
/// periodically-refreshed via polling, not a push channel -- itunda has no WebSocket
/// infra for this specifically.
///
/// `onWatchedPositionChanged` fires with the watched incoming share's (lat, lng), or nil
/// when watching stops, so the host screen can fly the camera / drop a marker without
/// this section needing its own handle onto the MLNMapView.
struct LocationShareSection: View {
    let myLocation: (lat: Double, lng: Double)?
    let onWatchedPositionChanged: (Double, Double) -> Void
    let onStopWatching: () -> Void

    @State private var showStartShare = false
    @State private var recipientPhone = ""
    @State private var durationHours = 1
    @State private var busy = false
    @State private var error: String?
    @State private var myShares: [LiveLocationShareDto] = []
    @State private var sharesWithMe: [LiveLocationShareDto] = []
    @State private var watchingShareId: String?
    @State private var pushTask: Task<Void, Never>?
    @State private var watchTask: Task<Void, Never>?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Divider().padding(.top, 12)
            HStack {
                Text("📍 Live location sharing").font(.caption).bold().foregroundColor(IDS.Colors.textTertiary)
                Spacer()
                Button(showStartShare ? "Cancel" : "+ Share my location") {
                    showStartShare.toggle()
                }.font(.caption2).bold().foregroundColor(IDS.Colors.brand)
            }.padding(.top, 12)

            if showStartShare {
                VStack(alignment: .leading, spacing: 6) {
                    TextField("Recipient's phone number", text: $recipientPhone)
                        .font(.subheadline).padding(8).background(IDS.Colors.card).cornerRadius(6)
                    HStack(spacing: 6) {
                        Text("For").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        ForEach([1, 2, 3, 4, 5, 6], id: \.self) { h in
                            Text("\(h)h")
                                .font(.caption).bold()
                                .foregroundColor(durationHours == h ? IDS.Colors.brand : IDS.Colors.textSecondary)
                                .padding(4)
                                .onTapGesture { durationHours = h }
                        }
                    }
                    if let error { Text(error).font(.caption).foregroundColor(IDS.Colors.danger) }
                    Text(busy ? "Starting…" : "Start sharing")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(8)
                        .background(IDS.Colors.brand).cornerRadius(6)
                        .opacity(recipientPhone.trimmingCharacters(in: .whitespaces).isEmpty || busy ? 0.6 : 1)
                        .onTapGesture {
                            guard !busy, !recipientPhone.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                            startShare()
                        }
                }
                .padding(8).background(IDS.Colors.backgroundTertiary).cornerRadius(8)
            }

            ForEach(myShares) { s in
                HStack {
                    Text("Sharing until \(formatShareExpiry(s.expiresAt))").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button("Stop") { stopShare(s.id) }.font(.caption2).bold().foregroundColor(IDS.Colors.danger)
                }
            }

            if !sharesWithMe.isEmpty {
                Text("Shared with you").font(.caption2).bold().foregroundColor(IDS.Colors.textTertiary).padding(.top, 4)
                ForEach(sharesWithMe) { s in
                    HStack {
                        Text("Live location · until \(formatShareExpiry(s.expiresAt))").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        if watchingShareId == s.id {
                            Button("Stop watching") { stopWatching() }.font(.caption2).bold().foregroundColor(IDS.Colors.danger)
                        } else {
                            Button("View on map") { startWatching(s.id) }.font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                        }
                    }
                }
            }
        }
        .task { await reload() }
        .onDisappear { pushTask?.cancel(); watchTask?.cancel() }
    }

    private func reload() async {
        if let res = try? await NetworkClient.shared.getMyLocationShares() { myShares = res.shares }
        if let res = try? await NetworkClient.shared.getLocationSharesWithMe() { sharesWithMe = res.shares }
        if !myShares.isEmpty { startPushingMyLocation() }
    }

    private func startShare() {
        busy = true; error = nil
        Task {
            do {
                let share = try await NetworkClient.shared.startLocationShare(recipientPhoneNumber: recipientPhone.trimmingCharacters(in: .whitespaces), durationHours: durationHours).share
                myShares.insert(share, at: 0)
                showStartShare = false
                recipientPhone = ""
                startPushingMyLocation()
            } catch {
                self.error = "Could not start sharing your location."
            }
            busy = false
        }
    }

    // Real "client owns when to push a fresh reading" loop -- pushes this device's own
    // real location every 30s while at least one real share is active, matching
    // RideDriverService.updateLocation's own real backend rate limit (20/min = one push
    // every 3s minimum; 30s is comfortably under that with real headroom for retries).
    private func startPushingMyLocation() {
        pushTask?.cancel()
        pushTask = Task {
            while !Task.isCancelled {
                if let location = myLocation {
                    _ = try? await NetworkClient.shared.updateMyLocationShare(latitude: location.lat, longitude: location.lng)
                }
                try? await Task.sleep(nanoseconds: 30_000_000_000)
            }
        }
    }

    private func stopShare(_ id: String) {
        Task { _ = try? await NetworkClient.shared.stopLocationShare(id: id) }
        myShares.removeAll { $0.id == id }
        if myShares.isEmpty { pushTask?.cancel() }
    }

    // Real recipient-side watch -- polls the sharer's latest pushed position every 15s.
    private func startWatching(_ id: String) {
        watchTask?.cancel()
        watchingShareId = id
        watchTask = Task {
            while !Task.isCancelled {
                if let share = try? await NetworkClient.shared.getLocationShare(id: id).share,
                   let lat = share.latitude, let lng = share.longitude {
                    onWatchedPositionChanged(lat, lng)
                }
                try? await Task.sleep(nanoseconds: 15_000_000_000)
            }
        }
    }

    private func stopWatching() {
        watchTask?.cancel()
        watchingShareId = nil
        onStopWatching()
    }
}
