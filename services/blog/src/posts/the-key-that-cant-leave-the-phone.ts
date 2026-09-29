export const theKeyThatCantLeaveThePhone = {
  slug: 'the-key-that-cant-leave-the-phone',
  title: "Building our own 토스인증서: a device-verification key that never leaves the phone",
  date: '2026-08-07',
  author: 'Security Team',
  tags: ['security', 'android', 'ios', 'backend', 'cryptography'],
  excerpt:
    "We'd already written down that replacing password re-entry with a real Keystore-signed challenge was the right next step, and explicitly not a quick one. This is the account of actually building it — three platforms, one wire format, and a design call that mattered more than any line of code: what a stolen login token is and isn't allowed to do on its own.",
  content: `
A few weeks back we audited our own device-verification flow against Toss's real security architecture and found a gap we deliberately didn't shortcut: our step-up dialog asks for a password on a new device, when Toss's own 토스인증서 (Toss Certificate) system uses a real signed challenge instead. We wrote at the time that wiring a local biometric success straight into "mark this device trusted" would be faster to ship and strictly worse — a fingerprint that unlocks the phone proves nothing to the server unless there's a real private key behind it, signing something the server can check. We sized it as its own project and moved on. This is that project.

## The design decision that mattered more than the crypto

The cryptography itself is standard: ECDSA over P-256, the same curve both Android Keystore and iOS Secure Enclave support natively, with the private key generated in hardware and marked non-exportable. Neither platform's OS will let that key leave the secure element under any circumstance — that part isn't really a decision, it's just correctly using the platform.

The decision that actually took thought was a smaller-sounding question: what does a client need to prove to be allowed to *register* a new key in the first place? Our first draft of this was: any authenticated session (a valid JWT) can register a device key, since the JWT itself is proof someone is logged in. That's wrong, and it's wrong in a way that's worse than the system we were replacing. Here's the failure case: an attacker who steals a JWT — no password, just the token — could register their own key under the victim's account, then immediately request a challenge and sign it with that same attacker-controlled key. Full money-moving device trust, from a stolen token alone, with no password ever entered. Our *existing* password-based device verification doesn't have this hole — a stolen JWT alone can't pass it, because it still asks for the password. A "simpler" version of the new feature would have been a real regression hiding behind a modern-sounding name.

The fix was to require the same password re-proof for key registration that the original \`verifyDevice\` endpoint already requires. Since registering a key now costs exactly as much as the old password flow, a successful registration marks the device trusted immediately — there's no reason to make someone prove it twice in the same request. The entire value of the new key is in what it buys for every *future* step-up: a fingerprint prompt instead of retyping a password. It was never meant to make the *first* proof cheaper, only the ones after it.

\`\`\`kotlin
// DeviceService.kt
fun registerDeviceKey(userId: String, deviceId: String?, publicKeyBase64: String, password: String): TrustedDevice {
    // ... same rate limit + bcrypt check as verifyDevice ...
    if (!passwordEncoder.matches(password, user.passwordHash)) {
        throw InvalidDeviceVerificationException("Incorrect password")
    }
    parsePublicKey(publicKeyBase64) // fail fast on a malformed key
    device.publicKey = publicKeyBase64
    device.trusted = true // exactly as strong a proof as verifyDevice — no separate step needed
    device.verifiedAt = Instant.now()
    return trustedDeviceRepository.save(device)
}
\`\`\`

## One wire format, zero platform-specific conversion

The other thing we spent real time on before writing any code was picking a public-key encoding both platforms could produce natively, so neither client would need a DER/X.509 parsing step just to talk to the other's counterpart. Android Keystore's \`ECPublicKey.w\` and iOS's \`SecKeyCopyExternalRepresentation\` both hand back the exact same shape for an EC key if you ask for it right: a raw uncompressed point, \`0x04\` followed by the 32-byte X coordinate and the 32-byte Y coordinate, 65 bytes total. Base64 that and it's the same string format on both clients and the backend.

That sounds obvious in hindsight, but it's the difference between three lines of code and an afternoon of format-conversion bugs. On the backend, reconstructing a usable Java public key from those 65 bytes is a handful of lines:

\`\`\`kotlin
private fun parsePublicKey(publicKeyBase64: String): ECPublicKey {
    val raw = Base64.getDecoder().decode(publicKeyBase64)
    if (raw.size != 65 || raw[0] != 0x04.toByte()) {
        throw InvalidDeviceVerificationException("Public key must be a raw uncompressed P-256 point")
    }
    val x = BigInteger(1, raw.copyOfRange(1, 33))
    val y = BigInteger(1, raw.copyOfRange(33, 65))
    val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
    val keyFactory = KeyFactory.getInstance("EC")
    return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), params.getParameterSpec(ECParameterSpec::class.java))) as ECPublicKey
}
\`\`\`

On Android, the annoying part isn't the key generation, it's that \`BigInteger.toByteArray()\` doesn't reliably return a fixed 32-byte array — it strips leading zeros on small values and adds a sign byte on values whose high bit happens to be set. Skip normalizing that and you get a public key that's usually 64 or 66 bytes instead of 65, which fails the exact size check above on maybe one key generation in a few hundred — the kind of bug that passes every manual test and then fails for a real user weeks later. We wrote a small fixed-width padding helper specifically for this:

\`\`\`kotlin
private fun toFixedLength(bytes: ByteArray, length: Int): ByteArray {
    if (bytes.size == length) return bytes
    val result = ByteArray(length)
    if (bytes.size > length) {
        System.arraycopy(bytes, bytes.size - length, result, 0, length) // drop the sign byte
    } else {
        System.arraycopy(bytes, 0, result, length - bytes.size, bytes.size) // pad leading zeros
    }
    return result
}
\`\`\`

iOS never needed this — \`SecKeyCopyExternalRepresentation\` already returns the fixed-width form directly, no BigInteger involved. Same output, less code, because the platform API shape happened to match what we needed without translation.

## A challenge that can only be spent once

The last piece was making sure a signed challenge can't be replayed. Challenges live in Redis with a short TTL — the same store our login rate limiter already uses — but a plain "read it, then delete it" from application code has a real race: two concurrent verify-signature requests could both read the challenge before either deletes it, and both would pass. We closed that the same way our rate limiter already closes its own INCR-then-EXPIRE race: one atomic Lua script, not two round trips.

\`\`\`kotlin
private val consumeChallengeScript = DefaultRedisScript(
    """
    local v = redis.call('GET', KEYS[1])
    if v then redis.call('DEL', KEYS[1]) end
    return v
    """.trimIndent(),
    String::class.java,
)
\`\`\`

It's four lines of Lua, and it's the difference between "a challenge is single-use" being an actual guarantee versus a race condition wearing a comment that says it isn't one.

## What this bought, concretely

A user who opts in once — a single password prompt in Settings, the same cost as our existing device-verification flow — gets every future device step-up as a fingerprint or Face ID prompt instead. The password path never goes away; both native clients try the biometric signature first when a key is registered and fall through silently to the password field otherwise, so nothing breaks for anyone who hasn't opted in, and nothing about the fallback is a downgrade — it's exactly the flow that already existed.

The part worth remembering isn't the ECDSA math. It's that the actual security-critical decision in this whole feature was a one-sentence question — "what does registering a key cost, compared to what it's meant to replace?" — and getting that answer wrong would have shipped a real regression under a name that sounds like an upgrade.
`,
};
