package rw.itunda.core.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.SupportTicket
import rw.itunda.core.domain.SupportTicketCategory
import rw.itunda.core.domain.SupportTicketResolution
import rw.itunda.core.domain.SupportTicketStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.SupportTicketRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class SupportServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a real transaction between two users") {
        val supportTicketRepository = mockk<SupportTicketRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = SupportService(supportTicketRepository, transactionRepository, walletRepository, ledgerEntryRepository, ledgerService)

        val payerWallet = wallet("wallet_payer", "user_1")
        val transaction = Transaction(
            id = "ledgertxn_1", referenceNumber = "TXN1", senderId = "user_1", recipientId = "user_2",
            fromWalletId = "wallet_payer", toWalletId = "wallet_recipient", amount = BigDecimal("5000"),
            fee = BigDecimal("50"), currency = "RWF", type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED,
            description = "test payment",
        )

        every { transactionRepository.findById("ledgertxn_1") } returns Optional.of(transaction)
        every { supportTicketRepository.save(any()) } answers { firstArg() }

        When("the sender files a GENERAL ticket about it") {
            val ticket = service.createTicket("user_1", "ledgertxn_1", SupportTicketCategory.GENERAL, "Wrong amount")

            Then("a real ticket is created, unresolved, with no wallet frozen") {
                ticket.userId shouldBe "user_1"
                ticket.status shouldBe SupportTicketStatus.OPEN
                ticket.frozeWalletId shouldBe null
            }
            Then("its SLA due date is real itunda policy for GENERAL (72 hours)") {
                val hoursUntilDue = java.time.Duration.between(Instant.now(), ticket.dueBy).toHours()
                (hoursUntilDue in 71..72) shouldBe true
            }
        }

        When("someone with no connection to the transaction tries to file a ticket") {
            Then("it throws SupportTransactionNotOwnedException") {
                try {
                    service.createTicket("user_stranger", "ledgertxn_1", SupportTicketCategory.GENERAL, "Not mine")
                    error("expected SupportTransactionNotOwnedException")
                } catch (e: SupportTransactionNotOwnedException) {
                    // expected
                }
            }
        }

        When("the sender files an ACCOUNT_TAKEOVER ticket") {
            every { walletRepository.findById("wallet_payer") } returns Optional.of(payerWallet)
            every { walletRepository.save(any()) } answers { firstArg() }

            val ticket = service.createTicket("user_1", "ledgertxn_1", SupportTicketCategory.ACCOUNT_TAKEOVER, "I never sent this")

            Then("it real-freezes the sender's own wallet from this transaction") {
                payerWallet.isActive shouldBe false
                ticket.frozeWalletId shouldBe "wallet_payer"
            }
            Then("its SLA due date is the tighter 4-hour ACCOUNT_TAKEOVER policy") {
                val hoursUntilDue = java.time.Duration.between(Instant.now(), ticket.dueBy).toHours()
                (hoursUntilDue in 3..4) shouldBe true
            }
        }
    }

    Given("an open ticket for a real transaction with real ledger legs") {
        val supportTicketRepository = mockk<SupportTicketRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = SupportService(supportTicketRepository, transactionRepository, walletRepository, ledgerEntryRepository, ledgerService)

        val frozenWallet = wallet("wallet_payer", "user_1").apply { isActive = false }
        val ticket = SupportTicket(
            id = "ticket_1", userId = "user_1", transactionId = "ledgertxn_1",
            category = SupportTicketCategory.ACCOUNT_TAKEOVER, description = "unauthorized",
            frozeWalletId = "wallet_payer", dueBy = Instant.now().plusSeconds(3600),
        )
        val originalLegs = listOf(
            LedgerEntry(id = "e1", transactionId = "ledgertxn_1", accountId = "wallet_payer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("5000"), currency = "RWF", balanceAfter = BigDecimal("5000"), memo = "payment to user_2"),
            LedgerEntry(id = "e2", transactionId = "ledgertxn_1", accountId = "wallet_recipient", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5000"), currency = "RWF", balanceAfter = BigDecimal("5000"), memo = "payment received"),
        )

        every { supportTicketRepository.findById("ticket_1") } returns Optional.of(ticket)
        every { supportTicketRepository.save(any()) } answers { firstArg() }
        every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalLegs
        every { walletRepository.findById("wallet_payer") } returns Optional.of(frozenWallet)
        every { walletRepository.save(any()) } answers { firstArg() }

        When("an admin resolves it with REFUNDED") {
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_refund", emptyList())
            val ticketSavedSlot = slot<SupportTicket>()
            every { supportTicketRepository.save(capture(ticketSavedSlot)) } answers { firstArg() }

            val resolved = service.resolve("ticket_1", "admin_1", SupportTicketResolution.REFUNDED, "Confirmed unauthorized")

            // Real bug found live (2026-08-02): resolve() already read this exact
            // ticket, checked its status, then wrote back to it -- the correct SHAPE
            // for a race-safe check-then-act, same as RideTripService.acceptTrip -- but
            // with no @Version, two reviewers concurrently resolving the same ticket as
            // REFUNDED could both pass the status check before either committed and
            // both post a real double refund. This asserts the mechanism the fix now
            // relies on: the SAME versioned entity that was read and status-checked is
            // the one actually passed to save(), so a concurrent second resolve() on a
            // stale version real-409s via the existing global
            // ObjectOptimisticLockingFailureException handler.
            Then("the same versioned ticket instance that was read is the one saved") {
                ticketSavedSlot.captured shouldBe ticket
                ticketSavedSlot.captured.version shouldBe ticket.version
            }
            Then("it posts a real reversing ledger transaction with every leg flipped") {
                val legs = legsSlot.captured
                legs.size shouldBe 2
                val payerLeg = legs.first { it.accountId == "wallet_payer" }
                val recipientLeg = legs.first { it.accountId == "wallet_recipient" }
                payerLeg.direction shouldBe LedgerDirection.CREDIT
                recipientLeg.direction shouldBe LedgerDirection.DEBIT
            }
            Then("the ticket is resolved and tied to the real refund transaction") {
                resolved.status shouldBe SupportTicketStatus.RESOLVED
                resolved.resolution shouldBe SupportTicketResolution.REFUNDED
                resolved.refundTransactionId shouldBe "ledgertxn_refund"
                resolved.reviewedBy shouldBe "admin_1"
            }
            Then("the frozen wallet is real-unfrozen") {
                frozenWallet.isActive shouldBe true
            }
        }

        When("resolving it again") {
            Then("it throws SupportTicketAlreadyResolvedException") {
                ticket.status = SupportTicketStatus.RESOLVED
                try {
                    service.resolve("ticket_1", "admin_1", SupportTicketResolution.REJECTED, null)
                    error("expected SupportTicketAlreadyResolvedException")
                } catch (e: SupportTicketAlreadyResolvedException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
