import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) -- porting the
// same pattern already shipped on bank-mfe (ForeignCurrencyView.tsx's
// OpenForeignAccountFlow) and Android (ForeignCurrencyIntroContent.kt), the one
// remaining platform in that 3-product/3-platform matrix. Before this,
// ForeignCurrencyScreenView's "opening" an account was a bare row of "+ Open USD"
// -style buttons with zero explanation. Every fact below is real, sourced from
// backend ForeignCurrencyAccountService.kt's own doc comment/MARGIN_RATE constant --
// the 1.5% margin, the live mid-market rate, and the rate-alert feature are all real,
// already-shipped backend behavior, not invented copy.
private let currencyFullName = ["USD": "US Dollar", "EUR": "Euro", "GBP": "British Pound"]

struct ForeignCurrencyIntroContent: View {
    let availableCurrencies: [String]
    @Binding var selected: String?
    let submitting: Bool
    let onOpen: () -> Void

    var body: some View {
        FixedBottomCTA {
            VStack(alignment: .leading, spacing: 20) {
                Text("Hold and convert real foreign currency")
                    .font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("A separate account for each currency")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Keep USD, EUR, or GBP in its own account, completely separate from your RWF balance.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Convert at a real live rate")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Move money between RWF and your foreign currency anytime, at the real market rate plus itunda's transparent 1.5% margin -- no hidden fees.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Get notified at your rate")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Set a target rate once the account is open, and itunda tells you the moment the market crosses it -- convert when it's good for you, not just when you happen to check.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Choose a currency").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    HStack(spacing: 8) {
                        ForEach(availableCurrencies, id: \.self) { code in
                            let isSelected = selected == code
                            Button(action: { selected = code }) {
                                VStack(spacing: 2) {
                                    Text(code).font(.subheadline).bold()
                                    Text(currencyFullName[code] ?? code).font(.caption2)
                                }
                                .foregroundColor(isSelected ? .white : IDS.Colors.textPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(isSelected ? IDS.Colors.brand : IDS.Colors.backgroundPrimary)
                                .cornerRadius(10)
                            }
                        }
                    }
                }
            }
            .padding()
        } cta: {
            IdsButton(text: submitting ? "Opening…" : "Open account", isEnabled: selected != nil && !submitting, action: onOpen)
        }
    }
}
