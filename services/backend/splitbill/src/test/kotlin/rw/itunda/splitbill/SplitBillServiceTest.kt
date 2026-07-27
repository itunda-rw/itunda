package rw.itunda.splitbill

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.SplitBill
import rw.itunda.core.domain.SplitBillParticipant
import rw.itunda.core.domain.SplitBillParticipantStatus
import rw.itunda.core.domain.SplitBillStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SplitBillParticipantRepository
import rw.itunda.core.repository.SplitBillRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.messaging.GroupMemberInfo
import rw.itunda.messaging.GroupMessagingService
import rw.itunda.messaging.GroupNotFoundException
import java.math.BigDecimal
import java.util.Optional

class SplitBillServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, balance: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("evenSplitWithRoundingAbsorption in isolation") {
        val service = SplitBillService(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(relaxed = true))

        Then("a cleanly divisible total splits evenly with no remainder") {
            val shares = service.evenSplitWithRoundingAbsorption(BigDecimal("3000"), 3)
            shares shouldBe listOf(BigDecimal("1000.00"), BigDecimal("1000.00"), BigDecimal("1000.00"))
        }

        Then("a non-divisible total silently absorbs the remainder into the last participant, reconciling exactly") {
            // 1000 RWF / 3 = 333.33... -- in minor units (cents), 100000 / 3 = 33333
            // remainder 1, so two participants get 333.33 and the absorbing participant
            // gets 333.34 (the leftover 1 cent), summing back to exactly 1000.00.
            val shares = service.evenSplitWithRoundingAbsorption(BigDecimal("1000"), 3)
            shares.dropLast(1) shouldBe listOf(BigDecimal("333.33"), BigDecimal("333.33"))
            shares.last() shouldBe BigDecimal("333.34")
            shares.reduce { a, b -> a + b } shouldBe BigDecimal("1000.00")
        }
    }

    Given("a real organizer, a real group of 3 (organizer + 2 others), and a real total bill") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(
            splitBillRepository, splitBillParticipantRepository, walletRepository,
            transactionRepository, ledgerService, groupMessagingService, rateLimiter,
        )

        val group = GroupConversation(id = "group_1", name = "Dinner squad", createdBy = "user_organizer")
        val message = GroupMessage(id = "group_message_1", groupConversationId = "group_1", senderId = "user_organizer", body = "split")

        every { groupMessagingService.getGroupForMember("user_organizer", "group_1") } returns group
        every { groupMessagingService.getMembers("user_organizer", "group_1") } returns listOf(
            GroupMemberInfo("user_organizer", "Organizer"),
            GroupMemberInfo("user_a", "Alice"),
            GroupMemberInfo("user_b", "Bob"),
        )
        every { groupMessagingService.sendMessage(any(), any(), any()) } returns message
        every { splitBillRepository.save(any()) } answers { firstArg() }
        every { splitBillParticipantRepository.saveAll<SplitBillParticipant>(any()) } answers { firstArg() }

        When("the organizer creates a real split bill among the 2 other real members") {
            val result = service.createSplitBill("user_organizer", "group_1", BigDecimal("1000"), "Dinner", listOf("user_a", "user_b"))

            Then("it creates a real OPEN split bill with 2 participants whose shares reconcile exactly to the total") {
                result.splitBill.organizerId shouldBe "user_organizer"
                result.splitBill.status shouldBe SplitBillStatus.OPEN
                result.participants.size shouldBe 2
                result.participants.sumOf { it.shareAmount } shouldBe BigDecimal("1000.00")
                verify(exactly = 1) { groupMessagingService.sendMessage("user_organizer", "group_1", any()) }
            }
        }

        When("the organizer lists themselves as a participant of their own split bill") {
            Then("the organizer is silently excluded rather than owing their own request") {
                val result = service.createSplitBill("user_organizer", "group_1", BigDecimal("900"), "Dinner", listOf("user_organizer", "user_a", "user_b"))
                result.participants.map { it.userId }.toSet() shouldBe setOf("user_a", "user_b")
            }
        }

        When("a named participant isn't actually a real member of the group") {
            Then("it throws SplitBillParticipantNotGroupMemberException before any real money-adjacent row is created") {
                try {
                    service.createSplitBill("user_organizer", "group_1", BigDecimal("1000"), "Dinner", listOf("user_a", "user_stranger"))
                    error("expected SplitBillParticipantNotGroupMemberException")
                } catch (e: SplitBillParticipantNotGroupMemberException) {
                    // expected
                }
                verify(exactly = 0) { splitBillRepository.save(any()) }
            }
        }

        When("a non-member of the group tries to create a split bill in it") {
            every { groupMessagingService.getGroupForMember("user_stranger", "group_1") } throws GroupNotFoundException("Group not found")

            Then("the real 404 from messaging's own IDOR check propagates, not a fabricated one") {
                try {
                    service.createSplitBill("user_stranger", "group_1", BigDecimal("1000"), "Dinner", listOf("user_a"))
                    error("expected GroupNotFoundException")
                } catch (e: GroupNotFoundException) {
                    // expected
                }
            }
        }

        When("a zero or negative total is submitted") {
            Then("it throws SplitBillInvalidAmountException before any lookup") {
                try {
                    service.createSplitBill("user_organizer", "group_1", BigDecimal.ZERO, "Dinner", listOf("user_a"))
                    error("expected SplitBillInvalidAmountException")
                } catch (e: SplitBillInvalidAmountException) {
                    // expected
                }
                verify(exactly = 0) { groupMessagingService.getGroupForMember(any(), any()) }
            }
        }

        When("no real other participants are named") {
            Then("it throws SplitBillNeedsParticipantsException") {
                try {
                    service.createSplitBill("user_organizer", "group_1", BigDecimal("1000"), "Dinner", emptyList())
                    error("expected SplitBillNeedsParticipantsException")
                } catch (e: SplitBillNeedsParticipantsException) {
                    // expected
                }
            }
        }
    }

    Given("a real open split bill with two real pending participant shares") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(
            splitBillRepository, splitBillParticipantRepository, walletRepository,
            transactionRepository, ledgerService, groupMessagingService, rateLimiter,
        )

        val splitBill = SplitBill(
            id = "splitbill_1", organizerId = "user_organizer", groupConversationId = "group_1",
            messageId = "group_message_1", totalAmount = BigDecimal("1000.00"), description = "Dinner",
        )
        val participantA = SplitBillParticipant(id = "sbp_a", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("500.00"))
        val participantB = SplitBillParticipant(id = "sbp_b", splitBillId = "splitbill_1", userId = "user_b", shareAmount = BigDecimal("500.00"))

        every { splitBillRepository.findById("splitbill_1") } returns Optional.of(splitBill)
        every { splitBillParticipantRepository.findBySplitBillIdAndUserId("splitbill_1", "user_a") } returns participantA
        every { splitBillParticipantRepository.findBySplitBillIdAndUserId("splitbill_1", "user_stranger") } returns null
        every { walletRepository.findByUserIdAndType("user_a", WalletType.MAIN) } returns wallet("wallet_a", "user_a", "10000")
        every { walletRepository.findByUserIdAndType("user_organizer", WalletType.MAIN) } returns wallet("wallet_organizer", "user_organizer", "0")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { splitBillParticipantRepository.save(any()) } answers { firstArg() }
        every { splitBillRepository.save(any()) } answers { firstArg() }

        When("a real named participant pays their own real share") {
            every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(
                SplitBillParticipant(id = "sbp_a", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("500.00"), status = SplitBillParticipantStatus.PAID),
                participantB,
            )

            val paid = service.payShare("user_a", "splitbill_1")

            Then("it moves the real share directly into the organizer's wallet and marks it PAID, but the bill stays OPEN since Bob hasn't paid yet") {
                paid.status shouldBe SplitBillParticipantStatus.PAID
                splitBill.status shouldBe SplitBillStatus.OPEN
            }
        }

        When("every real participant has now paid") {
            every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(
                SplitBillParticipant(id = "sbp_a", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("500.00"), status = SplitBillParticipantStatus.PAID),
                SplitBillParticipant(id = "sbp_b", splitBillId = "splitbill_1", userId = "user_b", shareAmount = BigDecimal("500.00"), status = SplitBillParticipantStatus.PAID),
            )

            service.payShare("user_a", "splitbill_1")

            Then("the split bill flips to SETTLED") {
                splitBill.status shouldBe SplitBillStatus.SETTLED
                (splitBill.settledAt != null) shouldBe true
            }
        }

        When("a non-participant (not organizer, not named) tries to pay into this split bill") {
            Then("it real-404s rather than revealing the split bill exists") {
                try {
                    service.payShare("user_stranger", "splitbill_1")
                    error("expected SplitBillNotFoundException")
                } catch (e: SplitBillNotFoundException) {
                    // expected
                }
            }
        }

        When("a participant who already paid tries to pay again") {
            every { splitBillParticipantRepository.findBySplitBillIdAndUserId("splitbill_1", "user_a") } returns
                SplitBillParticipant(id = "sbp_a", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("500.00"), status = SplitBillParticipantStatus.PAID)

            Then("it throws SplitBillAlreadyPaidException") {
                try {
                    service.payShare("user_a", "splitbill_1")
                    error("expected SplitBillAlreadyPaidException")
                } catch (e: SplitBillAlreadyPaidException) {
                    // expected
                }
            }
        }

        When("the payer doesn't have enough balance for their own share") {
            every { walletRepository.findByUserIdAndType("user_a", WalletType.MAIN) } returns wallet("wallet_a", "user_a", "10")

            Then("it throws InsufficientFundsException before ever moving real money") {
                try {
                    service.payShare("user_a", "splitbill_1")
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Real photo receipt attach (2026-07-28) -- see SplitBillService.attachReceipt's own
    // doc comment.
    Given("a real split bill and its real organizer") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(
            splitBillRepository, splitBillParticipantRepository, walletRepository,
            transactionRepository, ledgerService, groupMessagingService, rateLimiter,
        )

        val splitBill = SplitBill(
            id = "splitbill_1", organizerId = "user_organizer", groupConversationId = "group_1",
            messageId = "group_message_1", totalAmount = BigDecimal("1000.00"), description = "Dinner",
        )
        every { splitBillRepository.findById("splitbill_1") } returns Optional.of(splitBill)
        every { splitBillRepository.save(any()) } answers { firstArg() }

        When("the real organizer attaches a real receipt photo") {
            val result = service.attachReceipt("user_organizer", "splitbill_1", "https://uploads.example/receipt.jpg")

            Then("it real-saves the URL on the split bill") {
                result.receiptImageUrl shouldBe "https://uploads.example/receipt.jpg"
            }
        }

        When("someone who isn't the real organizer tries to attach a receipt") {
            Then("it real-404s rather than revealing the split bill exists") {
                try {
                    service.attachReceipt("user_stranger", "splitbill_1", "https://uploads.example/receipt.jpg")
                    error("expected SplitBillNotFoundException")
                } catch (e: SplitBillNotFoundException) {
                    // expected
                }
            }
        }

        When("the organizer submits a blank receipt URL") {
            Then("it throws SplitBillInvalidReceiptUrlException before ever touching the split bill row") {
                try {
                    service.attachReceipt("user_organizer", "splitbill_1", "   ")
                    error("expected SplitBillInvalidReceiptUrlException")
                } catch (e: SplitBillInvalidReceiptUrlException) {
                    verify(exactly = 0) { splitBillRepository.save(any()) }
                }
            }
        }

        When("a split bill that doesn't exist") {
            every { splitBillRepository.findById("splitbill_ghost") } returns Optional.empty()

            Then("it throws SplitBillNotFoundException") {
                try {
                    service.attachReceipt("user_organizer", "splitbill_ghost", "https://uploads.example/receipt.jpg")
                    error("expected SplitBillNotFoundException")
                } catch (e: SplitBillNotFoundException) {
                    // expected
                }
            }
        }
    }

    // Real up-to-5 settlement-round escalation (2026-07-28) -- see
    // SplitBillService.requestNextRound's own doc comment.
    Given("a real OPEN split bill with one real PENDING participant and its real organizer") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(
            splitBillRepository, splitBillParticipantRepository, walletRepository,
            transactionRepository, ledgerService, groupMessagingService, rateLimiter,
        )

        val splitBill = SplitBill(
            id = "splitbill_1", organizerId = "user_organizer", groupConversationId = "group_1",
            messageId = "group_message_1", totalAmount = BigDecimal("1000.00"), description = "Dinner",
        )
        val pendingParticipant = SplitBillParticipant(
            id = "participant_1", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("500.00"),
        )
        every { splitBillRepository.findById("splitbill_1") } returns Optional.of(splitBill)
        every { splitBillRepository.save(any()) } answers { firstArg() }
        every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(pendingParticipant)

        When("the real organizer requests the next settlement round") {
            val result = service.requestNextRound("user_organizer", "splitbill_1")

            Then("it real-increments the round counter and re-announces to the group") {
                result.currentRound shouldBe 2
                verify(exactly = 1) { groupMessagingService.sendMessage("user_organizer", "group_1", any()) }
            }
        }

        When("someone who isn't the real organizer tries to request the next round") {
            Then("it real-404s rather than revealing the split bill exists") {
                try {
                    service.requestNextRound("user_stranger", "splitbill_1")
                    error("expected SplitBillNotFoundException")
                } catch (e: SplitBillNotFoundException) {
                    // expected
                }
            }
        }

        When("every participant has already paid") {
            every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(
                SplitBillParticipant(
                    id = "participant_1", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("500.00"),
                    status = SplitBillParticipantStatus.PAID,
                ),
            )

            Then("it throws SplitBillNoPendingParticipantsException before touching the round counter") {
                try {
                    service.requestNextRound("user_organizer", "splitbill_1")
                    error("expected SplitBillNoPendingParticipantsException")
                } catch (e: SplitBillNoPendingParticipantsException) {
                    verify(exactly = 0) { splitBillRepository.save(any()) }
                }
            }
        }

        When("the split bill is already SETTLED") {
            val settledBill = SplitBill(
                id = "splitbill_settled", organizerId = "user_organizer", groupConversationId = "group_1",
                messageId = "group_message_1", totalAmount = BigDecimal("1000.00"), description = "Dinner",
                status = SplitBillStatus.SETTLED,
            )
            every { splitBillRepository.findById("splitbill_settled") } returns Optional.of(settledBill)

            Then("it throws SplitBillAlreadySettledException") {
                try {
                    service.requestNextRound("user_organizer", "splitbill_settled")
                    error("expected SplitBillAlreadySettledException")
                } catch (e: SplitBillAlreadySettledException) {
                    // expected
                }
            }
        }

        When("the split bill is already at the maximum of 5 rounds") {
            val maxedBill = SplitBill(
                id = "splitbill_maxed", organizerId = "user_organizer", groupConversationId = "group_1",
                messageId = "group_message_1", totalAmount = BigDecimal("1000.00"), description = "Dinner",
                currentRound = 5,
            )
            every { splitBillRepository.findById("splitbill_maxed") } returns Optional.of(maxedBill)
            every { splitBillParticipantRepository.findBySplitBillId("splitbill_maxed") } returns listOf(pendingParticipant)

            Then("it throws SplitBillMaxRoundsReachedException") {
                try {
                    service.requestNextRound("user_organizer", "splitbill_maxed")
                    error("expected SplitBillMaxRoundsReachedException")
                } catch (e: SplitBillMaxRoundsReachedException) {
                    verify(exactly = 0) { splitBillRepository.save(any()) }
                }
            }
        }
    }

    // Real scheduled reminder nudges (2026-07-27) -- see SplitBillReminderScheduler's
    // own doc comment.
    Given("a real OPEN split bill with a real never-yet-reminded PENDING participant") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(splitBillRepository, splitBillParticipantRepository, walletRepository, transactionRepository, ledgerService, groupMessagingService, rateLimiter)

        val bill = SplitBill(id = "splitbill_1", organizerId = "organizer_1", groupConversationId = "group_1", messageId = "msg_1", totalAmount = BigDecimal("3000"), description = "Dinner")
        val participant = SplitBillParticipant(id = "participant_1", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("1000"))
        every { splitBillRepository.findByStatus(SplitBillStatus.OPEN) } returns listOf(bill)
        every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(participant)
        val savedSlot = mutableListOf<SplitBillParticipant>()
        every { splitBillParticipantRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the real sweep runs") {
            val remindedCount = service.sendDueReminders()

            Then("it real-nudges the group chat as the organizer and stamps the real reminder timestamp") {
                remindedCount shouldBe 1
                verify(exactly = 1) { groupMessagingService.sendMessage("organizer_1", "group_1", any()) }
                (savedSlot.first().lastReminderSentAt != null) shouldBe true
            }
        }
    }

    Given("a real OPEN split bill whose PENDING participant was already real-reminded recently") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(splitBillRepository, splitBillParticipantRepository, walletRepository, transactionRepository, ledgerService, groupMessagingService, rateLimiter)

        val bill = SplitBill(id = "splitbill_1", organizerId = "organizer_1", groupConversationId = "group_1", messageId = "msg_1", totalAmount = BigDecimal("3000"), description = "Dinner")
        val participant = SplitBillParticipant(
            id = "participant_1", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("1000"),
            lastReminderSentAt = java.time.Instant.now().minusSeconds(3600),
        )
        every { splitBillRepository.findByStatus(SplitBillStatus.OPEN) } returns listOf(bill)
        every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(participant)

        When("the real sweep runs less than 24 real hours later") {
            val remindedCount = service.sendDueReminders()

            Then("it honestly skips this participant -- not a spammy repeat") {
                remindedCount shouldBe 0
                verify(exactly = 0) { groupMessagingService.sendMessage(any(), any(), any()) }
            }
        }
    }

    Given("a real OPEN split bill whose participant already PAID their share") {
        val splitBillRepository = mockk<SplitBillRepository>()
        val splitBillParticipantRepository = mockk<SplitBillParticipantRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = SplitBillService(splitBillRepository, splitBillParticipantRepository, walletRepository, transactionRepository, ledgerService, groupMessagingService, rateLimiter)

        val bill = SplitBill(id = "splitbill_1", organizerId = "organizer_1", groupConversationId = "group_1", messageId = "msg_1", totalAmount = BigDecimal("3000"), description = "Dinner")
        val paidParticipant = SplitBillParticipant(id = "participant_1", splitBillId = "splitbill_1", userId = "user_a", shareAmount = BigDecimal("1000"), status = SplitBillParticipantStatus.PAID)
        every { splitBillRepository.findByStatus(SplitBillStatus.OPEN) } returns listOf(bill)
        every { splitBillParticipantRepository.findBySplitBillId("splitbill_1") } returns listOf(paidParticipant)

        When("the real sweep runs") {
            val remindedCount = service.sendDueReminders()

            Then("it never nudges someone who already real-paid") {
                remindedCount shouldBe 0
                verify(exactly = 0) { groupMessagingService.sendMessage(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
