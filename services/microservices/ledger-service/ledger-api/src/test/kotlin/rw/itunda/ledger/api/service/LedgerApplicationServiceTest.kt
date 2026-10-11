package rw.itunda.ledger.api.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import rw.itunda.ledger.api.controller.TransferRequest
import rw.itunda.ledger.domain.AccountStatus
import rw.itunda.ledger.domain.AccountType
import rw.itunda.ledger.domain.LedgerAccount
import rw.itunda.ledger.domain.LedgerAccountRepository
import rw.itunda.ledger.domain.OutboxEvent
import rw.itunda.ledger.domain.OutboxEventRepository
import java.math.BigDecimal

class LedgerApplicationServiceTest {

    @Test
    fun `locks both accounts in deterministic order and persists balanced state`() {
        val repository = RecordingLedgerAccountRepository(
            listOf(
                account("z-source", "100.00"),
                account("a-destination", "10.00")
            )
        )
        val outbox = RecordingOutboxRepository()
        val service = LedgerApplicationService(repository, outbox)

        service.executeTransfer(
            TransferRequest(
                sourceAccountId = "z-source",
                destinationAccountId = "a-destination",
                amount = BigDecimal("25.00"),
                idempotencyKey = "transfer-1"
            )
        )

        assertEquals(listOf("a-destination", "z-source"), repository.lockCalls)
        assertEquals(0, repository.accounts.getValue("z-source").balance.compareTo(BigDecimal("75.00")))
        assertEquals(0, repository.accounts.getValue("a-destination").balance.compareTo(BigDecimal("35.00")))
        assertEquals(1, outbox.events.size)
    }

    @Test
    fun `rejects same-account transfer before locking or writing`() {
        val repository = RecordingLedgerAccountRepository(listOf(account("same", "100.00")))
        val outbox = RecordingOutboxRepository()
        val service = LedgerApplicationService(repository, outbox)

        assertThrows(IllegalArgumentException::class.java) {
            service.executeTransfer(
                TransferRequest(
                    sourceAccountId = "same",
                    destinationAccountId = "same",
                    amount = BigDecimal("10.00"),
                    idempotencyKey = "self-transfer"
                )
            )
        }

        assertEquals(emptyList<String>(), repository.lockCalls)
        assertEquals(0, repository.saveCalls)
        assertEquals(0, outbox.events.size)
    }

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
}
