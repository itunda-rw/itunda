import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real personal KYC identity submission (2026-07-22) -- found fully built on the
// backend (rw.itunda.identity) with zero client UI anywhere; merchant-mfe already has
// KYB submission and ops-mfe the review queue, but this ordinary personal
// NATIONAL_ID/PASSPORT submission had zero UI on any client. Extracted out of
// App/Sources' OverviewLoansCreditScoreScreens.swift (2026-08-30) into its own
// FeatureIdentity module -- real, sourced Toss precedent
// (toss.tech/article/slash23-iOS's own example Microfeature list names "본인확인"
// (identity verification) as a standalone module in its own right). This module is a
// different concern than CoreIdentity (device/biometric identity) -- kept deliberately
// separate, see Project.swift's own comment on the distinction.
private let identityDocumentTypes = ["NATIONAL_ID", "PASSPORT"]

public struct IdentityScreenView: View {
    public var onBack: () -> Void
    public init(onBack: @escaping () -> Void = {}) { self.onBack = onBack }

    @State private var submissions: [KycSubmissionDto]?
    @State private var error: String?
    @State private var busy = false
    @State private var documentType = identityDocumentTypes[0]
    @State private var documentNumber = ""
    @State private var documentReference = ""
    // Real, sourced Toss simplification (2026-08-24, toss.tech/article/signup: adding
    // WHY a personal-info request exists, not removing fields, is what measurably cut
    // Toss's own signup drop-off). bank-mfe's BankDashboard.tsx IdentityView already
    // got this exact fix (2026-08-19, a different sourced Toss article), Android got
    // it the same pass as this iOS fix -- reuses the same real live number both do
    // (CreditScoreService.KYC_VERIFIED_POINTS via the already-shipped
    // getCreditScoreSuggestions endpoint), not a hardcoded points value.
    @State private var kycPointsGain: Int?

    private var hasPending: Bool { submissions?.contains { $0.status == "PENDING" } ?? false }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Verify your identity").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if hasPending {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Submission pending review").bold()
                            Text("We'll update your status once it's reviewed.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped both Card wrappers
                        // in this if/else (mutually exclusive, no divider between them), and
                        // the per-row Card below (kept the per-row Divider -- a real status log
                        // of past submissions) (docs/UI_UX_GUIDELINES.md §10 /
                        // docs/DESIGN_REFERENCES.md §274).
                        .padding(.vertical, 10)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(kycPointsGain.map { "A quick, one-time check that confirms it's really you -- it protects your account from takeover, and raises your Credit Score by \($0) points once approved." }
                                ?? "A quick, one-time check that confirms it's really you -- it protects your account from takeover.")
                                .font(.caption)
                                .foregroundColor(IDS.Colors.textSecondary)
                            HStack {
                                ForEach(identityDocumentTypes, id: \.self) { t in
                                    Button(t) { documentType = t }.font(.caption).bold()
                                        .foregroundColor(documentType == t ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(documentType == t ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(999)
                                }
                            }
                            IdsTextField("Document number", text: $documentNumber)
                            IdsTextField("Document reference (scan/photo reference)", text: $documentReference)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit for review").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || documentNumber.isEmpty || documentReference.isEmpty)
                        }
                        .padding(.vertical, 10)
                    }

                    Divider().overlay(IDS.Colors.divider)
                    Text("Your submissions").bold()
                    if let submissions {
                        if submissions.isEmpty { Text("You have no submissions yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                        ForEach(submissions) { s in
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(s.documentType) · \(s.documentNumber)").bold()
                                Text("Status: \(s.status)").font(.subheadline)
                                if let reason = s.decisionReason { Text(reason).font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                Text("Filed: \(s.submittedAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(.vertical, 10)
                            Divider().overlay(IDS.Colors.divider)
                        }
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
        .task { await loadKycPointsGain() }
    }

    private func refresh() async {
        do { submissions = try await NetworkClient.shared.getIdentityStatus().submissions; error = nil }
        catch { self.error = "Could not load your identity status." }
    }

    private func loadKycPointsGain() async {
        // Non-critical -- the explanatory line just falls back to the generic wording
        // above when this call fails.
        kycPointsGain = try? await NetworkClient.shared.getCreditScoreSuggestions().suggestions
            .first { $0.action == "Verify your identity" }?.pointsGain
    }

    private func submit() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.submitIdentity(documentType: documentType, documentNumber: documentNumber, documentReference: documentReference)
            documentNumber = ""; documentReference = ""
            await refresh()
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            documentNumber = ""; documentReference = ""
            await refresh()
        } catch { self.error = "That submission could not be completed." }
    }
}
