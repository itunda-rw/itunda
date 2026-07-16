import Foundation
import BrickModule
import GraniteBrownfield

/// Itunda's own real implementation of granite's generic, open-source
/// `GraniteBrownfieldModuleSpec` -- the real, open-source spec (installed at
/// `packages/saronite/node_modules/@granite-js/brownfield-module`) explicitly leaves this
/// unimplemented; the real native implementation is each host app's own, same as Toss's own
/// private implementation behind Apps in Toss. Direct iOS port of Android's
/// `GraniteBrownfieldModuleImpl.kt` (`android/brownfield-module-stub/`): same two methods,
/// same "closeView asks the host screen to dismiss itself" shape, same honest
/// `onVisibilityChanged` gap (not wired to a real lifecycle signal on either platform yet).
final class GraniteBrownfieldModuleImpl: BrickModuleBase, GraniteBrownfieldModuleSpec {
    private let scheme: String
    private let onCloseView: () -> Void

    init(scheme: String, onCloseView: @escaping () -> Void) {
        self.scheme = scheme
        self.onCloseView = onCloseView
        super.init(moduleName: "GraniteBrownfieldModule")
    }

    var schemeUri: String { scheme }

    func getSchemeUri() throws -> String { scheme }

    func closeView() async throws {
        await MainActor.run { onCloseView() }
    }
}
