import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-08-26): split out of Grow31SavingsScreenView.swift once that file
// grew past its file-size-lint baseline. The plan-creation sheet, called from the
// main screen which stays behind -- flipped from private (file-scoped in Swift
// too, at top level) to internal.

// MARK: - Create

struct CreateGrow31SavingsPlanView: View {
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var name = ""
    @State private var dailyAmount = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Pick a small amount you can realistically save every single day for \(Grow31SavingsConstants.termDays) days. Miss a day and your streak resets -- but your longest streak still locks in a bonus rate at maturity, up to +10% for a full unbroken run.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Plan name").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. New laptop", text: $name)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Daily amount (RWF)").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. 500", text: $dailyAmount)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    Button(action: create) {
                        Text(submitting ? "Working…" : "Start 31-day plan")
                            .foregroundColor(.white).bold()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(IDS.Colors.brand)
                            .cornerRadius(12)
                    }
                    .disabled(submitting)
                }
                .padding()
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .navigationTitle("New 31-day plan")
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
            error = "Enter a real plan name."
            return
        }
        guard let amount = Double(dailyAmount), amount > 0 else {
            error = "Enter a real daily amount greater than zero."
            return
        }
        submitting = true
        Task {
            do {
                _ = try await NetworkClient.shared.createGrow31SavingsPlan(name: name, dailyAmount: amount)
                ToastCenter.shared.show("31-day plan started.")
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
        case 400: return "That amount isn't valid."
        case 429: return "Too many plans created recently -- try again in a bit."
        default: return "Could not start this plan. Please try again."
        }
    }
}
