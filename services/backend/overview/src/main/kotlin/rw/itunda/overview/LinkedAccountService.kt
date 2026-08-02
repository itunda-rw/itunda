package rw.itunda.overview

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.LinkedAccountRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LinkedAccountNotFoundException(message: String) : RuntimeException(message)
class LinkedAccountAlreadyUnlinkedException(message: String) : RuntimeException(message)

/**
 * Real external bank/MoMo account consent registry -- see LinkedAccount's own doc
 * comment. Provider verification is a real call through ProviderConnector/
 * RailCatalog -- the same simulated (latency/success-rate per rail) mechanism
 * transfers/bills/airtime already use, so "linking" a MTN MoMo account genuinely
 * exercises the mtn_momo rail profile rather than an unconditional success.
 */
@Service
class LinkedAccountService(
    private val linkedAccountRepository: LinkedAccountRepository,
    private val providerConnector: ProviderConnector,
    private val demoExternalBalanceService: DemoExternalBalanceService,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun link(userId: String, provider: String, externalAccountNumber: String): LinkedAccount {
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Unlike
        // every other create-a-row endpoint in this backend, `link` had no dedup logic
        // at all: a repeat call with the same provider/account number just creates
        // another real row and burns another real simulated ProviderConnector call
        // every single time, unlike a real bank consent flow.
        rateLimiter.checkLimit("accounts:link:$userId", limit = 10, window = Duration.ofHours(1))
        require(externalAccountNumber.length >= 4) { "Account number is too short to link" }
        val masked = "•••• " + externalAccountNumber.takeLast(4)
        val rail = RailCatalog.resolve(provider)

        val account = LinkedAccount(
            id = "linked_${UUID.randomUUID()}",
            userId = userId,
            provider = provider,
            externalAccountNumberMasked = masked,
            status = LinkedAccountStatus.LINKED,
        )

        try {
            providerConnector.attempt(rail, "Account verification for $provider")
            // Real demo balance (2026-07-17), only generated on a real successful
            // verification -- see DemoExternalBalanceService's own doc comment. A
            // VERIFICATION_FAILED account never got real consent, so it shouldn't show
            // a balance of any kind, demo or otherwise.
            account.demoBalance = demoExternalBalanceService.generate(provider, externalAccountNumber)
            account.demoBalanceCurrency = "RWF"
        } catch (e: ProviderDeclinedException) {
            account.status = LinkedAccountStatus.VERIFICATION_FAILED
            account.failureReason = e.message
        }

        return linkedAccountRepository.save(account)
    }

    fun getMyLinkedAccounts(userId: String): List<LinkedAccount> =
        linkedAccountRepository.findByUserIdOrderByLinkedAtDesc(userId)

    @Transactional
    fun unlink(userId: String, accountId: String): LinkedAccount {
        val account = linkedAccountRepository.findById(accountId)
            .orElseThrow { LinkedAccountNotFoundException("Linked account not found") }
        // Real IDOR fix (2026-08-02): accountId is a real URL path variable
        // (POST /api/v1/accounts/link/{accountId}/unlink), directly probeable/
        // enumerable -- a linked account belonging to a DIFFERENT user used to throw
        // LinkedAccountNotOwnedException, mapped to a real 403 that confirmed the id
        // was real. Same real-existence-confirming probe WalletService.getWalletById's
        // own doc comment already documents fixing for wallet lookups; same fix here.
        if (account.userId != userId) {
            throw LinkedAccountNotFoundException("Linked account not found")
        }
        if (account.status == LinkedAccountStatus.UNLINKED) {
            throw LinkedAccountAlreadyUnlinkedException("This account is already unlinked")
        }
        account.status = LinkedAccountStatus.UNLINKED
        account.unlinkedAt = Instant.now()
        return linkedAccountRepository.save(account)
    }
}
