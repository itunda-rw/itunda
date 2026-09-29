package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Emoticon
import rw.itunda.core.domain.EmoticonAcquisitionSource
import rw.itunda.core.domain.EmoticonPack
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.User
import rw.itunda.core.domain.UserEmoticonPack
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EmoticonPackRepository
import rw.itunda.core.repository.EmoticonRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserEmoticonPackRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

/** First test coverage for the real KakaoTalk Emoticon Store equivalent. */
class EmoticonServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    val pack = EmoticonPack(id = "pack_1", title = "Sunny Days", artistName = "itunda Art", thumbnailUrl = "/uploads/thumb.png", price = BigDecimal("500"))

    Given("a user purchasing a real emoticon pack") {
        val emoticonPackRepository = mockk<EmoticonPackRepository>()
        val emoticonRepository = mockk<EmoticonRepository>()
        val userEmoticonPackRepository = mockk<UserEmoticonPackRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = EmoticonService(
            emoticonPackRepository, emoticonRepository, userEmoticonPackRepository, accountRepository,
            userRepository, notificationRepository, ledgerService, messagingService, groupMessagingService, rateLimiter,
            pushNotificationService, fraudRuleEngine,
        )

        When("purchasing a pack they don't already own") {
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_1", "pack_1") } returns null
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { userEmoticonPackRepository.save(any()) } answers { firstArg() }

            val result = service.purchasePack("user_1", "pack_1")

            Then("it real-debits the buyer's account and credits emoticon_revenue for the pack's exact price") {
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_1" }.amount shouldBe BigDecimal("500")
                legs.first { it.accountId == "emoticon_revenue" }.amount shouldBe BigDecimal("500")
            }
            Then("it real-records ownership as PURCHASED") {
                result.userId shouldBe "user_1"
                result.packId shouldBe "pack_1"
                result.source shouldBe EmoticonAcquisitionSource.PURCHASED
            }
            Then("it rate-limits the purchase, same convention every other real purchase endpoint carries") {
                verify(exactly = 1) { rateLimiter.checkLimit("emoticon:purchase:user_1", limit = 20, window = any()) }
            }
            // Real gap found (2026-09-13): real money movement with zero FraudRuleEngine
            // coverage before this fix -- CardChargeService/GiftService/P2pService all
            // already had it, this sibling service never did.
            Then("the real purchase is evaluated against the buyer's own fraud history") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_1", null, BigDecimal("500"), "ledgertxn_1") }
            }
        }

        When("purchasing a pack they already own") {
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_1", "pack_1") } returns
                UserEmoticonPack(id = "existing", userId = "user_1", packId = "pack_1", source = EmoticonAcquisitionSource.PURCHASED)

            Then("it throws EmoticonPackAlreadyOwnedException before touching the ledger") {
                try {
                    service.purchasePack("user_1", "pack_1")
                    error("expected EmoticonPackAlreadyOwnedException")
                } catch (e: EmoticonPackAlreadyOwnedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("purchasing a pack that doesn't exist") {
            every { emoticonPackRepository.findById("pack_ghost") } returns Optional.empty()

            Then("it throws EmoticonPackNotFoundException") {
                try {
                    service.purchasePack("user_1", "pack_ghost")
                    error("expected EmoticonPackNotFoundException")
                } catch (e: EmoticonPackNotFoundException) {
                    // expected
                }
            }
        }

        When("purchasing with no real account") {
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_2", "pack_1") } returns null
            every { accountRepository.findByUserIdAndType("user_2", AccountType.MAIN) } returns null

            Then("it throws EmoticonNoAccountException before touching the ledger") {
                try {
                    service.purchasePack("user_2", "pack_1")
                    error("expected EmoticonNoAccountException")
                } catch (e: EmoticonNoAccountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a user gifting a real emoticon pack to another user") {
        val emoticonPackRepository = mockk<EmoticonPackRepository>()
        val emoticonRepository = mockk<EmoticonRepository>()
        val userEmoticonPackRepository = mockk<UserEmoticonPackRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = EmoticonService(
            emoticonPackRepository, emoticonRepository, userEmoticonPackRepository, accountRepository,
            userRepository, notificationRepository, ledgerService, messagingService, groupMessagingService, rateLimiter,
            pushNotificationService, fraudRuleEngine,
        )

        val recipient = User(id = "user_recipient", phoneNumber = "+250788000002", firstName = "Recipient", lastName = "Test", passwordHash = "hash")

        When("gifting a pack the recipient doesn't already own") {
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userRepository.findByPhoneNumber("+250788000002") } returns recipient
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_recipient", "pack_1") } returns null
            every { accountRepository.findByUserIdAndType("user_giver", AccountType.MAIN) } returns account("account_giver", "user_giver")
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { userEmoticonPackRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.giftPack("user_giver", "+250788000002", "pack_1")

            Then("it real-debits the GIVER's account and credits emoticon_revenue for the pack's exact price") {
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_giver" }.amount shouldBe BigDecimal("500")
                legs.first { it.accountId == "emoticon_revenue" }.amount shouldBe BigDecimal("500")
            }
            Then("ownership real-lands on the RECIPIENT, not the giver, recorded as GIFTED") {
                result.userId shouldBe "user_recipient"
                result.packId shouldBe "pack_1"
                result.source shouldBe EmoticonAcquisitionSource.GIFTED
            }
            Then("it real-notifies the recipient of the gift") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_recipient" && it.type == "EMOTICON_PACK_GIFTED" }) }
            }

            Then("the recipient also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_recipient", "You received a gift!", any(), any()) }
            }
            // Real gap found (2026-09-13), same as purchasePack -- this gift DOES have
            // a real counterparty (the recipient), so recipientUserId is passed rather
            // than null, matching P2pService.sendDirect's own NEW_RECIPIENT-rule shape.
            Then("the real gift is evaluated against the giver's fraud history, with the recipient as the counterparty") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_giver", "user_recipient", BigDecimal("500"), "ledgertxn_2") }
            }
        }

        When("the recipient's phone number doesn't resolve to any real itunda account") {
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userRepository.findByPhoneNumber("+250788999999") } returns null

            Then("it throws EmoticonGiftRecipientNotFoundException, a real 404, before ever touching the ledger") {
                try {
                    service.giftPack("user_giver", "+250788999999", "pack_1")
                    error("expected EmoticonGiftRecipientNotFoundException")
                } catch (e: EmoticonGiftRecipientNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("trying to gift a pack to yourself") {
            val self = User(id = "user_giver", phoneNumber = "+250788000001", firstName = "Giver", lastName = "Test", passwordHash = "hash")
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userRepository.findByPhoneNumber("+250788000001") } returns self

            Then("it throws EmoticonGiftToSelfException before ever touching the ledger") {
                try {
                    service.giftPack("user_giver", "+250788000001", "pack_1")
                    error("expected EmoticonGiftToSelfException")
                } catch (e: EmoticonGiftToSelfException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the recipient already owns the pack") {
            every { emoticonPackRepository.findById("pack_1") } returns Optional.of(pack)
            every { userRepository.findByPhoneNumber("+250788000002") } returns recipient
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_recipient", "pack_1") } returns
                UserEmoticonPack(id = "existing", userId = "user_recipient", packId = "pack_1", source = EmoticonAcquisitionSource.PURCHASED)

            Then("it throws EmoticonPackAlreadyOwnedException before ever touching the ledger") {
                try {
                    service.giftPack("user_giver", "+250788000002", "pack_1")
                    error("expected EmoticonPackAlreadyOwnedException")
                } catch (e: EmoticonPackAlreadyOwnedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a user sending a real owned emoticon") {
        val emoticonPackRepository = mockk<EmoticonPackRepository>()
        val emoticonRepository = mockk<EmoticonRepository>()
        val userEmoticonPackRepository = mockk<UserEmoticonPackRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = EmoticonService(
            emoticonPackRepository, emoticonRepository, userEmoticonPackRepository, accountRepository,
            userRepository, notificationRepository, ledgerService, messagingService, groupMessagingService, rateLimiter,
            pushNotificationService, fraudRuleEngine,
        )

        val emoticon = Emoticon(id = "emoticon_1", packId = "pack_1", imageUrl = "/uploads/smile.png")

        When("sending it into a real 1:1 conversation") {
            every { emoticonRepository.findById("emoticon_1") } returns Optional.of(emoticon)
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_1", "pack_1") } returns
                UserEmoticonPack(id = "owned", userId = "user_1", packId = "pack_1", source = EmoticonAcquisitionSource.PURCHASED)
            val sentMessage = Message(id = "message_1", conversationId = "conv_1", senderId = "user_1", body = "😀 Emoticon", emoticonId = "emoticon_1")
            every { messagingService.sendMessage("user_1", "conv_1", "", emoticonId = "emoticon_1") } returns sentMessage

            val result = service.sendEmoticon("user_1", "conv_1", "emoticon_1")

            Then("it delegates to the real, already-tested MessagingService.sendMessage with the emoticon id set") {
                result.emoticonId shouldBe "emoticon_1"
                verify(exactly = 1) { messagingService.sendMessage("user_1", "conv_1", "", emoticonId = "emoticon_1") }
            }
        }

        When("sending it into a real group thread") {
            every { emoticonRepository.findById("emoticon_1") } returns Optional.of(emoticon)
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_1", "pack_1") } returns
                UserEmoticonPack(id = "owned", userId = "user_1", packId = "pack_1", source = EmoticonAcquisitionSource.PURCHASED)
            val sentGroupMessage = GroupMessage(id = "group_message_1", groupConversationId = "group_1", senderId = "user_1", body = "😀 Emoticon", emoticonId = "emoticon_1")
            every { groupMessagingService.sendMessage("user_1", "group_1", "", emoticonId = "emoticon_1") } returns sentGroupMessage

            val result = service.sendGroupEmoticon("user_1", "group_1", "emoticon_1")

            Then("it delegates to the real, already-tested GroupMessagingService.sendMessage with the emoticon id set") {
                result.emoticonId shouldBe "emoticon_1"
            }
        }

        When("trying to send an emoticon from a pack they don't own") {
            every { emoticonRepository.findById("emoticon_1") } returns Optional.of(emoticon)
            every { userEmoticonPackRepository.findByUserIdAndPackId("user_3", "pack_1") } returns null

            Then("it throws EmoticonPackNotOwnedException before ever touching MessagingService") {
                try {
                    service.sendEmoticon("user_3", "conv_1", "emoticon_1")
                    error("expected EmoticonPackNotOwnedException")
                } catch (e: EmoticonPackNotOwnedException) {
                    verify(exactly = 0) { messagingService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
                }
            }
        }

        When("trying to send an emoticon id that doesn't exist") {
            every { emoticonRepository.findById("emoticon_ghost") } returns Optional.empty()

            Then("it throws EmoticonNotFoundException") {
                try {
                    service.sendEmoticon("user_1", "conv_1", "emoticon_ghost")
                    error("expected EmoticonNotFoundException")
                } catch (e: EmoticonNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
