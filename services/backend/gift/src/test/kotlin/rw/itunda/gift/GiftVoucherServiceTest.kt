package rw.itunda.gift

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.domain.GiftVoucherStatus
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.GiftVoucherRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class GiftVoucherServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun user(id: String, phone: String) = User(id = id, phoneNumber = phone, firstName = "Test", lastName = "User", passwordHash = "hash")

    fun service(
        giftVoucherRepository: GiftVoucherRepository = mockk(),
        merchantRepository: MerchantRepository = mockk(),
        merchantProductRepository: MerchantProductRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        userRepository: UserRepository = mockk(),
        transactionRepository: TransactionRepository = mockk(relaxed = true),
        ledgerService: LedgerService = mockk(),
        messagingService: MessagingService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
        // Real default: no top-up needed, the account passed in already covers the
        // amount -- the short-balance test overrides this with its own explicit mock.
        autoTopUpService: rw.itunda.account.AutoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
            .also { every { it.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() } },
    ) = GiftVoucherService(
        giftVoucherRepository, merchantRepository, merchantProductRepository, accountRepository,
        userRepository, transactionRepository, ledgerService, messagingService, rateLimiter, fraudRuleEngine,
        autoTopUpService,
    )

    Given("a real purchaser buying a real product-tied gift voucher for a real recipient") {
        val giftVoucherRepository = mockk<GiftVoucherRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val svc = service(
            giftVoucherRepository, merchantRepository, merchantProductRepository, accountRepository, userRepository,
            transactionRepository, ledgerService, messagingService, rateLimiter, fraudRuleEngine,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Iced Latte", price = BigDecimal("3000"))
        val recipient = user("user_recipient", "+250788000002")
        val conversation = Conversation(id = "conversation_1", participantAId = "user_recipient", participantBId = "user_purchaser")
        val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_purchaser", body = "voucher")

        every { userRepository.findByPhoneNumber("+250788000002") } returns recipient
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
        every { accountRepository.findByUserIdAndType("user_purchaser", AccountType.PAY) } returns account("account_purchaser", "user_purchaser", "10000")
        every { messagingService.startOrGetConversation("user_purchaser", "user_recipient") } returns conversation
        every { messagingService.sendMessage(any(), any(), any()) } returns message
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { giftVoucherRepository.save(any()) } answers { firstArg() }

        When("purchasing the voucher") {
            val voucher = svc.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", "product_1", null)

            Then("it snapshots the real product price/name and creates a real ACTIVE voucher") {
                voucher.purchaserId shouldBe "user_purchaser"
                voucher.recipientId shouldBe "user_recipient"
                voucher.amount shouldBe BigDecimal("3000")
                voucher.productNameSnapshot shouldBe "Iced Latte"
                voucher.status shouldBe GiftVoucherStatus.ACTIVE
                verify(exactly = 1) { messagingService.sendMessage("user_purchaser", "conversation_1", any()) }
            }

            // Real gap found live (Gift product-completeness pass, 2026-09-08): every
            // rateLimiter/fraudRuleEngine mock in this file was relaxed = true with zero
            // verify{} anywhere, so a future accidental removal of either real call
            // would have compiled and passed silently.
            Then("the real rate limiter and real fraud engine are actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("giftvoucher:purchase:user_purchaser", limit = 20, window = java.time.Duration.ofHours(1)) }
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_purchaser", "user_recipient", BigDecimal("3000"), any()) }
            }
        }

        When("the purchaser doesn't have enough balance") {
            every { accountRepository.findByUserIdAndType("user_purchaser", AccountType.PAY) } returns account("account_purchaser", "user_purchaser", "100")

            Then("it throws InsufficientFundsException before ever moving real money") {
                try {
                    svc.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", "product_1", null)
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
            }
        }

        // Real Toss Bank/Toss Pay separation (2026-08-21) -- purchasing a gift voucher
        // is real merchant collection, same as MerchantService.collect()'s own QR path,
        // so it gets the same auto-topup-from-Bank-if-short treatment.
        When("the purchaser's itunda Pay money is short but auto top-up from Bank covers it") {
            val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
            val svcWithTopUp = service(giftVoucherRepository, merchantRepository, merchantProductRepository, accountRepository, userRepository, transactionRepository, ledgerService, messagingService, autoTopUpService = autoTopUpService)
            val shortAccount = account("account_purchaser", "user_purchaser", "1000")
            val toppedUpAccount = account("account_purchaser", "user_purchaser", "10000")
            every { accountRepository.findByUserIdAndType("user_purchaser", AccountType.PAY) } returns shortAccount
            every { autoTopUpService.ensureSufficientPayBalance("user_purchaser", shortAccount, BigDecimal("3000")) } returns toppedUpAccount

            val voucher = svcWithTopUp.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", "product_1", null)

            Then("it calls the shared top-up helper for exactly this voucher's real amount, then completes the purchase") {
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("user_purchaser", shortAccount, BigDecimal("3000")) }
                voucher.status shouldBe GiftVoucherStatus.ACTIVE
            }
        }

        When("the selected product is already sold out") {
            product.stockQuantity = 0

            Then("it refuses before placing a real escrow hold") {
                try {
                    svc.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", "product_1", null)
                    error("expected GiftVoucherProductUnavailableException")
                } catch (e: GiftVoucherProductUnavailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("trying to gift a voucher to yourself") {
            every { userRepository.findByPhoneNumber("+250788000002") } returns user("user_purchaser", "+250788000002")

            Then("it throws GiftVoucherSelfException") {
                try {
                    svc.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", "product_1", null)
                    error("expected GiftVoucherSelfException")
                } catch (e: GiftVoucherSelfException) {
                    // expected
                }
            }
        }
    }

    Given("a real purchaser buying a real flat-amount voucher (no specific product)") {
        val giftVoucherRepository = mockk<GiftVoucherRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>()
        val svc = service(giftVoucherRepository, merchantRepository, merchantProductRepository, accountRepository, userRepository, transactionRepository, ledgerService, messagingService)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val recipient = user("user_recipient", "+250788000002")
        val conversation = Conversation(id = "conversation_1", participantAId = "user_recipient", participantBId = "user_purchaser")
        val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_purchaser", body = "voucher")

        every { userRepository.findByPhoneNumber("+250788000002") } returns recipient
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { accountRepository.findByUserIdAndType("user_purchaser", AccountType.PAY) } returns account("account_purchaser", "user_purchaser", "10000")
        every { messagingService.startOrGetConversation("user_purchaser", "user_recipient") } returns conversation
        every { messagingService.sendMessage(any(), any(), any()) } returns message
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { giftVoucherRepository.save(any()) } answers { firstArg() }

        When("purchasing a flat 2000 RWF voucher with no product id") {
            val voucher = svc.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", null, BigDecimal("2000"))

            Then("it uses the real given amount with no product snapshot") {
                voucher.amount shouldBe BigDecimal("2000")
                voucher.productNameSnapshot shouldBe null
                voucher.merchantProductId shouldBe null
            }
        }

        When("purchasing a flat voucher with no amount given at all") {
            Then("it throws GiftVoucherInvalidAmountException") {
                try {
                    svc.purchaseVoucher("user_purchaser", "+250788000002", "merchant_1", null, null)
                    error("expected GiftVoucherInvalidAmountException")
                } catch (e: GiftVoucherInvalidAmountException) {
                    // expected
                }
            }
        }
    }

    Given("a real merchant redeeming a real ACTIVE gift voucher") {
        val giftVoucherRepository = mockk<GiftVoucherRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val svc = service(giftVoucherRepository = giftVoucherRepository, merchantRepository = merchantRepository, accountRepository = accountRepository, transactionRepository = transactionRepository, ledgerService = ledgerService, messagingService = messagingService, rateLimiter = rateLimiter, fraudRuleEngine = fraudRuleEngine)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val voucher = GiftVoucher(
            id = "giftvoucher_1", purchaserId = "user_purchaser", recipientId = "user_recipient",
            conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
            amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().plus(10, ChronoUnit.DAYS),
        )

        every { giftVoucherRepository.findByIdForUpdate("giftvoucher_1") } returns Optional.of(voucher)
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { accountRepository.findById("account_merchant") } returns Optional.of(account("account_merchant", "seller_1", "0"))
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("redeemtxn_1", emptyList())
        every { giftVoucherRepository.save(any()) } answers { firstArg() }

        When("the real merchant owner redeems it") {
            val result = svc.redeemVoucher("seller_1", "giftvoucher_1")

            Then("it marks REDEEMED and credits the merchant net of the real 1.5% fee") {
                result.status shouldBe GiftVoucherStatus.REDEEMED
                result.redeemTransactionId shouldBe "redeemtxn_1"
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_merchant" }.amount shouldBe BigDecimal("2955.00")
                legs.first { it.accountId == "fee_revenue" }.amount shouldBe BigDecimal("45.00")
            }

            Then("it real-posts the confirmation as the RECIPIENT's own message, not the merchant's -- the merchant is never a participant in that 1:1 conversation") {
                verify(exactly = 1) { messagingService.sendMessage("user_recipient", "conversation_1", any()) }
            }

            // Real gap found live (sibling-asymmetry check against GiftService.claimGift's
            // identical "release escrowed value" shape, 2026-09-13): redeemVoucher had no
            // rate limit at all despite the RateLimiter bean already being injected.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("giftvoucher:redeem:seller_1", limit = 30, window = java.time.Duration.ofHours(1)) }
            }

            // Real gap found live (2026-09-14, FraudRuleEngine-verify sweep): this
            // method mirrors MerchantService.collect()'s own doc comment, but never
            // copied collect()'s real fraudRuleEngine.evaluate call.
            Then("the real fraud engine is actually consulted, not just mocked away") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_recipient", "seller_1", BigDecimal("3000"), "redeemtxn_1") }
            }
        }

        When("someone who isn't the real merchant owner tries to redeem it") {
            Then("it throws GiftVoucherNotFoundException, not a 403 that would confirm the voucher exists") {
                try {
                    svc.redeemVoucher("stranger", "giftvoucher_1")
                    error("expected GiftVoucherNotFoundException")
                } catch (e: GiftVoucherNotFoundException) {
                    // expected
                }
            }
        }

        When("trying to redeem an already-redeemed voucher") {
            val redeemed = GiftVoucher(
                id = "giftvoucher_2", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("3000"), status = GiftVoucherStatus.REDEEMED, holdTransactionId = "ledgertxn_1",
                expiresAt = Instant.now().plus(10, ChronoUnit.DAYS),
            )
            every { giftVoucherRepository.findByIdForUpdate("giftvoucher_2") } returns Optional.of(redeemed)

            Then("it throws GiftVoucherNotActiveException") {
                try {
                    svc.redeemVoucher("seller_1", "giftvoucher_2")
                    error("expected GiftVoucherNotActiveException")
                } catch (e: GiftVoucherNotActiveException) {
                    // expected
                }
            }
        }

        When("trying to redeem a real expired voucher") {
            val expiredVoucher = GiftVoucher(
                id = "giftvoucher_3", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
            )
            every { giftVoucherRepository.findByIdForUpdate("giftvoucher_3") } returns Optional.of(expiredVoucher)

            Then("it throws GiftVoucherExpiredException") {
                try {
                    svc.redeemVoucher("seller_1", "giftvoucher_3")
                    error("expected GiftVoucherExpiredException")
                } catch (e: GiftVoucherExpiredException) {
                    // expected
                }
            }
        }
    }

    Given("a real voucher nearing its real expiry, extended once") {
        val giftVoucherRepository = mockk<GiftVoucherRepository>()
        val messagingService = mockk<MessagingService>(relaxed = true)
        val svc = service(giftVoucherRepository = giftVoucherRepository, messagingService = messagingService)

        every { giftVoucherRepository.save(any()) } answers { firstArg() }

        When("extending a voucher within the real 30-day window of its expiry") {
            val voucher = GiftVoucher(
                id = "giftvoucher_1", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().plus(10, ChronoUnit.DAYS),
            )
            every { giftVoucherRepository.findById("giftvoucher_1") } returns Optional.of(voucher)
            val originalExpiry = voucher.expiresAt

            val result = svc.extendExpiry("user_purchaser", "giftvoucher_1")

            Then("it real-adds exactly 90 days and marks it extended") {
                result.expiresAt shouldBe originalExpiry.plus(GiftVoucher.EXTENSION_AMOUNT)
                result.extended shouldBe true
            }
        }

        When("trying to extend a voucher that's still far from expiry (outside the real 30-day window)") {
            val voucher = GiftVoucher(
                id = "giftvoucher_2", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().plus(100, ChronoUnit.DAYS),
            )
            every { giftVoucherRepository.findById("giftvoucher_2") } returns Optional.of(voucher)

            Then("it throws GiftVoucherNotExtendableException") {
                try {
                    svc.extendExpiry("user_purchaser", "giftvoucher_2")
                    error("expected GiftVoucherNotExtendableException")
                } catch (e: GiftVoucherNotExtendableException) {
                    // expected
                }
            }
        }

        When("trying to extend a voucher that's already been extended once") {
            val voucher = GiftVoucher(
                id = "giftvoucher_3", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().plus(10, ChronoUnit.DAYS),
                extended = true,
            )
            every { giftVoucherRepository.findById("giftvoucher_3") } returns Optional.of(voucher)

            Then("it throws GiftVoucherAlreadyExtendedException -- only one real extension per voucher") {
                try {
                    svc.extendExpiry("user_purchaser", "giftvoucher_3")
                    error("expected GiftVoucherAlreadyExtendedException")
                } catch (e: GiftVoucherAlreadyExtendedException) {
                    // expected
                }
            }
        }
    }

    Given("a real unredeemed voucher past its real expiry") {
        val giftVoucherRepository = mockk<GiftVoucherRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val ledgerService = mockk<LedgerService>()
        val messagingService = mockk<MessagingService>(relaxed = true)
        val svc = service(giftVoucherRepository = giftVoucherRepository, accountRepository = accountRepository, transactionRepository = transactionRepository, ledgerService = ledgerService, messagingService = messagingService)

        val voucher = GiftVoucher(
            id = "giftvoucher_1", purchaserId = "user_purchaser", recipientId = "user_recipient",
            conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
            amount = BigDecimal("1000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
        )
        every { accountRepository.findByUserIdAndType("user_purchaser", AccountType.PAY) } returns account("account_purchaser", "user_purchaser", "0")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("refundtxn_1", emptyList())
        every { giftVoucherRepository.save(any()) } answers { firstArg() }

        When("the scheduler expires it") {
            svc.expireVoucher(voucher)

            Then("it real-refunds exactly the sourced 90% (900 RWF) and forfeits the other 10% (100 RWF) as real fee revenue") {
                voucher.status shouldBe GiftVoucherStatus.EXPIRED
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_purchaser" }.amount shouldBe BigDecimal("900.00")
                legs.first { it.accountId == "fee_revenue" }.amount shouldBe BigDecimal("100.00")
            }
        }

        When("expireVoucher is called on an already-resolved voucher") {
            val alreadyRedeemed = GiftVoucher(
                id = "giftvoucher_2", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("1000"), status = GiftVoucherStatus.REDEEMED, holdTransactionId = "ledgertxn_1",
                expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
            )

            Then("it real-no-ops, never double-refunding an already-settled voucher") {
                svc.expireVoucher(alreadyRedeemed)
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real ACTIVE voucher entering its real 7-day expiry-reminder window") {
        val giftVoucherRepository = mockk<GiftVoucherRepository>()
        val messagingService = mockk<MessagingService>(relaxed = true)
        val svc = service(giftVoucherRepository = giftVoucherRepository, messagingService = messagingService)

        val dueVoucher = GiftVoucher(
            id = "giftvoucher_due", purchaserId = "user_purchaser", recipientId = "user_recipient",
            conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
            amount = BigDecimal("1000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().plus(3, ChronoUnit.DAYS),
        )
        val notDueVoucher = GiftVoucher(
            id = "giftvoucher_not_due", purchaserId = "user_purchaser2", recipientId = "user_recipient2",
            conversationId = "conversation_2", messageId = "message_2", merchantId = "merchant_1",
            amount = BigDecimal("1000"), holdTransactionId = "ledgertxn_2", expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
        )

        When("getVouchersDueForExpiryReminder is called") {
            every { giftVoucherRepository.findByStatusAndExpiryReminderSentAtIsNull(GiftVoucherStatus.ACTIVE) } returns listOf(dueVoucher, notDueVoucher)

            Then("it real-returns only the voucher whose real expiresAt has entered the real 7-day window") {
                val due = svc.getVouchersDueForExpiryReminder()
                due.size shouldBe 1
                due.first().id shouldBe "giftvoucher_due"
            }
        }

        When("sendExpiryReminder is called for the due voucher") {
            every { giftVoucherRepository.findById("giftvoucher_due") } returns java.util.Optional.of(dueVoucher)
            every { giftVoucherRepository.save(any()) } answers { firstArg() }

            svc.sendExpiryReminder("giftvoucher_due")

            Then("it real-sends exactly one Talk message into the real existing conversation and marks the reminder sent") {
                verify(exactly = 1) { messagingService.sendMessage("user_purchaser", "conversation_1", any()) }
                dueVoucher.expiryReminderSentAt shouldNotBe null
            }
        }

        When("sendExpiryReminder is called again for an already-reminded voucher") {
            val alreadyReminded = GiftVoucher(
                id = "giftvoucher_reminded", purchaserId = "user_purchaser", recipientId = "user_recipient",
                conversationId = "conversation_1", messageId = "message_1", merchantId = "merchant_1",
                amount = BigDecimal("1000"), holdTransactionId = "ledgertxn_1", expiresAt = Instant.now().plus(3, ChronoUnit.DAYS),
                expiryReminderSentAt = Instant.now().minus(1, ChronoUnit.HOURS),
            )
            every { giftVoucherRepository.findById("giftvoucher_reminded") } returns java.util.Optional.of(alreadyReminded)

            Then("it real-no-ops, never double-notifying an already-reminded voucher") {
                svc.sendExpiryReminder("giftvoucher_reminded")
                verify(exactly = 0) { messagingService.sendMessage("user_purchaser", "conversation_1", any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
