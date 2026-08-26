import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13, 2026-08-26) --
// porting the same pattern already proven on web/Android, both built earlier the same
// session. Real mechanics sourced from WeeklySavingsConstants/WeeklySavingsService.kt:
// unlike Grow31's partial-credit longest-streak bonus, this one is all-or-nothing --
// one missed installment or any early withdrawal forfeits the bonus permanently.
// Stated explicitly rather than reusing Grow31's softer framing.
struct WeeklySavingsIntroContent: View {
    let onContinue: () -> Void

    var body: some View {
        FixedBottomCTA {
            VStack(alignment: .leading, spacing: 20) {
                Text("A weekly habit that grows on its own")
                    .font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("A real \(WeeklySavingsConstants.termWeeks)-week term deposit")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Like KakaoBank's 26주적금: your weekly amount auto-debits from your main account every week for \(WeeklySavingsConstants.termWeeks) weeks -- nothing to top up manually.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Base 5% + a 3% bonus for staying unbroken")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("The 3% bonus is all-or-nothing: miss even one week's installment, or withdraw early, and the bonus is forfeited for good -- unlike a 31-day plan's partial-credit streak, this one doesn't have a middle ground.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Your weekly amount can step up automatically")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Choose an escalation rate and your weekly amount compounds up every \(WeeklySavingsConstants.escalationStepWeeks) weeks -- start small and build up, instead of committing to one fixed amount for all \(WeeklySavingsConstants.termWeeks) weeks.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            .padding()
        } cta: {
            IdsButton(text: "Continue", action: onContinue)
        }
    }
}
