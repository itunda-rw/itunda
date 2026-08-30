import SwiftUI
import CoreDesignSystem
import CoreNetwork

// OverviewScreenView moved to Features/Assets/Sources/OverviewScreenView.swift and
// CreditScoreScreenView/LoansScreenView moved to Features/Credit/Sources/ (2026-07-23,
// 2026-08-30) -- see project_itunda_feature_isolation's own memory for the extraction
// order/rationale. Certificate/Identity/Support below are still open candidates.

// Real digital identity/signing certificate (2026-07-22 port) -- this feature was
// already real and live-verified on bank-mfe (web) since 2026-07-17, and ported to
// Android the same day as this iOS port; both used it as the reference for a
// platform-parity gap, not a never-built feature.
struct CertificateScreenView: View {
    var onBack: () -> Void = {}
    @State private var certificate: CertificateDto?
    @State private var loaded = false
    @State private var issuedPrivateKey: String?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Itunda Certificate").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !loaded {
                        ProgressView()
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("A digital certificate you can use to sign agreements in Itunda. You'll need a verified identity first.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if let certificate, certificate.status == "ACTIVE" {
                                Text("Active").bold().foregroundColor(.green)
                                Text("Serial \(certificate.serialNumber)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Text("Expires \(certificate.expiresAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Button(action: { Task { await revoke() } }) {
                                    Text(busy ? "Revoking…" : "Revoke certificate").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                }
                                .disabled(busy)
                            } else {
                                if let certificate {
                                    Text("Your previous certificate was \(certificate.status.lowercased()).").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Button(action: { Task { await issue() } }) {
                                    Text(busy ? "Issuing…" : "Issue a certificate").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy)
                            }
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped this Card wrapper
                        // and VerifyCertificateCard's own -- 2 real sections shown together on
                        // this screen, so a Divider marks the boundary between them instead
                        // (docs/UI_UX_GUIDELINES.md §10). The orange private-key warning box
                        // below is deliberately left as-is -- a distinct color treatment, not
                        // itunda-card, signaling a one-time, high-stakes notice.
                        .padding(.vertical, 10)

                        if let issuedPrivateKey {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Save this private key now — you won't be able to see it again.").font(.subheadline).bold().foregroundColor(.orange)
                                Text(issuedPrivateKey).font(.system(.caption, design: .monospaced))
                            }
                            .padding(16).background(Color.orange.opacity(0.1)).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                        if let error { Text(error).font(.caption).foregroundColor(.red) }

                        Divider().overlay(IDS.Colors.divider)
                        VerifyCertificateCard()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do { certificate = try await NetworkClient.shared.getMyCertificate().certificate } catch { self.error = "Could not load your certificate." }
            loaded = true
        }
    }

    private func issue() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            let result = try await NetworkClient.shared.issueCertificate()
            certificate = result.certificate
            issuedPrivateKey = result.privateKey
        } catch NetworkError.httpError(let code) where code == 403 {
            error = "You need a verified identity before you can issue a certificate."
        } catch {
            self.error = "Could not issue a certificate."
        }
    }

    private func revoke() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            certificate = try await NetworkClient.shared.revokeCertificate().certificate
            issuedPrivateKey = nil
        } catch { self.error = "Could not revoke your certificate." }
    }
}

// Real public certificate status/verify (2026-08-04) -- see NetworkClient.swift's own
// doc comment on getCertificateStatus/verifyCertificateSignature: the two endpoints
// that answer "does this signed thing check out," found via a fresh backend-endpoint
// sweep with zero client anywhere. Deliberately separate from CertificateScreenView
// above -- that one manages the caller's own certificate; this one checks someone
// else's.
private struct VerifyCertificateCard: View {
    @State private var serialNumber = ""
    @State private var payload = ""
    @State private var signature = ""
    @State private var statusResult: CertificateDto?
    @State private var verifyResult: VerifyCertificateSignatureResponse?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Verify a certificate").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("Check whether a certificate serial number is still active, or verify a document someone signed with theirs.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)

            IdsTextField("Serial number", text: $serialNumber)
                .onChange(of: serialNumber) { _ in statusResult = nil; verifyResult = nil }
            Button(action: { Task { await checkStatus() } }) {
                Text(busy ? "Checking…" : "Check status").bold().frame(maxWidth: .infinity).padding(10)
                    .background(IDS.Colors.chipBackground).cornerRadius(8)
            }
            .disabled(busy || serialNumber.isEmpty)

            if let statusResult {
                Text("Status: \(statusResult.status) · Expires \(statusResult.expiresAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }

            Text("Verify a signature").font(.footnote).bold().foregroundColor(IDS.Colors.textPrimary).padding(.top, 6)
            IdsTextField("Payload (the exact text they signed)", text: $payload)
                .onChange(of: payload) { _ in verifyResult = nil }
            IdsTextField("Signature (base64)", text: $signature)
                .onChange(of: signature) { _ in verifyResult = nil }
            Button(action: { Task { await verify() } }) {
                Text(busy ? "Verifying…" : "Verify signature").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(busy || serialNumber.isEmpty || payload.isEmpty || signature.isEmpty)

            if let verifyResult {
                Text(verifyResult.signatureValid ? "✓ Signature is valid" : "✗ Signature does not match")
                    .bold().foregroundColor(verifyResult.signatureValid ? .green : .red)
                Text("Certificate status: \(verifyResult.certificateStatus)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
        }
        .padding(.vertical, 10)
    }

    private func checkStatus() async {
        busy = true; error = nil; verifyResult = nil
        defer { busy = false }
        do {
            statusResult = try await NetworkClient.shared.getCertificateStatus(serialNumber: serialNumber).certificate
        } catch {
            statusResult = nil
            self.error = "No certificate found with that serial number."
        }
    }

    private func verify() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            verifyResult = try await NetworkClient.shared.verifyCertificateSignature(serialNumber: serialNumber, payload: payload, signature: signature)
        } catch {
            verifyResult = nil
            self.error = "Could not verify this signature."
        }
    }
}

