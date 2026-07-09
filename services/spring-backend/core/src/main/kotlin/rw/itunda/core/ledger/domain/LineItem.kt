package rw.itunda.core.ledger.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "ledger_line_items")
data class LineItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    val journalEntry: JournalEntry,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: Account,

    @Column(nullable = false)
    val amount: BigDecimal, // Must be positive

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val direction: Direction
)

enum class Direction {
    DEBIT, CREDIT
}
