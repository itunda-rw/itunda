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

    // Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13, 2026-08-26) --
    // porting the same pattern already proven on web/Android. Kept as internal state on
    // this same view (not a second .sheet) rather than chaining two sheet
    // presentations, which is fragile in SwiftUI when the trigger changes mid-
    // presentation -- matches this view's own existing "one sheet, internal step
    // state" shape.
    @State private var showingIntro = true

    @State private var name = ""
    @State private var dailyAmount = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        NavigationView {
            if showingIntro {
                Grow31IntroContent(onContinue: { showingIntro = false })
                    .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
                    .navigationTitle("31-day plan")
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
                    }
                    .padding()
                } cta: {
                    IdsButton(text: submitting ? "Working…" : "Start 31-day plan", isEnabled: !submitting, action: create)
                }
                .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
                .navigationTitle("New 31-day plan")
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
