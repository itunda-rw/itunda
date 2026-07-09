//
//  NIDABiometricAuth.swift
//  Itunda
//
//  Security Layer - Rwanda NIDA Integration
//  Implements Toss-style Face Pay and Biometric Auth but mapped to Rwanda's National ID (NIDA) Database
//

import Foundation
import LocalAuthentication

class NIDABiometricAuth {
    static let shared = NIDABiometricAuth()
    
    // Toss uses their FDS (Fraud Detection System), we adapt this to NIDA + local context
    func authenticateUser(nidNumber: String, completion: @escaping (Bool, Error?) -> Void) {
        let context = LAContext()
        var error: NSError?
        
        // 1. Verify Local Biometrics (Secure Enclave)
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Log in securely using Face ID / Touch ID") { success, evalError in
                if success {
                    // 2. Local biometric passed. Now, perform NIDA Biometric/Selfie match (Mocked for architecture)
                    self.verifyWithNIDADatabase(nidNumber: nidNumber) { nidaSuccess in
                        completion(nidaSuccess, nil)
                    }
                } else {
                    completion(false, evalError)
                }
            }
        } else {
            // Fallback to PIN / NIDA SMS OTP for USSD fallback users
            completion(false, error)
        }
    }
    
    private func verifyWithNIDADatabase(nidNumber: String, completion: @escaping (Bool) -> Void) {
        // In a real implementation, this would securely send a Face match token to the Itunda Backend,
        // which then securely communicates with the Rwandan NIDA API to verify the identity.
        // Toss does this for seamless onboarding. We apply it to NIDA.
        print("Verifying biometrics via NIDA API for NID: \(nidNumber)...")
        // Simulated network delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            completion(true) // Mock success
        }
    }
}
