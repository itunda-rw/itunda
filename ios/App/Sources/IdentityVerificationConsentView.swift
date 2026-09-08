import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real "verify with itunda" identity-verification-for-partners consent screen
/// (Partners product-completeness pass, 2026-09-07) -- ported from Android's own
/// real, already-live-verified `IdentityVerificationConsentScreen`
/// (`ItundaAppScreen.kt`), reached the same way: a partner's own site shows an
/// `itunda://verify/{requestId}` deep link, this app parses it (see
/// `ContentView.swift`'s URL-scheme handling) and presents this screen. Genuinely
/// distinct from `Features/Identity/IdentityScreenView.swift` (personal KYC/NIDA
/// document submission) -- this is itunda vouching for a user's real identity TO a
/// third party, never itunda's own KYC flow.
struct IdentityVerificationConsentView: View {
    let requestId: String
    var onDone: () -> Void = {}

    @State private var request: IdentityVerificationRequestResponse?
    @State private var loading = true
    @State private var error: String?
    @State private var busy = false
    @State private var outcome: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button(action: onDone) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Close")
                Spacer()
                Text("Verify with itunda").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }

            Group {
                if loading {
                    Text("Loading request…").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                } else if let outcome {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(outcome).font(.headline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("You can return to the app that sent you here.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        Button(action: onDone) {
                            Text("Done").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                    }
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).font(.subheadline).foregroundColor(.red)
                        Button(action: onDone) {
                            Text("Close").bold().frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Color(.secondarySystemBackground)).cornerRadius(10)
                        }
                    }
                } else if let request, request.status != "PENDING" {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("This request has already been answered, or it expired. Requests are only valid for a few minutes.")
                            .font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                        Button(action: onDone) {
                            Text("Close").bold().frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Color(.secondarySystemBackground)).cornerRadius(10)
                        }
                    }
                } else if let request {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(request.partnerName) wants to verify your identity").font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("If you approve, itunda will share only this with them:").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        ForEach(request.requestedFields, id: \.self) { field in
                            Text("• \(field)").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                        }
                        Text("Nothing is shared unless you approve. itunda never shares your PIN, balance, or transaction history.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        // Real defense-in-depth (deep-link scheme-hijacking finding,
                        // 2026-09-08 -- see project_itunda_deeplink_scheme_hijacking
                        // memory's own named partial mitigation). itunda:// is a plain
                        // custom URL scheme on both platforms, not a domain-verified
                        // Universal Link/App Link, so another app COULD register the
                        // same scheme and render its own fake version of this exact
                        // screen before the real itunda app ever sees the tap. Fixing
                        // that for real needs a real hosted domain itunda doesn't have
                        // yet -- this doesn't close that gap, but it does close the
                        // most damaging thing a convincing fake screen could still try:
                        // asking the user to "confirm" by typing a real credential.
                        HStack(alignment: .top, spacing: 6) {
                            Image(systemName: "exclamationmark.shield.fill").foregroundColor(.orange).font(.caption)
                            Text("This screen will never ask you to type your PIN, password, or a one-time code. If it ever does, close it -- you're not in the real itunda app.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    Button(action: { Task { await approve() } }) {
                        Text(busy ? "…" : "Approve and share").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(busy)
                    Button(action: { Task { await decline() } }) {
                        Text("Decline").bold().frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.secondarySystemBackground)).cornerRadius(10)
                    }
                    .disabled(busy)
                }
            }
            Spacer()
        }
        .padding()
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        loading = true
        error = nil
        do {
            request = try await NetworkClient.shared.getIdentityVerificationRequest(requestId)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        loading = false
    }

    private func approve() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.approveIdentityVerification(requestId)
            outcome = "Shared with \(request?.partnerName ?? "the partner")."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func decline() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.declineIdentityVerification(requestId)
            outcome = "Declined. Nothing was shared."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
