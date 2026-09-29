package rw.itunda.partners

import org.springframework.stereotype.Component
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * itunda's OWN system-level signing key -- distinct from `CertificateService`'s
 * per-user Ed25519 keys (a user's own document-signing identity, whose private key the
 * backend deliberately never stores). This key signs identity-verification payloads
 * itunda hands to a PARTNER, the same real non-repudiation role Toss's own tosscert
 * "본인확인" response signature plays (toss.im/tosscert/docs/guides/integration/user):
 * proof the disclosed data really came from itunda at this moment, not a forged
 * response.
 *
 * Honest scope boundary, same discipline as `DemoNidaVerificationService`/
 * `DemoKybVerificationService`: this key is generated once in memory at process
 * startup, not persisted or rotated -- a real production system would hold this in a
 * real secrets manager / HSM and rotate it, publishing each generation's public key so
 * old signatures stay verifiable. `publicKeyBase64()` is exposed via a real endpoint so
 * a partner CAN independently verify a signature during this process's lifetime, which
 * is the real, functional part of this feature; only the "survives a restart, gets
 * rotated safely" part is the acknowledged demo-mode gap.
 */
@Component
class IdentitySigningKeyProvider {
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    fun sign(payload: String): String {
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey())
        signature.update(payload.toByteArray())
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    fun verify(payload: String, signatureBase64: String): Boolean {
        val signature = Signature.getInstance("Ed25519")
        signature.initVerify(keyPair.public)
        signature.update(payload.toByteArray())
        return signature.verify(Base64.getDecoder().decode(signatureBase64))
    }

    fun publicKeyBase64(): String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun privateKey(): PrivateKey = keyPair.private
}
