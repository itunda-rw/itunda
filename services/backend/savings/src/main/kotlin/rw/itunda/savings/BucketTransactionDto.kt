package rw.itunda.savings

import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import java.math.BigDecimal
import java.time.Instant

// Real per-bucket detail screens (2026-08-31, direct user-supplied Toss Bank
// screenshots: 보관하기/매일모으기 each get their own full-screen ledger, not just an
// inline card) -- every savings bucket (Savings Goal, Interest Jar, Weekly/Grow31/
// Upfront plans) now posts to its own real ledger account (see SavingsService/
// WeeklySavingsService/Grow31SavingsService/UpfrontInterestDepositService's own
// get*Transactions methods), but each backing LedgerEntry carries generic
// account/direction/memo fields, not a client-friendly shape. This one normalized DTO
// lets every platform (web/Android/iOS) render every bucket's ledger with ONE shared
// row component, regardless of which product it came from.
data class BucketTransactionDto(
    val id: String,
    val description: String,
    val amount: BigDecimal,
    val isCredit: Boolean,
    val balanceAfter: BigDecimal,
    val createdAt: Instant,
)

fun LedgerEntry.toBucketTransactionDto() = BucketTransactionDto(
    id = id,
    description = memo,
    amount = amount,
    isCredit = direction == LedgerDirection.CREDIT,
    balanceAfter = balanceAfter,
    createdAt = createdAt,
)
