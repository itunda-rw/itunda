package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real recurring premium collection (2026-08-02) -- see InsuranceService.collectPremium's
 * own doc comment for the bug this closes: enrollInPlan charged the first premium and set
 * nextPaymentDate = now + 30 days, but nothing ever read that field again, so every policy
 * silently stopped being paid for after month one, forever. The *business* cadence is real
 * (30 days since a policy's last premium collection, matching enrollInPlan's own
 * nextPaymentDate.plusDays(30)), same convention as AutoSaveScheduler's own
 * AUTO_CONTRIBUTION_INTERVAL_DAYS. The *poll* interval below is demo-speed, same
 * convention as AutoSaveScheduler's/OutboxRelay's own fixedDelay -- checking every 30
 * seconds for policies/funds whose real 30-day window has elapsed is cheap and correct;
 * it does not mean premiums are collected or funds are contributed to every 30 seconds.
 *
 * Two jobs, one scheduler: collect due premiums first (draining a linked
 * InsurancePremiumFund when the account alone is short), then auto-contribute to funds
 * that are due for their own recurring top-up -- mirroring how AutoSaveScheduler and
 * InterestAccrualScheduler each own one real recurring job for their module.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): both `collectPremium` and `autoContributeToFund` call
 * `ledgerService.postLedgerTransaction` with no try/catch of their own, and neither
 * loop here had one either -- a single bad policy/fund (a since-deleted account, a
 * transient ledger error) would throw uncaught and silently stop the REST of that
 * same loop (and, for a failure in the first loop, the second loop never even runs)
 * for every other real due row. Per-item try/catch in both loops closes it.
 */
@Component
class InsurancePremiumScheduler(private val insuranceService: InsuranceService) {
    private val log = LoggerFactory.getLogger(InsurancePremiumScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val duePolicies = insuranceService.getPoliciesDueForPremiumCollection()
        for (policy in duePolicies) {
            try {
                val collected = insuranceService.collectPremium(policy)
                if (collected) {
                    log.info("Collected premium {} for policy {} ({})", policy.monthlyPremium, policy.id, policy.planName)
                } else {
                    log.info("Premium collection failed for policy {} ({}) -- policy lapsed", policy.id, policy.planName)
                }
            } catch (e: Exception) {
                log.error("Premium collection failed unexpectedly for policy {}", policy.id, e)
            }
        }

        val dueFunds = insuranceService.getFundsDueForAutoContribution()
        for (fund in dueFunds) {
            try {
                val contributed = insuranceService.autoContributeToFund(fund)
                if (contributed) {
                    log.info("Auto-contributed {} to premium fund {} (policy {})", fund.dailyContribution, fund.id, fund.policyId)
                }
            } catch (e: Exception) {
                log.error("Premium fund auto-contribution failed for fund {}", fund.id, e)
            }
        }
    }
}
