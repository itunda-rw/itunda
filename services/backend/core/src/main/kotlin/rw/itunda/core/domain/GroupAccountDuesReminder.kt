package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Dedupe record so an unpaid member gets at most one real dues reminder per real
 * calendar cycle, whether it was sent by the automatic `GroupAccountDuesReminderScheduler`
 * or the organizer's own manual "request all unpaid" one-tap action -- see
 * GroupAccountService's own doc comment. A unique (group account, user, cycle) row is
 * the whole mechanism: presence means "already reminded this cycle", so a daily
 * scheduler poll never re-spams the same member.
 */
@Entity
@Table(name = "group_account_dues_reminders")
class GroupAccountDuesReminder(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_account_id", nullable = false, length = 64)
    val groupAccountId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "cycle_month", nullable = false, length = 7)
    val cycleMonth: String,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupAccountId = "", userId = "", cycleMonth = "")
}
