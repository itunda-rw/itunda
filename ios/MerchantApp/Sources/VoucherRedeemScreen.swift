import SwiftUI
import CoreDesignSystem

/// Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption --
/// ported field-for-field from merchant-mfe's own CollectScreen.tsx VoucherRedeem
/// (and Android's own VoucherRedeemScreen.kt): the customer shows the merchant their
/// voucher (its real id, from their own itunda app), the merchant enters it here to
/// redeem -- never a self-serve redeem the customer could fake. Real, shipped on the
/// backend + merchant-mfe/Android with zero iOS MerchantApp client until now --
/// found via a cross-platform-parity check.
struct VoucherRedeemTab: View {
    @State private var voucherId = ""
    @State private var result: RedeemedGiftVoucherDto?
    @State private var error: String?
    @State private var submitting = false
    // Real device step-up -- same DeviceStepUpDialog convention CardCheckoutView
    // above already establishes for money-moving merchant actions.
    @State private var needsDeviceVerification = false

    var body: some View {
        if needsDeviceVerification {
            ZStack {
                Color.black.opacity(0.3).ignoresSafeArea()
                DeviceStepUpDialog(
                    onVerified: { Task { await redeem() } },
                    onCancel: { needsDeviceVerification = false }
                )
            }
        } else if let result {
            VStack(spacing: 12) {
                Text("Voucher redeemed").bold()
                Text(result.productNameSnapshot ?? "\(formattedRWF(result.amount)) RWF")
                    .bold().foregroundColor(IDS.Colors.brand)
                Button(action: { self.result = nil; voucherId = "" }) {
                    Text("Redeem another").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            }
            .padding(24)
        } else {
            VStack(alignment: .leading, spacing: 12) {
                Text("Redeem a gift voucher").font(.headline)
                Text("Ask the customer for their voucher id and enter it below to redeem it in person.")
                    .font(.footnote).foregroundColor(.secondary)
                IdsTextField("giftvoucher_...", text: $voucherId)
                if let error {
                    Text(error).foregroundColor(.red).font(.footnote)
                }
                Button(action: { Task { await redeem() } }) {
                    Text(submitting ? "Redeeming…" : "Redeem").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(submitting || voucherId.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(16)
        }
    }

    private func redeem() async {
        let id = voucherId.trimmingCharacters(in: .whitespaces)
        guard !id.isEmpty else { return }
        submitting = true
        error = nil
        needsDeviceVerification = false
        defer { submitting = false }
        do {
            let response = try await MerchantNetworkClient.shared.redeemGiftVoucher(id)
            result = response.voucher
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch {
            self.error = "Could not redeem this voucher."
        }
    }
}
