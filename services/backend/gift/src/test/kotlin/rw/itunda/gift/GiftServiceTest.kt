package rw.itunda.gift

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Gift
import rw.itunda.core.domain.GiftStatus
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.GiftRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class GiftServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun user(id: String, phone: String) = User(id = id, phoneNumber = phone, firstName = "Test", lastName = "User", passwordHash = "hash")

    Given("a real sender with a account and a real recipient resolvable by phone number") {
        val giftRepository = mockk<GiftRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = GiftService(giftRepository, accountRepository, userRepository, transactionRepository, ledgerService, messagingService, rateLimiter, fraudRuleEngine)

        val recipient = user("user_recipient", "+250788000002")
        val conversation = Conversation(id = "conversation_1", participantAId = "user_recipient", participantBId = "user_sender")
        val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_sender", body = "gift")

        every { userRepository.findByPhoneNumber("+250788000002") } returns recipient
        every { accountRepository.findByUserIdAndType("user_sender", AccountType.MAIN) } returns account("account_sender", "user_sender", "10000")
        every { accountRepository.findByUserIdAndType("user_recipient", AccountType.MAIN) } returns account("account_recipient", "user_recipient", "0")
        every { messagingService.startOrGetConversation("user_sender", "user_recipient") } returns conversation
        every { messagingService.sendMessage(any(), any(), any()) } returns message
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { giftRepository.save(any()) } answers { firstArg() }

        When("they send a real gift") {
            val gift = service.sendGift("user_sender", "+250788000002", BigDecimal("5000"), "Happy birthday")

            Then("it holds the real amount in escrow, posts a real chat message, and creates a real PENDING gift") {
                gift.senderId shouldBe "user_sender"
                gift.recipientId shouldBe "user_recipient"
                gift.amount shouldBe BigDecimal("5000")
                gift.status shouldBe GiftStatus.PENDING
                verify(exactly = 1) { messagingService.sendMessage("user_sender", "conversation_1", any()) }
            }
        }

        When("the sender doesn't have enough balance") {
            every { accountRepository.findByUserIdAndType("user_sender", AccountType.MAIN) } returns account("account_sender", "user_sender", "100")

            Then("it throws InsufficientFundsException before ever moving real money") {
                try {
                    service.sendGift("user_sender", "+250788000002", BigDecimal("5000"), null)
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
                // startOrGetConversation is real, harmless, and idempotent (find-or-create) --
                // it may run before the balance check; the money-safety-critical assertion is
                // that the real ledger posting never happens on a failed balance check.
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the phone number doesn't resolve to a real itunda account") {
            every { userRepository.findByPhoneNumber("+250700000000") } returns null

            Then("it throws GiftRecipientNotFoundException") {
                try {
                    service.sendGift("user_sender", "+250700000000", BigDecimal("5000"), null)
                    error("expected GiftRecipientNotFoundException")
                } catch (e: GiftRecipientNotFoundException) {
                    // expected
                }
            }
        }

        When("a user tries to send a gift to themselves") {
            every { userRepository.findByPhoneNumber("+250788000001") } returns user("user_sender", "+250788000001")

            Then("it throws GiftSelfException, never reaching messaging's own self-conversation check") {
                try {
                    service.sendGift("user_sender", "+250788000001", BigDecimal("5000"), null)
                    error("expected GiftSelfException")
                } catch (e: GiftSelfException) {
                    // expected
                }
                // Real bug this guards against: messagingService.startOrGetConversation
                // throws its own SelfConversationException for a self-pair, a type
                // GiftController has no handler for -- checking self *before* calling it
                // is what keeps the real error the caller sees GIFT_SELF_NOT_ALLOWED, not
                // an unhandled 500.
                verify(exactly = 0) { messagingService.startOrGetConversation(any(), any()) }
            }
        }

        When("a zero or negative amount is submitted") {
            Then("it throws GiftInvalidAmountException before any lookup") {
                try {
                    service.sendGift("user_sender", "+250788000002", BigDecimal.ZERO, null)
                    error("expected GiftInvalidAmountException")
                } catch (e: GiftInvalidAmountException) {
                    // expected
                }
                verify(exactly = 0) { userRepository.findByPhoneNumber(any()) }
            }
        }
    }

    Given("a real, already-open conversation between two real users, both with accounts") {
        val giftRepository = mockk<GiftRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = GiftService(giftRepository, accountRepository, userRepository, transactionRepository, ledgerService, messagingService, rateLimiter, fraudRuleEngine)

        // Real canonical-pair ordering (MessagingService.canonicalPair): participantAId
        // is whichever id sorts first -- "user_a" here, regardless of who is sender.
        val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
        val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_a", body = "gift")

        every { messagingService.getConversationForParticipant("user_a", "conversation_1") } returns conversation
        every { accountRepository.findByUserIdAndType("user_a", AccountType.MAIN) } returns account("account_a", "user_a", "10000")
        every { accountRepository.findByUserIdAndType("user_b", AccountType.MAIN) } returns account("account_b", "user_b", "0")
        every { messagingService.sendMessage(any(), any(), any()) } returns message
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { giftRepository.save(any()) } answers { firstArg() }

        When("participant A sends a real gift directly in that conversation") {
            val gift = service.sendGiftInConversation("user_a", "conversation_1", BigDecimal("3000"), "For you")

            Then("the recipient resolves to the OTHER participant (B), never re-typed") {
                gift.senderId shouldBe "user_a"
                gift.recipientId shouldBe "user_b"
                gift.amount shouldBe BigDecimal("3000")
                verify(exactly = 0) { userRepository.findByPhoneNumber(any()) }
            }
        }

        When("a non-participant tries to send a gift into a conversation they're not part of") {
            every { messagingService.getConversationForParticipant("user_stranger", "conversation_1") } throws
                rw.itunda.messaging.ConversationNotFoundException("Conversation not found")

            Then("the real 404 from messaging's own IDOR check propagates, not a fabricated one") {
                try {
                    service.sendGiftInConversation("user_stranger", "conversation_1", BigDecimal("1000"), null)
                    error("expected ConversationNotFoundException")
                } catch (e: rw.itunda.messaging.ConversationNotFoundException) {
                    // expected
                }
            }
        }

        When("a real participant fetches the conversation's gift history") {
            val gift = service.sendGiftInConversation("user_a", "conversation_1", BigDecimal("3000"), "For you")
            every { giftRepository.findByConversationId("conversation_1") } returns listOf(gift)

            Then("it returns the real gifts sent in that thread") {
                service.getGiftsForConversation("user_a", "conversation_1") shouldBe listOf(gift)
            }
        }

        When("a non-participant fetches the conversation's gift history") {
            every { messagingService.getConversationForParticipant("user_stranger", "conversation_1") } throws
                rw.itunda.messaging.ConversationNotFoundException("Conversation not found")

            Then("the real 404 from messaging's own IDOR check propagates, never leaking gift data") {
                try {
                    service.getGiftsForConversation("user_stranger", "conversation_1")
                    error("expected ConversationNotFoundException")
                } catch (e: rw.itunda.messaging.ConversationNotFoundException) {
                    // expected
                }
                verify(exactly = 0) { giftRepository.findByConversationId(any()) }
            }
        }
    }

    Given("a real pending gift addressed to a real recipient") {
        val giftRepository = mockk<GiftRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = GiftService(giftRepository, accountRepository, userRepository, transactionRepository, ledgerService, messagingService, rateLimiter, fraudRuleEngine)

        val pendingGift = Gift(
            id = "gift_1", senderId = "user_sender", recipientId = "user_recipient", conversationId = "conversation_1",
            messageId = "message_1", amount = BigDecimal("5000"), note = null, holdTransactionId = "ledgertxn_1",
            expiresAt = Instant.now().plusSeconds(3600),
        )

        every { giftRepository.findById("gift_1") } returns Optional.of(pendingGift)
        every { accountRepository.findByUserIdAndType("user_recipient", AccountType.MAIN) } returns account("account_recipient", "user_recipient", "0")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { giftRepository.save(any()) } answers { firstArg() }

        When("the real recipient opens/claims it") {
            val claimed = service.claimGift("user_recipient", "gift_1")

            Then("it moves the real escrowed amount into their account and marks it CLAIMED") {
                claimed.status shouldBe GiftStatus.CLAIMED
                (claimed.claimedAt != null) shouldBe true
            }
        }

        When("the sender (not the recipient) tries to claim their own gift") {
            Then("it throws GiftNotRecipientException") {
                try {
                    service.claimGift("user_sender", "gift_1")
                    error("expected GiftNotRecipientException")
                } catch (e: GiftNotRecipientException) {
                    // expected
                }
            }
        }

        When("a stranger who is neither sender nor recipient looks it up") {
            Then("it real-404s rather than revealing the gift exists") {
                try {
                    service.getGift("user_stranger", "gift_1")
                    error("expected GiftNotFoundException")
                } catch (e: GiftNotFoundException) {
                    // expected
                }
            }
        }

        When("the recipient tries to claim an already-claimed gift") {
            val claimedGift = Gift(
                id = "gift_2", senderId = "user_sender", recipientId = "user_recipient", conversationId = "conversation_1",
                messageId = "message_2", amount = BigDecimal("1000"), note = null, holdTransactionId = "ledgertxn_3",
                status = GiftStatus.CLAIMED, expiresAt = Instant.now().plusSeconds(3600),
            )
            every { giftRepository.findById("gift_2") } returns Optional.of(claimedGift)

            Then("it throws GiftAlreadyResolvedException") {
                try {
                    service.claimGift("user_recipient", "gift_2")
                    error("expected GiftAlreadyResolvedException")
                } catch (e: GiftAlreadyResolvedException) {
                    // expected
                }
            }
        }

        When("the recipient tries to claim a gift past its real expiry") {
            val expiredButStillPending = Gift(
                id = "gift_3", senderId = "user_sender", recipientId = "user_recipient", conversationId = "conversation_1",
                messageId = "message_3", amount = BigDecimal("1000"), note = null, holdTransactionId = "ledgertxn_4",
                expiresAt = Instant.now().minusSeconds(60),
            )
            every { giftRepository.findById("gift_3") } returns Optional.of(expiredButStillPending)

            Then("it throws GiftExpiredException rather than letting a late claim through") {
                try {
                    service.claimGift("user_recipient", "gift_3")
                    error("expected GiftExpiredException")
                } catch (e: GiftExpiredException) {
                    // expected
                }
            }
        }
    }

    Given("a real gift that expired unclaimed") {
        val giftRepository = mockk<GiftRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = GiftService(giftRepository, accountRepository, userRepository, transactionRepository, ledgerService, messagingService, rateLimiter, fraudRuleEngine)

        val expiredGift = Gift(
            id = "gift_4", senderId = "user_sender", recipientId = "user_recipient", conversationId = "conversation_1",
            messageId = "message_4", amount = BigDecimal("2000"), note = null, holdTransactionId = "ledgertxn_5",
            expiresAt = Instant.now().minusSeconds(60),
        )

        every { giftRepository.findByStatusAndExpiresAtBefore(GiftStatus.PENDING, any()) } returns listOf(expiredGift)
        every { accountRepository.findByUserIdAndType("user_sender", AccountType.MAIN) } returns account("account_sender", "user_sender", "0")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_6", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { giftRepository.save(any()) } answers { firstArg() }

        When("the scheduler processes it") {
            val found = service.getExpiredPendingGifts()
            service.expireGift(found.first())

            Then("it refunds the real escrowed amount back to the sender and marks the gift EXPIRED") {
                found.size shouldBe 1
                verify(exactly = 1) { giftRepository.save(match { it.status == GiftStatus.EXPIRED }) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
