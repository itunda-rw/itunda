import Foundation
import CoreNetwork

/// A real action queued locally while the device couldn't reach itunda's backend --
/// see docs/TOSS_PARITY_MATRIX.md's Offline row (backend half: real
/// POST /api/v1/actions/batch, mirrors Android's OfflineActionQueue.kt).
struct PendingSavingsDeposit: Codable {
    let clientActionId: String
    let idempotencyKey: String
    let goalId: String
    let amount: Double
    let createdAt: Date
}

/// Real local queue for actions attempted while offline. Backed by plain
/// UserDefaults (not Keychain like KeychainTokenStore.swift -- this holds queued
/// savings-deposit intents, not credentials), storing a JSON array via Codable.
/// Deliberately not Core Data/SwiftData: this repo has no persistence framework
/// anywhere in ios/ today, and a single small queue doesn't justify introducing one
/// -- same reasoning Android's OfflineActionQueue.kt documents for skipping Room.
/// Scoped to SAVINGS_DEPOSIT only (a concrete Codable struct, not a generic
/// [String: Any] body), matching exactly what's wired into the UI today -- Android's
/// queue is generically typed because bills/airtime action types exist there too,
/// but nothing on iOS calls bill-pay or airtime yet, so a generic body would be
/// unused complexity here.
final class OfflineActionQueue {
    static let shared = OfflineActionQueue()

    private let storageKey = "itunda_offline_queue_savings_deposits"
    private let defaults = UserDefaults.standard

    private init() {}

    @discardableResult
    func enqueueSavingsDeposit(goalId: String, amount: Double) -> PendingSavingsDeposit {
        let action = PendingSavingsDeposit(
            clientActionId: "local_\(UUID().uuidString)",
            idempotencyKey: UUID().uuidString,
            goalId: goalId,
            amount: amount,
            createdAt: Date()
        )
        var current = peekAll()
        current.append(action)
        persist(current)
        return action
    }

    func peekAll() -> [PendingSavingsDeposit] {
        guard let data = defaults.data(forKey: storageKey) else { return [] }
        return (try? JSONDecoder().decode([PendingSavingsDeposit].self, from: data)) ?? []
    }

    func removeByClientActionIds(_ ids: Set<String>) {
        guard !ids.isEmpty else { return }
        persist(peekAll().filter { !ids.contains($0.clientActionId) })
    }

    private func persist(_ actions: [PendingSavingsDeposit]) {
        if let data = try? JSONEncoder().encode(actions) {
            defaults.set(data, forKey: storageKey)
        }
    }
}
