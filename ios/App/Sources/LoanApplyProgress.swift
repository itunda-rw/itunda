import SwiftUI
import CoreDesignSystem

// Real Toss decision framework, applied directly (2026-08-29, toss.tech/article/
// interaction's own real, sourced principle: prioritize high-abandonment/trust-
// building moments over decorative flourishes -- their own named example is a loan
// ASSESSMENT loading screen, changed from a static placeholder to real-time content
// that incrementally builds confidence while a lending decision is made). itunda's
// own real loan-apply moment had ZERO acknowledgment before this -- not even a
// spinner, just an inline "Applying…" button label (found via a real audit fork
// this session; matches web/Android's identical fix same session). Steps below are
// the REAL gates LoansService.applyForLoan actually runs in order (credit-score
// check, account-type check, ledger disbursement) -- not invented filler copy.
//
// Extracted into its own file (2026-08-29) rather than added to
// OverviewLoansCreditScoreScreens.swift directly -- that file was already at its
// file-size-lint baseline (1030 lines) and this addition would have pushed it past.
let loanApplySteps = ["Checking your credit score", "Confirming loan terms", "Disbursing your funds"]

struct LoanApplyProgress: View {
    @State private var stepIndex = 0

    var body: some View {
        VStack(spacing: 14) {
            ProgressView().tint(IDS.Colors.brand)
            Text(loanApplySteps[stepIndex])
                .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                .id(stepIndex)
                .transition(.opacity)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .onAppear {
            for i in 1..<loanApplySteps.count {
                DispatchQueue.main.asyncAfter(deadline: .now() + Double(i) * 0.9) {
                    withAnimation(.easeInOut(duration: 0.25)) { stepIndex = i }
                }
            }
        }
    }
}
