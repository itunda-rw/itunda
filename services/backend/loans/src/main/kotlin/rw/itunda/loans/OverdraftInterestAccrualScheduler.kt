package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real background automation for the overdraft account's own real daily interest
 * accrual -- see OverdraftAccount.kt's own doc comment. Same "business condition is
 * real (a real day has passed since the last accrual, and there's a real nonzero
 * drawn balance to accrue against), poll interval is demo-speed" shape every other
 * recurring feature's own scheduler in this codebase already establishes
 * (`AutoTopUpScheduler`, `AutoSaveScheduler`, `SplitBillReminderScheduler`). A fresh
 * draw with `lastAccrualAt == null` is immediately due on the very first poll, the same
 * "null means immediately eligible" trick those schedulers already use, so live
 * verification never needs to wait out a real 24 hours.
 */
@Component
class OverdraftInterestAccrualScheduler(private val overdraftService: OverdraftService) {
    private val log = LoggerFactory.getLogger(OverdraftInterestAccrualScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val accounts = overdraftService.getAccountsDueForAccrual()
        for (account in accounts) {
            try {
                overdraftService.accrueInterest(account)
            } catch (e: Exception) {
                log.error("Overdraft interest accrual failed for account {}", account.id, e)
            }
        }
    }
}
