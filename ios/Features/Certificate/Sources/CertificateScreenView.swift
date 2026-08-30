import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real digital identity/signing certificate (2026-07-22 port) -- this feature was
// already real and live-verified on bank-mfe (web) since 2026-07-17, and ported to
// Android the same day as this iOS port; both used it as the reference for a
// platform-parity gap, not a never-built feature. Extracted out of App/Sources'
// OverviewLoansCreditScoreScreens.swift (2026-08-30) into its own FeatureCertificate
// module -- real, sourced Toss precedent (toss.tech/article/slash23-iOS's own example
// Microfeature list gives identity verification its own standalone module rather than
// folding it into a generic bucket) applied to this sibling concern too. See
// project_itunda_feature_isolation's own memory for the full rationale.
public struct CertificateScreenView: View {
    public var onBack: () -> Void
    public init(onBack: @escaping () -> Void = {}) { self.onBack = onBack }

    @State private var certificate: CertificateDto?
    @State private var loaded = false
    @State private var issuedPrivateKey: String?
    @State private var error: String?
    @State private var busy = false
    @State private var holderName: String?

    public var body: some View {
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
                                ItundaCertificateCard(certificate: certificate, holderName: holderName)
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
            // Real itunda-branded "Verified ID" credential card (2026-08-30) -- see
            // ItundaCertificateCard's own doc comment. Best-effort: a name-load
            // failure just falls back to a generic label, never blocks the
            // certificate itself.
            if let profile = try? await NetworkClient.shared.getProfile() {
                holderName = "\(profile.user.firstName) \(profile.user.lastName)"
            }
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

/// Real itunda-branded "Verified ID" credential card (2026-08-30, market-readiness
/// audit) -- the visual touchpoint real Toss 인증서/Apple Wallet/Google Wallet all give
/// a digital certificate that this screen never had (previously just bare "Active" +
/// monospace serial text, no card at all -- see bank-mfe's identical CertificateView.tsx
/// doc comment for the mirrored web version). itunda's own card design (indigo brand
/// gradient, itunda's own wordmark), never a replica of any government-issued ID --
/// shows only data the user already gave itunda (their name) plus certificate metadata
/// itunda itself generated.
private struct ItundaCertificateCard: View {
    let certificate: CertificateDto
    let holderName: String?

    private var isActive: Bool { certificate.status == "ACTIVE" }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("itunda").font(.system(size: 15, weight: .heavy)).foregroundColor(.white)
                    Text("Verified Certificate").font(.system(size: 11)).foregroundColor(.white.opacity(0.85))
                }
                Spacer()
                Image(systemName: "checkmark.shield.fill").foregroundColor(.white)
            }
            Spacer(minLength: 28)
            Text(holderName ?? "itunda user").font(.system(size: 18, weight: .bold)).foregroundColor(.white)
            Spacer(minLength: 18)
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("SERIAL").font(.system(size: 11)).foregroundColor(.white.opacity(0.75))
                    Text("\(certificate.serialNumber.prefix(4)) •••• \(certificate.serialNumber.suffix(4))")
                        .font(.system(size: 12, design: .monospaced)).foregroundColor(.white)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(isActive ? "VALID THRU" : certificate.status).font(.system(size: 11)).foregroundColor(.white.opacity(0.75))
                    if isActive {
                        Text(certificate.expiresAt.prefix(7)).font(.system(size: 12, weight: .bold)).foregroundColor(.white)
                    }
                }
            }
        }
        .padding(22)
        .background(
            LinearGradient(
                colors: isActive
                    ? [IDS.Colors.brand, Color(red: 0x5C / 255, green: 0x55 / 255, blue: 0xD8 / 255)]
                    : [Color(red: 0x9A / 255, green: 0x9A / 255, blue: 0xA5 / 255), Color(red: 0x6E / 255, green: 0x6E / 255, blue: 0x78 / 255)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
        )
        .cornerRadius(18)
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
