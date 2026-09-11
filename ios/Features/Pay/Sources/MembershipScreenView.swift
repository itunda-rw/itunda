import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "Membership" screen, adapted (itunda Pay redesign, 2026-08-28). The real
// Toss Pay reference aggregates points across many real THIRD-PARTY brands (Naver,
// Kakao, Coupang, CU, GS25...) -- itunda has no such partnerships, fabricating
// balances would violate this codebase's own standing honesty discipline.
// Confirmed with the user directly (AskUserQuestion): "Adapt Membership to
// itunda's own real points" -- real rewards points + real Pay Money balance + real
// per-merchant "Store points" (MerchantLoyaltyAccount, surfaced to a client for the
// first time this pass) only. See this file's own web sibling (MembershipView.tsx)
// for the full account.
struct MembershipScreenView: View {
    var onBack: () -> Void = {}
    var onOpenPayMoney: () -> Void = {}
    // Real "View all rewards" destination -- iOS has no native Rewards screen
    // either (RewardsService is otherwise reached only through the Saronite RN
    // mini-app). Injected as a plain callback (2026-09-02, Pay Feature-module
    // decomposition) rather than presented directly -- SaroniteRewardTasksView is
    // an :App-only Saronite bridge view, and this screen now lives in FeaturePay,
    // matching the exact onOpenRewards precedent FeatureAssets' own
    // OverviewScreenView already established (see its own doc comment) -- the
    // caller (ContentView.swift) owns the real showRewardTasksMiniApp state and
    // sheet presentation.
    var onOpenRewardsMiniApp: () -> Void = {}

    @State private var payBalance: Double?
    @State private var rewardsTotal: Double?
    @State private var rewardTasks: [RewardTaskDto] = []
    @State private var claimingRewardId: String?
    @State private var loyaltyBalances: [LoyaltyBalanceDto]?
    @State private var error: String?

    private var loading: Bool { payBalance == nil || rewardsTotal == nil || loyaltyBalances == nil }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Membership").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if loading {
                if let error {
                    Text(error).font(.caption).foregroundColor(.red).padding()
                } else {
                    ProgressView().padding(40)
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Linked memberships").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text("\(Int((rewardsTotal ?? 0) + (payBalance ?? 0))) RWF").font(.title).bold()
                            .padding(.bottom, 16)

                        Button(action: onOpenRewardsMiniApp) {
                            HStack {
                                Text("Available points").foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Text("\(Int(rewardsTotal ?? 0)) RWF").bold().foregroundColor(IDS.Colors.brand)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                        Divider()
                        Button(action: onOpenPayMoney) {
                            HStack {
                                Text("Pay Money").foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Text("\(Int(payBalance ?? 0)) RWF").bold().foregroundColor(IDS.Colors.brand)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)

                        if let loyaltyBalances, !loyaltyBalances.isEmpty {
                            Text("Store points").font(.subheadline).bold().padding(.top, 16).padding(.bottom, 8)
                            ForEach(loyaltyBalances) { balance in
                                HStack {
                                    Text(balance.merchantName).font(.subheadline)
                                    Spacer()
                                    Text("\(Int(balance.pointBalance))").font(.subheadline).bold()
                                }
                                .padding(.vertical, 6)
                            }
                        }

                        if let error {
                            Text(error).font(.caption).foregroundColor(.red).padding(.top, 8)
                        }
                        RewardsPreviewSection(tasks: rewardTasks, claimingId: claimingRewardId, onClaim: claimReward)
                            .padding(.top, 16)
                    }
                    .padding(.horizontal, 20)
                }
            }
            Spacer()
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do {
                let accounts = try await NetworkClient.shared.getAccounts().accounts
                payBalance = accounts.first(where: { $0.type == "PAY" })?.balance ?? 0
                let rewards = try await NetworkClient.shared.getRewardTasks()
                rewardsTotal = rewards.rewardsTotal
                rewardTasks = rewards.tasks
                loyaltyBalances = try await NetworkClient.shared.getMyLoyaltyBalances().balances
            } catch {
                self.error = "Could not load your membership details."
            }
        }
    }

    // Real "silence, not vagueness" bug found live (2026-09-11, same class this
    // codebase already fixed for résumé-remove/device-revoke) -- a failed claim
    // used to leave the row silently re-claimable with zero feedback. Matches
    // web's RewardsView.tsx's own real setError(...) handling.
    private func claimReward(_ taskId: String) {
        Task {
            claimingRewardId = taskId
            error = nil
            defer { claimingRewardId = nil }
            do {
                _ = try await NetworkClient.shared.claimRewardTask(taskId: taskId)
                let result = try await NetworkClient.shared.getRewardTasks()
                rewardTasks = result.tasks
                rewardsTotal = result.rewardsTotal
            } catch {
                self.error = "Couldn't claim this reward. Try again."
            }
        }
    }
}
