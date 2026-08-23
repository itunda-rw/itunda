package rw.itunda.core.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

private const val KEYSTORE_ALIAS = "itunda_device_verify_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/**
 * Real hardware-backed device-verification key (item 246) -- generates a P-256 key pair
 * inside Android Keystore (TEE/StrongBox-backed where the device supports it, never
 * exportable) gated by biometric auth on every use, not just at creation. Pairs with the
 * backend's DeviceService.registerDeviceKey/issueChallenge/verifyDeviceBySignature (see
 * AuthController.kt) -- this is the client half of that same protocol: once a user opts
 * in once (a one-time password-gated registration, same cost as the existing
 * verifyDevice), every FUTURE device step-up is a biometric prompt instead of retyping a
 * password, matching Toss's own 토스인증서 model this closes
 * (docs/DESIGN_REFERENCES.md Section 12 recommendation #2).
 */
class DeviceKeyManager {

    fun hasKey(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.containsAlias(KEYSTORE_ALIAS)
    }

    /** Generates the key pair and returns the raw uncompressed P-256 point (0x04 || X ||
     * Y, 65 bytes), base64-encoded -- the exact wire format
     * DeviceService.parsePublicKey expects server-side, so neither client needs a
     * DER/X.509 conversion step. */
    fun generateKeyPair(): String {
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // Required on every signature, not just once at creation -- so a stolen,
            // unlocked phone still can't sign a device-verification challenge without
            // the real owner's fingerprint/face.
            .setUserAuthenticationRequired(true)
            .build()
        keyPairGenerator.initialize(spec)
        val keyPair = keyPairGenerator.generateKeyPair()
        return encodePublicKey(keyPair.public as ECPublicKey)
    }

    private fun encodePublicKey(publicKey: ECPublicKey): String {
        val x = toFixedLength(publicKey.w.affineX.toByteArray(), 32)
        val y = toFixedLength(publicKey.w.affineY.toByteArray(), 32)
        val raw = ByteArray(65)
        raw[0] = 0x04
        System.arraycopy(x, 0, raw, 1, 32)
        System.arraycopy(y, 0, raw, 33, 32)
        return Base64.getEncoder().encodeToString(raw)
    }

    /** BigInteger.toByteArray() can return more than 32 bytes (a leading zero sign byte
     * on a value whose high bit is set) or fewer (leading zeros stripped) -- both need
     * normalizing to a fixed-width, unsigned big-endian field element before this
     * matches what the backend's parsePublicKey expects. */
    private fun toFixedLength(bytes: ByteArray, length: Int): ByteArray {
        if (bytes.size == length) return bytes
        val result = ByteArray(length)
        if (bytes.size > length) {
            System.arraycopy(bytes, bytes.size - length, result, 0, length)
        } else {
            System.arraycopy(bytes, 0, result, length - bytes.size, bytes.size)
        }
        return result
    }

    /** Signs `challenge` (the raw decoded bytes of the base64 challenge issued by
     * /devices/challenge) using the Keystore key, behind a real biometric prompt bound
     * to it via [BiometricPrompt.CryptoObject] -- the OS itself refuses to unlock the
     * key without a successful auth, this is not a client-side-only check. Returns the
     * DER-encoded signature, base64, matching what
     * DeviceService.verifyDeviceBySignature verifies with "SHA256withECDSA". */
    fun signChallenge(
        activity: FragmentActivity,
        challenge: ByteArray,
        reason: String,
        onResult: (signatureBase64: String?, error: String?) -> Unit,
    ) {
        if (!hasKey()) {
            onResult(null, "No device key registered.")
            return
        }
        val biometricManager = BiometricManager.from(activity)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(null, "Biometrics unavailable on this device.")
            return
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? PrivateKey
        if (privateKey == null) {
            onResult(null, "No device key registered.")
            return
        }
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
        val cryptoObject = BiometricPrompt.CryptoObject(signature)

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val signedWith = result.cryptoObject?.signature
                    if (signedWith == null) {
                        onResult(null, "Signing failed.")
                        return
                    }
                    signedWith.update(challenge)
                    onResult(Base64.getEncoder().encodeToString(signedWith.sign()), null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(null, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // A single unrecognized fingerprint/face -- the system prompt stays
                    // open for a retry, so this is not terminal.
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify this device")
            .setSubtitle(reason)
            .setNegativeButtonText("Use password instead")
            .build()

        prompt.authenticate(promptInfo, cryptoObject)
    }

    /** Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
     * AuthDtos.RegisterRequest/LoginRequest's own devicePublicKey doc comment. Returns
     * the SAME already-generated key's public half so register()/login() can publish
     * it alongside a normal password submission (auto-enrolling this device for
     * biometric-only login next time, no separate Settings-toggle trip required) --
     * reads it back from the Keystore's self-signed certificate rather than
     * generating a fresh key, since [generateKeyPair] must only ever run once per
     * device (a second call would silently replace the first key, invalidating
     * anything the server already has on file for it). Reading a certificate's public
     * key needs no biometric gate, unlike [signChallenge]. */
    fun exportPublicKeyIfPresent(): String? {
        if (!hasKey()) return null
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val publicKey = keyStore.getCertificate(KEYSTORE_ALIAS)?.publicKey as? ECPublicKey ?: return null
        return encodePublicKey(publicKey)
    }

    /** Real "forget this device"'s client-side counterpart -- called after
     * DeviceService.revokeDevice so a stale local key doesn't outlive its server record. */
    fun removeKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) keyStore.deleteEntry(KEYSTORE_ALIAS)
    }
}
