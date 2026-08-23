import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kakao Pay 머니굴리기 ("rolling money") round-up auto-saving (rw.itunda.savings.
// RoundUpService, real since well before this session) -- first iOS client for this
// feature (item 113; bank-mfe item 112, Android already had it). Same
// no-ViewModel, "call NetworkClient.shared directly from Task {} blocks" convention as
// YouthAccountScreenView.swift/SpendingScreenView.swift. Honest v1 scope, matching
// Android/bank-mfe exactly: goal destination only, not the newer stock-destination
// option.
struct RoundUpSettingsView: View {
    var onBack: () -> Void = {}
    @State private var settings: RoundUpSettingsDto?
    @State private var loaded = false
    @State private var goals: [SavingsGoal] = []
    @State private var increment: Double = 100
    @State private var goalId = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Round-up savings").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if !loaded {
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    } else if let current = settings, current.enabled {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Every transfer rounds up to the nearest \(Int(current.roundToNearest)) RWF, saved into your goal.")
                                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                            Button(action: { Task { await toggle(enabled: false) } }) {
                                Text(busy ? "…" : "Turn off").bold()
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(busy)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    } else {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Round up every transfer to a real RWF increment and auto-save the spare change.")
                                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                            HStack(spacing: 6) {
                                ForEach(ROUND_UP_INCREMENTS, id: \.self) { value in
                                    Button(action: { increment = value }) {
                                        Text("\(Int(value)) RWF").font(.caption).bold()
                                            .foregroundColor(increment == value ? .white : IDS.Colors.textPrimary)
                                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                                            .background(increment == value ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                                            .cornerRadius(8)
                                    }
                                }
                            }
                            if goals.isEmpty {
                                Text("Create a savings goal first to enable round-up.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                Picker("Savings goal", selection: $goalId) {
                                    Text("Choose a savings goal").tag("")
                                    ForEach(goals, id: \.id) { goal in
                                        Text(goal.name).tag(goal.id)
                                    }
                                }
                                .pickerStyle(.menu)
                            }
                            Button(action: { Task { await toggle(enabled: true) } }) {
                                Text(busy ? "Turning on…" : "Turn on round-up").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || goalId.isEmpty)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        async let settingsResult = try? NetworkClient.shared.getRoundUpSettings().settings
        async let goalsResult = try? NetworkClient.shared.getSavingsGoals().goals
        settings = await settingsResult ?? nil
        goals = await goalsResult ?? []
        if let current = settings {
            increment = current.roundToNearest
            goalId = current.targetGoalId ?? ""
        }
        loaded = true
    }

    private func toggle(enabled: Bool) async {
        if enabled && goalId.isEmpty {
            error = "Choose a savings goal first."
            return
        }
        busy = true
        error = nil
        do {
            let target = enabled ? goalId : settings?.targetGoalId
            settings = try await NetworkClient.shared.setRoundUpSettings(enabled: enabled, roundToNearest: increment, targetGoalId: target).settings
        } catch {
            self.error = "Could not update round-up settings."
        }
        busy = false
    }
}
