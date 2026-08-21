import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
// "our pay home screen should also look 100% like toss pay home screen") -- see
// PayScreen's own doc comment (ContentView.swift). RewardsPreviewSection replaces the
// reference's "Get more rewards" list (Toss Prime / Google gift codes / etc, none of
// which itunda has a real backend for) with itunda's own already-real RewardsService
// task list (getRewardTasks/claimRewardTask, NetworkClient.swift), never surfaced on
// iOS's Pay tab before. No "View all" link to a dedicated Rewards screen -- iOS has no
// native one (RewardsService is otherwise reached only through the Saronite RN
// mini-app bridge) -- each row is directly tap-to-claim instead, matching Android's
// identical fix the same session.
struct RewardsPreviewSection: View {
    let tasks: [RewardTaskDto]
    let claimingId: String?
    let onClaim: (String) -> Void

    private var preview: [RewardTaskDto] {
        Array(tasks.filter { !$0.claimed && $0.eligible }.prefix(3))
    }

    var body: some View {
        if !preview.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("Get more rewards")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(IDS.Colors.textPrimary)
                ForEach(preview) { task in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title).font(.system(size: 14, weight: .semibold)).foregroundColor(IDS.Colors.textPrimary)
                            Text(task.subtitle).font(.system(size: 12)).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { onClaim(task.id) }) {
                            Text(claimingId == task.id ? "…" : "+\(Int(task.rewardAmount)) RWF")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(IDS.Colors.brand)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(IDS.Colors.brand.opacity(0.12))
                                .cornerRadius(999)
                        }
                        .disabled(claimingId != nil)
                    }
                    .padding(.vertical, 10)
                }
            }
        }
    }
}

// A bold "Pay" wordmark plus a settings icon (routes to the You tab, tag 4 -- itunda
// has no dedicated Pay-settings screen, so this is an honest substitute), the real
// RewardsPreviewSection above, and a "Get help" row routing to the real
// SupportScreenView (itunda has no FAQ-content system, so this is an honest substitute
// for the reference's FAQ/feedback links) -- rather than fabricating Toss-specific rows
// ("Toss Prime", "Google gift codes", cross-merchant coupon wallet, external
// online-merchant integrations) itunda has no real backend for.
struct PayScreen: View {
    @State private var paymentResult: CollectPaymentResultDto?
    @State private var rewardTasks: [RewardTaskDto] = []
    @State private var claimingRewardId: String?
    @State private var showSupport = false
    var onSwitchToYou: () -> Void = {}

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack {
                    Text("Pay")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button(action: onSwitchToYou) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                    .accessibilityLabel("Pay settings")
                }
                .padding(.horizontal, 20)
                PayAMerchantSection(paymentResult: $paymentResult)
                    .padding(.horizontal, 20)
                RewardsPreviewSection(tasks: rewardTasks, claimingId: claimingRewardId, onClaim: claimReward)
                    .padding(.horizontal, 20)
                Button(action: { showSupport = true }) {
                    Text("Get help")
                        .font(.system(size: 13))
                        .foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 24)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
        .task { rewardTasks = (try? await NetworkClient.shared.getRewardTasks())?.tasks ?? [] }
        .sheet(isPresented: $showSupport) {
            SupportScreenView(onBack: { showSupport = false })
        }
    }

    private func claimReward(_ taskId: String) {
        Task {
            claimingRewardId = taskId
            defer { claimingRewardId = nil }
            _ = try? await NetworkClient.shared.claimRewardTask(taskId: taskId)
            rewardTasks = (try? await NetworkClient.shared.getRewardTasks())?.tasks ?? []
        }
    }
}
