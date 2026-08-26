import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13, 2026-08-26) --
// porting the same pattern already proven on web (BankDashboard.tsx's Grow31 'intro'
// step) and Android (Grow31IntroContent.kt), both built earlier the same session.
// Before this, tapping "+" on Grow31SavingsScreenView went straight into the creation
// form -- no explanation of the real bonus mechanics (base 1% + the streak-gated tier
// ladder, Grow31SavingsConstants.bonusRate) before the user committed.
struct Grow31IntroContent: View {
    let onContinue: () -> Void

    var body: some View {
        FixedBottomCTA {
            VStack(alignment: .leading, spacing: 20) {
                Text("Save a little every day, earn more the longer you keep it up")
                    .font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("One small deposit, every day, for \(Grow31SavingsConstants.termDays) days")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Pick a fixed amount you can realistically save every single day. A base 1% rate applies from day one.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("The longer your unbroken streak, the higher your bonus")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Your bonus rate is locked in by the longest unbroken run of daily deposits you reach:")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    VStack(spacing: 6) {
                        ForEach([3, 7, 14, 21, 31], id: \.self) { days in
                            HStack {
                                Text(days == Grow31SavingsConstants.termDays ? "\(days) days (full term)" : "\(days)-day streak")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Spacer()
                                Text("+\(Int(Grow31SavingsConstants.bonusRate(forStreak: days)))%")
                                    .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            }
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Miss a day? You keep what you already earned")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("A missed day resets your current streak, but the longest streak you already reached still locks in that bonus rate at maturity -- it isn't lost.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            .padding()
        } cta: {
            IdsButton(text: "Continue", action: onContinue)
        }
    }
}
