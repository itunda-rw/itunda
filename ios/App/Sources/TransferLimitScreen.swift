import SwiftUI
import CoreNetwork
import CoreDesignSystem

private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

/// Real "Transfer limit" screen (2026-09-01) -- the real, enforced
/// P2pTransferLimitService caps, previously surfaced only reactively as a decline
/// error on an oversized transfer. Mirrors web's TransferLimitScreen / Android's
/// TransferLimitScreen exactly. Lives in App/Sources for the same
/// App-module-type reason as VerificationMethodScreen.swift/DeviceListScreen.swift.
struct TransferLimitScreen: View {
    let onBack: () -> Void

    @State private var limit: TransferLimitResponse?
    @State private var error = false
    @State private var locale: AppLocale = loadStoredLocale()

    private let strings: [AppLocale: [String: String]] = [
        .en: [
            "title": "Transfer limit", "perTransfer": "Per transfer", "daily": "Daily",
            "remainingToday": "Remaining today", "error": "Could not load your transfer limit.", "loading": "Loading…",
        ],
        .rw: [
            "title": "Urugero rwo kohereza", "perTransfer": "Kuri buri koherezwa", "daily": "Ku munsi",
            "remainingToday": "Bisigaye uyu munsi", "error": "Ntibyakunze gushaka urugero rwawe rwo kohereza.", "loading": "Biratunganywa…",
        ],
        .fr: [
            "title": "Plafond de virement", "perTransfer": "Par virement", "daily": "Quotidien",
            "remainingToday": "Restant aujourd'hui", "error": "Impossible de charger votre plafond de virement.", "loading": "Chargement…",
        ],
    ]

    private func t(_ key: String) -> String {
        strings[locale]?[key] ?? strings[.en]?[key] ?? key
    }

    private func format(_ amount: Double) -> String {
        "\(formatAmount(Int(amount))) RWF"
    }

    private func load() async {
        do {
            limit = try await NetworkClient.shared.getTransferLimit()
        } catch {
            self.error = true
        }
    }

    private func row(_ label: String, _ value: String, color: Color = IDS.Colors.textPrimary) -> some View {
        HStack {
            Text(label).font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            Text(value).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(color)
        }
        .padding(.vertical, 12)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(t("title"))
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .padding(.bottom, 20)

                    if error {
                        Text(t("error"))
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.danger)
                    } else if let limit {
                        row(t("perTransfer"), format(limit.perTransferLimit))
                        row(t("daily"), format(limit.dailyLimit))
                        row(t("remainingToday"), format(limit.remainingToday), color: IDS.Colors.textBrand)
                    } else {
                        Text(t("loading"))
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { await load() }
    }
}
