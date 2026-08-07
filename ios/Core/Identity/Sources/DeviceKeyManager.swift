//
//  DeviceKeyManager.swift
//  Real Secure-Enclave-signed-challenge device verification (item 246) -- mirrors
//  Android's DeviceKeyManager.kt exactly: same wire format (raw uncompressed P-256
//  point, 0x04 || X || Y, 65 bytes, base64) and the same DER-encoded
//  "SHA256withECDSA"-compatible signature (.ecdsaSignatureMessageX962SHA256 produces
//  exactly that on iOS), so the backend's DeviceService.parsePublicKey/
//  verifyDeviceBySignature needs no per-platform branching. The private key never
//  leaves the Secure Enclave and requires Face ID/Touch ID on every use
//  (.biometryCurrentSet), not just at creation -- pairs with
//  DeviceService.registerDeviceKey/issueChallenge/verifyDeviceBySignature on the
//  backend (see AuthController.kt's own doc comments there).
//
//  NOT build-verified via xcodebuild -- see ZeroTrust.swift for why (Tuist can't run
//  in this sandbox's toolchain). Verified via `swiftc -parse`.
//

import Foundation
import Security
import LocalAuthentication

public enum DeviceKeyError: Error {
    case accessControlFailed
    case keyGenerationFailed
    case noKeyRegistered
    case unsupportedAlgorithm
    case signingFailed
}

public final class DeviceKeyManager {
    public static let shared = DeviceKeyManager()

    private let tag = "rw.itunda.device-verify-key".data(using: .utf8)!

    private init() {}

    public func hasKey() -> Bool {
        privateKey() != nil
    }

    /// Generates the key pair in the Secure Enclave and returns the raw uncompressed
    /// P-256 point, base64-encoded. SecKeyCopyExternalRepresentation already returns
    /// exactly this format for an EC key -- unlike Android Keystore's ECPublicKey.w,
    /// which needs manual BigInteger-to-fixed-width-bytes encoding (see
    /// DeviceKeyManager.kt's own toFixedLength/encodePublicKey) -- so no conversion is
    /// needed on this side either.
    public func generateKeyPair() throws -> String {
        // A stale key from a previous enable/disable cycle would otherwise collide on
        // the same applicationTag.
        removeKey()

        var accessControlError: Unmanaged<CFError>?
        guard let accessControl = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            [.privateKeyUsage, .biometryCurrentSet],
            &accessControlError
        ) else {
            throw (accessControlError?.takeRetainedValue() as Error?) ?? DeviceKeyError.accessControlFailed
        }

        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tag,
                kSecAttrAccessControl as String: accessControl,
            ],
        ]

        var createError: Unmanaged<CFError>?
        guard let generatedPrivateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &createError) else {
            throw (createError?.takeRetainedValue() as Error?) ?? DeviceKeyError.keyGenerationFailed
        }
        guard let publicKey = SecKeyCopyPublicKey(generatedPrivateKey) else {
            throw DeviceKeyError.keyGenerationFailed
        }
        var exportError: Unmanaged<CFError>?
        guard let rawPoint = SecKeyCopyExternalRepresentation(publicKey, &exportError) as Data? else {
            throw (exportError?.takeRetainedValue() as Error?) ?? DeviceKeyError.keyGenerationFailed
        }
        return rawPoint.base64EncodedString()
    }

    /// Signs `challenge` (the raw decoded bytes of the base64 challenge issued by
    /// /devices/challenge) behind a Face ID/Touch ID prompt -- the LAContext attached
    /// via kSecUseAuthenticationContext when fetching the key reference is what the
    /// Secure Enclave actually prompts with; the ensuing SecKeyCreateSignature call
    /// blocks on that same prompt, so this runs off the main thread.
    public func signChallenge(_ challenge: Data, reason: String, completion: @escaping (String?, Error?) -> Void) {
        let context = LAContext()
        context.localizedReason = reason

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self, let key = self.privateKey(context: context) else {
                DispatchQueue.main.async { completion(nil, DeviceKeyError.noKeyRegistered) }
                return
            }
            guard SecKeyIsAlgorithmSupported(key, .sign, .ecdsaSignatureMessageX962SHA256) else {
                DispatchQueue.main.async { completion(nil, DeviceKeyError.unsupportedAlgorithm) }
                return
            }
            var signError: Unmanaged<CFError>?
            guard let signature = SecKeyCreateSignature(
                key,
                .ecdsaSignatureMessageX962SHA256,
                challenge as CFData,
                &signError
            ) as Data? else {
                DispatchQueue.main.async { completion(nil, (signError?.takeRetainedValue() as Error?) ?? DeviceKeyError.signingFailed) }
                return
            }
            DispatchQueue.main.async { completion(signature.base64EncodedString(), nil) }
        }
    }

    /// Real "forget this device"'s client-side counterpart -- called after
    /// DeviceService.revokeDevice so a stale local key doesn't outlive its server record.
    public func removeKey() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tag,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
        ]
        SecItemDelete(query as CFDictionary)
    }

    private func privateKey(context: LAContext? = nil) -> SecKey? {
        var query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tag,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true,
        ]
        if let context {
            query[kSecUseAuthenticationContext as String] = context
        }
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return (item as! SecKey)
    }
}
