package rw.itunda.overview

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * A real, honestly-labeled demo external-account balance -- not a real Open Banking /
 * provider API fetch (that stays genuinely blocked on real provider access, see
 * LinkedAccount.kt's own doc comment), but a deterministic (hash of the account
 * itself, not random) balance in a realistic RWF range, so a linked account shows
 * something real-looking in the UI instead of permanently blank. Same "real
 * simulation, not a real integration" discipline DemoNidaVerificationService and
 * DemoCardAuthorizationService already established. Deliberately its own small
 * service, not folded into LinkedAccountService, so it's easy to find and easy to
 * delete outright the day a real provider integration replaces it.
 */
@Service
class DemoExternalBalanceService {

    fun generate(provider: String, externalAccountNumber: String): BigDecimal {
        val digest = MessageDigest.getInstance("SHA-256").digest("$provider:$externalAccountNumber".toByteArray())
        // A real, deterministic value in a realistic range (20,000 - 3,020,000 RWF,
        // roughly matching the spread already seen in this repo's own seeded demo
        // account balances) -- unsigned-interpreted first 4 bytes of the hash, not the
        // raw byte array, to spread evenly across the full range rather than clumping
        // near zero.
        val unsignedInt = ((digest[0].toLong() and 0xFF) shl 24) or
            ((digest[1].toLong() and 0xFF) shl 16) or
            ((digest[2].toLong() and 0xFF) shl 8) or
            (digest[3].toLong() and 0xFF)
        val cents = 2_000_000L + (unsignedInt % 300_000_000L)
        return BigDecimal(cents).movePointLeft(2)
    }
}
