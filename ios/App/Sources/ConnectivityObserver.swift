import Foundation
import Network

/// Real, proactive connectivity signal via NWPathMonitor -- fires the moment a real
/// satisfied network path appears, unlike BankViewModel's pre-existing `isOffline`
/// flag, which is purely reactive (only ever set inside load()'s URLError catch,
/// after a request has already failed). Used to trigger a real replay of the offline
/// action queue the instant connectivity actually returns. Mirrors Android's
/// ConnectivityObserver.kt (ConnectivityManager.NetworkCallback); nothing like this
/// existed anywhere in ios/ before -- no NWPathMonitor usage, no Reachability
/// library, confirmed by a repo-wide search.
final class ConnectivityObserver {
    private var monitor: NWPathMonitor?
    private let queue = DispatchQueue(label: "rw.itunda.app.connectivity")

    func start(onAvailable: @escaping () -> Void) {
        guard monitor == nil else { return }
        let monitor = NWPathMonitor()
        self.monitor = monitor
        monitor.pathUpdateHandler = { path in
            if path.status == .satisfied {
                onAvailable()
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor?.cancel()
        monitor = nil
    }
}
