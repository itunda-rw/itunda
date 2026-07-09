//
//  ZeroTrust.swift
//  Ported from mobile_clients/ios (2026-07-10) into its correct Tuist module --
//  see ARCHITECTURE.md §3. Device-integrity check gating native banking screens,
//  mirroring Toss's FDS pattern documented in docs/TOSS_ARCHITECTURE_FACTS.md.
//
//  NOT build-verified: this sandbox's Xcode toolchain (Tuist requires a Swift
//  runtime this environment doesn't have -- `libswiftSynchronization.dylib`
//  missing, OS older than Tuist's build target) can't run `tuist generate` or
//  compile this project. Marked `public` for correct cross-module visibility,
//  but honestly not confirmed to build -- unlike the Android port, which was.
//

import Foundation

public final class ZeroTrust {
    public static let shared = ZeroTrust()

    private init() {}

    public func verifyDeviceIntegrity() -> Bool {
        if isJailbroken() {
            return false
        }
        if isNetworkSecure() == false {
            return false
        }
        return true
    }

    private func isJailbroken() -> Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: "/Applications/Cydia.app") {
            return true
        }
        return false
        #endif
    }

    private func isNetworkSecure() -> Bool {
        return true
    }
}