// Real personal KYC identity submission (2026-07-22) -- found fully built on the
// backend (rw.itunda.identity) with zero client UI anywhere; merchant-mfe already has
// KYB submission and ops-mfe the review queue, but this ordinary personal
// NATIONAL_ID/PASSPORT submission had zero UI on any client.
private let identityDocumentTypes = ["NATIONAL_ID", "PASSPORT"]

struct IdentityScreenView: View {
    var onBack: () -> Void = {}
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

    var body: some View {
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

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere. A ticket is always tied to a
// specific transaction (see SupportTicket.kt's own doc comment for why), so this
// screen has the user pick one from their real transaction history rather than
// filing a free-floating complaint.
private let supportCategories = ["GENERAL", "PAYMENT_DISPUTE", "ACCOUNT_TAKEOVER"]

struct SupportScreenView: View {
    var onBack: () -> Void = {}
    @State private var tickets: [SupportTicketDto]?
    @State private var transactions: [TransactionDto] = []
    @State private var error: String?
    @State private var busy = false
    @State private var showNewForm = false
    @State private var selectedTransactionId: String?
    @State private var category = supportCategories[0]
    @State private var descriptionText = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Support").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if !showNewForm {
                        Button(action: { showNewForm = true }) {
                            Text("Report an issue with a transaction").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Which transaction?").font(.caption).bold()
                            ForEach(transactions.prefix(10), id: \.id) { tx in
                                HStack {
                                    Text("\(tx.description) · \(tx.currency) \(Int(tx.amount))").font(.subheadline)
                                    Spacer()
                                    Image(systemName: selectedTransactionId == tx.id ? "largecircle.fill.circle" : "circle")
                                }
                                .onTapGesture { selectedTransactionId = tx.id }
                            }
                            Text("Category").font(.caption).bold()
                            HStack {
                                ForEach(supportCategories, id: \.self) { c in
                                    Button(c) { category = c }.font(.caption).bold()
                                        .foregroundColor(category == c ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 8).padding(.vertical, 6)
                                        .background(category == c ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(8)
                                }
                            }
                            IdsTextField("Describe the issue", text: $descriptionText)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit ticket").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || selectedTransactionId == nil || descriptionText.isEmpty)
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped this Card and the
                        // per-row Card below (kept its per-row Divider -- a real status log of
                        // past tickets), same pattern as IdentityScreenView above
                        // (docs/UI_UX_GUIDELINES.md §10 / docs/DESIGN_REFERENCES.md §274).
                        .padding(.vertical, 10)
                    }

                    Divider().overlay(IDS.Colors.divider)
                    Text("Your tickets").bold()
                    if let tickets {
                        if tickets.isEmpty { Text("You have no support tickets.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                        ForEach(tickets) { t in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(t.category).bold()
                                Text(t.description).font(.subheadline)
                                Text("Status: \(t.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                if let resolution = t.resolution { Text("Resolution: \(resolution)").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                Text("Filed: \(t.createdAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
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
    }

    private func refresh() async {
        do {
            tickets = try await NetworkClient.shared.getSupportTickets().tickets
            transactions = try await NetworkClient.shared.getTransactionHistory().transactions
            error = nil
        } catch { self.error = "Could not load support tickets." }
    }

    private func submit() async {
        guard let transactionId = selectedTransactionId else { return }
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.createSupportTicket(transactionId: transactionId, category: category, description: descriptionText)
            showNewForm = false; selectedTransactionId = nil; descriptionText = ""
            await refresh()
        } catch { self.error = "That ticket could not be submitted." }
    }
}
