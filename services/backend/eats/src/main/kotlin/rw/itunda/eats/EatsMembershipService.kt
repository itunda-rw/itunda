package rw.itunda.eats

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EatsMembership
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EatsMembershipRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class EatsMembershipNoWalletException(message: String) : RuntimeException(message)
class InvalidMembershipDurationException(message: String) : RuntimeException(message)

/**
 * Real Baemin Club (배민클럽)-style free-delivery membership -- see `EatsMembership.kt`'s
 * own doc comment for the full sourced account, including the honest "exact pricing
 * unconfirmed, itunda's own scoping choice" reasoning for `MEMBERSHIP_TIERS`.
 */
@Service
class EatsMembershipService(
    private val eatsMembershipRepository: EatsMembershipRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
) {
    companion object {
        // Real flat-fee tiers, same "pay once, extend, stack" model
        // Listing.BOOST_TIERS/MerchantAd.LOCAL_AD_TIERS already established -- itunda's
        // own honest scoping choice, not a currency-converted reuse of an unconfirmed
        // real Baemin Club price.
        val MEMBERSHIP_TIERS: Map<Int, BigDecimal> = mapOf(
            30 to BigDecimal("1500"),
            90 to BigDecimal("4000"),
        )
    }

    @Transactional
    fun subscribe(userId: String, days: Int): EatsMembership {
        val price = MEMBERSHIP_TIERS[days]
            ?: throw InvalidMembershipDurationException("Choose a real membership duration -- ${MEMBERSHIP_TIERS.keys.sorted().joinToString()} days")

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw EatsMembershipNoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, price, "Eats membership for $days days"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, price, "Eats membership fee"),
            ),
        )

        val existing = eatsMembershipRepository.findByUserId(userId)
        val now = Instant.now()
        val currentActiveUntil = existing?.activeUntil?.takeIf { it.isAfter(now) } ?: now
        val membership = existing ?: EatsMembership(id = "eats_membership_${UUID.randomUUID()}", userId = userId, activeUntil = currentActiveUntil)
        membership.activeUntil = currentActiveUntil.plus(Duration.ofDays(days.toLong()))
        membership.updatedAt = now
        return eatsMembershipRepository.save(membership)
    }

    fun getMyMembership(userId: String): EatsMembership? = eatsMembershipRepository.findByUserId(userId)

    fun hasActiveMembership(userId: String): Boolean {
        val membership = eatsMembershipRepository.findByUserId(userId) ?: return false
        return membership.activeUntil.isAfter(Instant.now())
    }
}
