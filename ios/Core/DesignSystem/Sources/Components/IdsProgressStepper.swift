import SwiftUI

// Real Toss TDS ProgressStepper component (tossmini-docs.toss.im/tds-mobile/components/
// progress-stepper) -- see docs/UI_UX_GUIDELINES.md's own product-feel research: shows
// "step 2 of 3"-style progress in a multi-step flow so the user has a visual sense of
// how many steps remain, not just what screen they're on. Already ported to bank-mfe
// (web's own ProgressStepper in BankDashboard.tsx) and Android
// (IdsProgressStepper.kt, same day) -- this is the iOS port, matching both exactly.
public struct IdsProgressStepper: View {
    let activeStepIndex: Int
    let steps: [String]

    public init(activeStepIndex: Int, steps: [String]) {
        self.activeStepIndex = activeStepIndex
        self.steps = steps
    }

    public var body: some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(Array(steps.enumerated()), id: \.offset) { i, label in
                VStack(spacing: 6) {
                    Circle()
                        .fill(i <= activeStepIndex ? IDS.Colors.brand : IDS.Colors.divider)
                        .frame(width: 8, height: 8)
                    Text(label)
                        .font(IDS.scaledFont(size: 11, weight: i == activeStepIndex ? .bold : .medium, relativeTo: .caption2))
                        .foregroundColor(
                            i == activeStepIndex ? IDS.Colors.brand :
                            i < activeStepIndex ? IDS.Colors.textSecondary : IDS.Colors.textTertiary
                        )
                        .lineLimit(1)
                }
                .fixedSize()
                if i < steps.count - 1 {
                    Rectangle()
                        .fill(i < activeStepIndex ? IDS.Colors.brand : IDS.Colors.divider)
                        .frame(height: 1)
                        .padding(.horizontal, 4)
                        .padding(.top, 4)
                }
            }
        }
        .padding(.top, 4)
        .padding(.bottom, 12)
    }
}
