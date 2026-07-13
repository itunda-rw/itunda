package rw.itunda.overview

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.LinkedAccountRepository
import java.time.Instant
import java.util.UUID

class LinkedAccountNotFoundException(message: String) : RuntimeException(message)
class LinkedAccountNotOwnedException(message: String) : RuntimeException(message)
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
) {
    @Transactional
    fun link(userId: String, provider: String, externalAccountNumber: String): LinkedAccount {
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
        if (account.userId != userId) {
            throw LinkedAccountNotOwnedException("That linked account does not belong to you")
        }
        if (account.status == LinkedAccountStatus.UNLINKED) {
            throw LinkedAccountAlreadyUnlinkedException("This account is already unlinked")
        }
        account.status = LinkedAccountStatus.UNLINKED
        account.unlinkedAt = Instant.now()
        return linkedAccountRepository.save(account)
    }
}
