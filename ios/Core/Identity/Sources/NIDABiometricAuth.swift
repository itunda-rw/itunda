//
//  NIDABiometricAuth.swift
//  Ported from mobile_clients/ios (2026-07-10) into its correct Tuist module --
//  see ARCHITECTURE.md §3. Local biometric gate intended to precede a National
//  ID (NIDA) server-side verification call, which is not implemented here (see
//  docs/TOSS_PARITY_MATRIX.md's open KYC/AML gate) -- honestly labeled below
//  rather than presented as a finished identity flow.
//
//  NOT build-verified -- see ZeroTrust.swift for why (Tuist can't run in this
//  sandbox's toolchain).
//

import Foundation
import LocalAuthentication

public final class NIDABiometricAuth {
    public static let shared = NIDABiometricAuth()

    private init() {}

    public func authenticateUser(nidNumber: String, completion: @escaping (Bool, Error?) -> Void) {
        evaluate(reason: "Log in securely using Face ID / Touch ID") { success, evalError in
            if success {
                // Not implemented: the actual NIDA server-side selfie/ID match.
                // Local biometric success only, honestly labeled.
                completion(true, nil)
            } else {
                completion(false, evalError)
            }
        }
    }

    /// Generic transaction-confirm gate -- e.g. before a transfer/payment is sent.
    /// Mirrors NIDABiometricAuth.kt's `authenticateForTransaction` (2026-07-11).
    public func authenticateForTransaction(reason: String, completion: @escaping (Bool, Error?) -> Void) {
        evaluate(reason: reason, completion: completion)
    }

    private func evaluate(reason: String, completion: @escaping (Bool, Error?) -> Void) {
        let context = LAContext()
        var error: NSError?

        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, evalError in
                completion(success, evalError)
            }
        } else {
            completion(false, error)
        }
    }
}
