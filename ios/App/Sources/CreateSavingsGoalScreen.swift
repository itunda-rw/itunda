import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real named-goal creation (2026-08-22, product-feel/Toss-parity work) -- iOS could
// view and deposit into existing SavingsGoals (SavingsFlowContainer.swift) but had no
// way to ever create one, a real standing gap vs Android's
// MainViewModel.createSavingsGoal and web's own CreateGoalForm (BankDashboard.tsx),
// both real and already-shipped. Self-contained single-screen form, matching
// CreateWeeklySavingsPlanView's own established pattern (WeeklySavingsScreenView.swift)
// rather than web's newer multi-step ProgressStepper wizard.
//
// FixedBottomCTA applied 2026-08-26 (roadmap item 10, now closed -- see
// FixedBottomCTA's own doc comment in Components.swift): the "Create goal" button
// used to sit inline at the end of the scrolling form, invisible until scrolled all
// the way down. Now pinned below the scroll area, matching web's FullScreenFlow.
struct CreateSavingsGoalScreen: View {
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var name = ""
    @State private var targetAmount = ""
    @State private var addMonthlyContribution = false
    @State private var monthlyContribution = ""
    @State private var addTargetDate = false
    @State private var targetDate = Date()
    @State private var error: String?
    @State private var submitting = false

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    var body: some View {
        NavigationView {
            FixedBottomCTA {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("What are you saving for?").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. Emergency Fund", text: $name)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Target amount (RWF)").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. 500000", text: $targetAmount)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    Toggle("Add a monthly auto-save target", isOn: $addMonthlyContribution.animation())
                        .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                    if addMonthlyContribution {
                        TextField("e.g. 20000", text: $monthlyContribution)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    Toggle("Add a target date", isOn: $addTargetDate.animation())
                        .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                    if addTargetDate {
                        DatePicker("Target date", selection: $targetDate, displayedComponents: .date)
                            .datePickerStyle(.compact)
                            .labelsHidden()
                    }

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                }
                .padding()
            } cta: {
                IdsButton(text: submitting ? "Creating…" : "Create goal", isEnabled: !submitting, action: create)
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .navigationTitle("New savings goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
            }
        }
    }

    private func create() {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real goal name."
            return
        }
        guard let amount = Double(targetAmount), amount > 0 else {
            error = "Enter a real target amount greater than zero."
            return
        }
        let contribution: Double? = addMonthlyContribution ? Double(monthlyContribution) : nil
        if addMonthlyContribution && contribution == nil {
            error = "Enter a real monthly amount, or turn off auto-save."
            return
        }
        submitting = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.createSavingsGoal(
                    name: name.trimmingCharacters(in: .whitespaces),
                    targetAmount: amount,
                    monthlyContribution: contribution,
                    targetDate: addTargetDate ? Self.dateFormatter.string(from: targetDate) : nil
                )
                ToastCenter.shared.show("Savings goal created.")
                onCreated()
            } catch let NetworkError.httpError(statusCode) {
                error = Self.errorMessage(statusCode)
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            submitting = false
        }
    }

    private static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 400: return "That name or amount isn't valid."
        case 429: return "Too many goals created recently -- try again in a bit."
        default: return "Could not create this goal. Please try again."
        }
    }
}
