package rw.itunda.agents

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Real MTN MoMo-style agent cash-in/cash-out commission -- sourced from MTN Mobile
 * Money's own real, published agent commission mechanic: a flat fee for small
 * transactions, and a percentage-based commission that increases by real transaction
 * band for larger ones, rewarding agents who handle higher transaction values
 * (momo.mtn.com/pricing, momo.mtn.com/agents; a concrete real published rate card was
 * found for MTN MoMo Ghana specifically -- jbklutse.com/mtn-momo-fees-2026 -- not
 * Rwanda's own, which wasn't found during research).
 *
 * Honest scope: the real STRUCTURE (flat-then-tiered-percentage, rewarding larger
 * transactions, real per-transaction cap) is sourced; these exact RWF amounts/
 * percentages/bands are itunda's own honest adaptation for its own Rwanda-modeled
 * agent network, not presented as copied from a real published Rwanda rate card that
 * doesn't appear to exist publicly.
 *
 * Closes a real gap this session found by re-reading `AgentService`'s own already-
 * shipped `cashIn`/`cashOut`: a mature float-management/receipt/daily-limit system
 * existed with zero commission ever credited to the real human who operates the till --
 * a real itunda agent earned nothing for providing this service, unlike every real
 * mobile money agent network this is modeled on.
 */
object AgentCommissionSchedule {
    private val TIER_1_MAX = BigDecimal("5000")
    private val TIER_2_MAX = BigDecimal("20000")
    private val TIER_3_MAX = BigDecimal("100000")
    private val TIER_1_FLAT = BigDecimal("50")
    private val TIER_2_RATE = BigDecimal("0.01")
    private val TIER_3_RATE = BigDecimal("0.015")
    private val TIER_4_RATE = BigDecimal("0.02")
    private val MAX_COMMISSION = BigDecimal("3000")

    fun computeCommission(amount: BigDecimal): BigDecimal {
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        val commission = when {
            amount <= TIER_1_MAX -> TIER_1_FLAT
            amount <= TIER_2_MAX -> amount.multiply(TIER_2_RATE).setScale(2, RoundingMode.HALF_UP)
            amount <= TIER_3_MAX -> amount.multiply(TIER_3_RATE).setScale(2, RoundingMode.HALF_UP)
            else -> amount.multiply(TIER_4_RATE).setScale(2, RoundingMode.HALF_UP)
        }
        return commission.min(MAX_COMMISSION)
    }
}
