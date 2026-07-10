import SwiftUI
import CoreRisk

@main
struct ItundaApp: App {
    // Root/FDS gate on the real app entry point, mirroring Android's
    // MainActivity.onCreate check (see docs/ARCHITECTURE.md §3) --
    // money-moving screens should not render on a compromised device.
    private let isDeviceTrusted = ZeroTrust.shared.verifyDeviceIntegrity()

    var body: some Scene {
        WindowGroup {
            if isDeviceTrusted {
                ContentView()
            } else {
                Text("Itunda can't run on a jailbroken or compromised device.")
                    .multilineTextAlignment(.center)
                    .padding()
            }
        }
    }
}
