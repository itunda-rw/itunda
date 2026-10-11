package rw.itunda.ledger.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import rw.itunda.ledger.api.controller.TransferRequest
import rw.itunda.ledger.domain.AccountStatus
import rw.itunda.ledger.domain.AccountType
import rw.itunda.ledger.domain.LedgerAccount
import rw.itunda.ledger.domain.LedgerAccountRepository
import rw.itunda.ledger.domain.LedgerIdempotencyRecord
import rw.itunda.ledger.domain.LedgerIdempotencyRepository
import rw.itunda.ledger.domain.OutboxEvent
import rw.itunda.ledger.domain.OutboxEventRepository
import java.math.BigDecimal

class LedgerApplicationServiceTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `locks both accounts in deterministic order and persists balanced state`() {
        val accounts = RecordingLedgerAccountRepository(
            listOf(account("z-source", "100.00"), account("a-destination", "10.00"))
        )
        val outbox = RecordingOutboxRepository()
        val idempotency = RecordingIdempotencyRepository()
        val service = service(accounts, outbox, idempotency)

        service.executeTransfer(request())

        assertEquals(listOf("a-destination", "z-source"), accounts.lockCalls)
        assertEquals(0, accounts.accounts.getValue("z-source").balance.compareTo(BigDecimal("75.00")))
        assertEquals(0, accounts.accounts.getValue("a-destination").balance.compareTo(BigDecimal("35.00")))
        assertEquals(1, outbox.events.size)
        assertEquals(1, idempotency.records.values.count { it.statusCode == 200 })
    }

    @Test
    fun `replays completed transfer without moving money twice`() {
        val accounts = RecordingLedgerAccountRepository(
            listOf(account("source", "100.00"), account("destination", "0.00"))
        )
        val outbox = RecordingOutboxRepository()
        val service = service(accounts, outbox, RecordingIdempotencyRepository())

        val first = service.executeTransfer(request("source", "destination", "retry-key"))
        val second = service.executeTransfer(request("source", "destination", "retry-key"))

        assertEquals(first, second)
        assertEquals(1, accounts.saveCalls)
        assertEquals(1, outbox.events.size)
        assertEquals(0, accounts.accounts.getValue("source").balance.compareTo(BigDecimal("75.00")))
    }

    @Test
    fun `rejects reuse of idempotency key with a different request body`() {
        val accounts = RecordingLedgerAccountRepository(
            listOf(account("source", "100.00"), account("destination", "0.00"))
        )
        val service = service(accounts, RecordingOutboxRepository(), RecordingIdempotencyRepository())
        service.executeTransfer(request("source", "destination", "same-key"))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.executeTransfer(
                TransferRequest("source", "destination", BigDecimal("20.00"), "same-key")
            )
        }

        assertEquals(409, error.statusCode.value())
        assertEquals(1, accounts.saveCalls)
    }

    @Test
    fun `rejects same-account transfer before locking or writing`() {
        val accounts = RecordingLedgerAccountRepository(listOf(account("same", "100.00")))
        val outbox = RecordingOutboxRepository()
        val idempotency = RecordingIdempotencyRepository()
        val service = service(accounts, outbox, idempotency)

        assertThrows(IllegalArgumentException::class.java) {
            service.executeTransfer(request("same", "same", "self-transfer"))
        }

        assertEquals(emptyList<String>(), accounts.lockCalls)
        assertEquals(0, accounts.saveCalls)
        assertEquals(0, outbox.events.size)
        assertEquals(0, idempotency.records.size)
    }

    private fun service(
        accounts: RecordingLedgerAccountRepository,
        outbox: RecordingOutboxRepository,
        idempotency: RecordingIdempotencyRepository
    ): LedgerApplicationService {
        val claimStore = LedgerIdempotencyClaimStore(idempotency)
        val executor = LedgerTransferExecutor(accounts, outbox, idempotency, objectMapper)
        return LedgerApplicationService(claimStore, executor, objectMapper)
    }

    private fun request(
        source: String = "z-source",
        destination: String = "a-destination",
        key: String = "transfer-1"
    ) = TransferRequest(source, destination, BigDecimal("25.00"), key)

    private fun account(id: String, balance: String) = LedgerAccount(
        accountId = id,
        customerId = "customer-$id",
        currency = "RWF",
        balance = BigDecimal(balance),
        accountType = AccountType.USER_WALLET,
        status = AccountStatus.ACTIVE
    )

    private class RecordingLedgerAccountRepository(
        initialAccounts: List<LedgerAccount>
    ) : LedgerAccountRepository {
        val accounts = initialAccounts.associateByTo(mutableMapOf()) { it.accountId }
        val lockCalls = mutableListOf<String>()
        var saveCalls = 0

        override fun findById(accountId: String): LedgerAccount? = accounts[accountId]

        override fun findByIdForUpdate(accountId: String): LedgerAccount? {
            lockCalls += accountId
            return accounts[accountId]
        }

        override fun save(account: LedgerAccount): LedgerAccount {
            saveCalls += 1
            accounts[account.accountId] = account
            return account
        }

        override fun saveAll(accountsToSave: List<LedgerAccount>): List<LedgerAccount> {
            saveCalls += 1
            accountsToSave.forEach { accounts[it.accountId] = it }
            return accountsToSave
        }
    }

    private class RecordingOutboxRepository : OutboxEventRepository {
        val events = mutableListOf<OutboxEvent>()

        override fun save(event: OutboxEvent): OutboxEvent {
            events += event
            return event
        }
    }

    private class RecordingIdempotencyRepository : LedgerIdempotencyRepository {
        val records = mutableMapOf<String, LedgerIdempotencyRecord>()

        override fun findByKey(recordKey: String): LedgerIdempotencyRecord? = records[recordKey]

        override fun insertIfAbsent(record: LedgerIdempotencyRecord): Boolean {
            if (records.containsKey(record.recordKey)) return false
            records[record.recordKey] = record
            return true
        }

        override fun complete(recordKey: String, statusCode: Int, transactionId: String): Int {
            val record = records[recordKey] ?: return 0
            if (record.statusCode != -1) return 0
            records[recordKey] = record.copy(statusCode = statusCode, transactionId = transactionId)
            return 1
        }

        override fun deleteByKey(recordKey: String) {
            records.remove(recordKey)
        }
    }
}
