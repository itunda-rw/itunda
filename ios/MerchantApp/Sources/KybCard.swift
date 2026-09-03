import SwiftUI
import CoreDesignSystem

private let autoCheckLabels: [String: String] = [
    "MATCHED": "Matched a real registered business TIN.",
    "NOT_FOUND": "This TIN wasn't found -- a real reviewer will take a closer look.",
    "INVALID_FORMAT": "This doesn't look like a real 9-digit TIN -- a real reviewer will take a closer look.",
]

/// Real demo KYB structural pre-check -- ported from merchant-mfe's KybCard
/// (2026-09-03), see DemoKybVerificationService.kt's own doc comment on the backend.
/// Not a real RDB/RRA registry lookup, but a real 9-digit-TIN structural validator
/// plus a real human-review queue (the same ops-mfe Compliance queue personal KYC
/// already uses), never auto-decided.
struct KybCard: View {
    let merchant: MerchantDto

    @State private var submissions: [IdentitySubmissionDto]?
    @State private var tin = ""
    @State private var error: String?
    @State private var submitting = false

    private var latestKyb: IdentitySubmissionDto? {
        submissions?.filter { $0.documentType == "BUSINESS_TIN" }.max { $0.submittedAt < $1.submittedAt }
    }
    private var pending: Bool { latestKyb?.status == "PENDING" }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.shield").foregroundColor(merchant.kybVerified ? IDS.Colors.brand : .secondary)
                Text("Business verification").bold()
            }
            if merchant.kybVerified {
                IdsBadge("Verified")
            } else if pending {
                Text("Your submission is under review.").font(.footnote).foregroundColor(.secondary)
                if let status = latestKyb?.autoVerificationStatus, let label = autoCheckLabels[status] {
                    Text(label).font(.footnote).foregroundColor(.secondary)
                }
            } else {
                IdsTextField("Business TIN (9 digits)", text: $tin, keyboardType: .numberPad)
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Submitting…" : "Submit for verification")
                        .bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(tin.trimmingCharacters(in: .whitespaces).count == 9 && !submitting ? IDS.Colors.brand : IDS.Colors.textTertiary)
                        .cornerRadius(10)
                }
                .disabled(submitting || tin.trimmingCharacters(in: .whitespaces).count != 9)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .task { await load() }
    }

    private func load() async {
        submissions = (try? await MerchantNetworkClient.shared.getIdentityStatus().submissions) ?? []
    }

    private func submit() async {
        let trimmed = tin.trimmingCharacters(in: .whitespaces)
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await MerchantNetworkClient.shared.submitIdentity(documentType: "BUSINESS_TIN", documentNumber: trimmed, documentReference: "TIN \(trimmed)")
            tin = ""
            await load()
        } catch {
            self.error = "Could not submit. Try again."
        }
    }
}
