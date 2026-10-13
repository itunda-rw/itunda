package rw.itunda.account

import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.TransferQuoteRecord
import rw.itunda.core.domain.TransferQuoteRecordStatus
import rw.itunda.core.repository.TransferQuoteRecordRepository
import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Stateful repository fake for service-level tests; production concurrency is verified by MySQL integration tests. */
internal fun mockTransferQuoteRepository(): TransferQuoteRecordRepository {
    val repository = mockk<TransferQuoteRecordRepository>()
    val records = ConcurrentHashMap<String, TransferQuoteRecord>()
    every { repository.save(any()) } answers {
        firstArg<TransferQuoteRecord>().also { records[it.id] = it }
    }
    every { repository.findById(any()) } answers {
        Optional.ofNullable(records[firstArg<String>()])
    }
    every { repository.claimPending(any(), any(), any(), any(), any(), any()) } answers {
        val id = firstArg<String>()
        val owner = secondArg<String>()
        val pending = thirdArg<TransferQuoteRecordStatus>()
        val claimed = invocation.args[3] as TransferQuoteRecordStatus
        val now = invocation.args[4] as Instant
        val claimedAt = invocation.args[5] as Instant
        val quote = records[id]
        if (quote != null && quote.userId == owner && quote.status == pending && quote.expiresAt.isAfter(now)) {
            quote.status = claimed
            quote.claimedAt = claimedAt
            1
        } else 0
    }
    every { repository.releaseClaim(any(), any(), any()) } answers {
        val quote = records[firstArg<String>()]
        if (quote != null && quote.status == secondArg<TransferQuoteRecordStatus>()) {
            quote.status = thirdArg<TransferQuoteRecordStatus>()
            quote.claimedAt = null
            1
        } else 0
    }
    every { repository.markConfirmed(any(), any(), any(), any()) } answers {
        val quote = records[firstArg<String>()]
        if (quote != null && quote.status == secondArg<TransferQuoteRecordStatus>()) {
            quote.status = thirdArg<TransferQuoteRecordStatus>()
            quote.confirmedAt = invocation.args[3] as Instant
            1
        } else 0
    }
    return repository
}
