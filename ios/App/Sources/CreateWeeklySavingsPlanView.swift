import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-08-26): split out of WeeklySavingsScreenView.swift once that file
// grew past its file-size-lint baseline. The plan-creation sheet (+ its own
// escalation-rate option data), called from the main screen which stays behind --
// flipped from private (file-scoped in Swift too, at top level) to internal.

// MARK: - Create

private struct EscalationOption: Identifiable {
    let rate: Double
    let label: String
    var id: Double { rate }
}

private let escalationOptions: [EscalationOption] = [
    EscalationOption(rate: 0.00, label: "Flat"),
    EscalationOption(rate: 0.10, label: "+10%"),
    EscalationOption(rate: 0.20, label: "+20%"),
    EscalationOption(rate: 0.30, label: "+30%"),
    EscalationOption(rate: 0.50, label: "+50%"),
    EscalationOption(rate: 1.00, label: "+100%"),
]

struct CreateWeeklySavingsPlanView: View {
    let onCreated: () -> Void
    let onCancel: () -> Void

    // Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13, 2026-08-26) --
    // see CreateGrow31SavingsPlanView's identical shape for why this is internal state
    // on the same view rather than a second .sheet.
    @State private var showingIntro = true

    @State private var name = ""
    @State private var baseWeeklyAmount = ""
    @State private var escalationRate: Double = 0.10
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        NavigationView {
            if showingIntro {
                WeeklySavingsIntroContent(onContinue: { showingIntro = false })
                    .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
                    .navigationTitle("26-week plan")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel", action: onCancel)
                        }
                    }
            } else {
            FixedBottomCTA {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Plan name").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. New laptop", text: $name)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Base weekly amount (RWF)").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. 5000", text: $baseWeeklyAmount)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Escalation rate").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                            ForEach(escalationOptions) { option in
                                Button(action: { escalationRate = option.rate }) {
                                    Text(option.label)
                                        .font(.caption).bold()
                                        .foregroundColor(escalationRate == option.rate ? .white : IDS.Colors.textPrimary)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(escalationRate == option.rate ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                        .cornerRadius(10)
                                }
                            }
                        }
                    }

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                }
                .padding()
            } cta: {
                IdsButton(text: submitting ? "Working…" : "Start 26-week plan", isEnabled: !submitting, action: create)
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .navigationTitle("New 26-week plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Back", action: { showingIntro = true })
                }
            }
            }
        }
    }

    private func create() {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real plan name."
            return
        }
        guard let amount = Double(baseWeeklyAmount), amount > 0 else {
            error = "Enter a real weekly amount greater than zero."
            return
        }
        submitting = true
        Task {
            do {
                _ = try await NetworkClient.shared.createWeeklySavingsPlan(name: name, baseWeeklyAmount: amount, escalationRate: escalationRate)
                ToastCenter.shared.show("26-week plan started.")
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
        case 400: return "That amount or escalation rate isn't valid."
        case 429: return "Too many plans created recently -- try again in a bit."
        default: return "Could not start this plan. Please try again."
        }
    }
}
