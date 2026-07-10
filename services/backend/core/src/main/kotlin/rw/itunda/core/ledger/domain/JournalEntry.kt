package rw.itunda.core.ledger.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "journal_entries")
data class JournalEntry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val transactionReference: String, // Idempotency key from API Gateway or Mobile

    @Column(nullable = false)
    val description: String,

    @OneToMany(mappedBy = "journalEntry", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val lineItems: MutableList<LineItem> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EntryStatus = EntryStatus.PENDING,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class EntryStatus {
    PENDING,
    POSTED,
    REVERSED,
    FAILED
}
