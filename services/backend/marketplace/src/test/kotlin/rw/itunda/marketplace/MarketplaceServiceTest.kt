package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.MarketplaceEscrow
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.domain.MarketplaceListingReport
import rw.itunda.core.domain.MarketplaceReportReason
import rw.itunda.core.repository.ListingLikeRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.MarketplaceEscrowRepository
import rw.itunda.core.repository.ListingFavoriteRepository
import rw.itunda.core.repository.MarketplaceListingReportRepository
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration
import java.util.Optional

class MarketplaceServiceTest : BehaviorSpec({

    Given("a seller listing a real item") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        When("creating a listing with valid fields") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            val listing = service.createListing("seller_1", "  Bicycle  ", "  Barely used  ", BigDecimal("15000"), "  sports  ")

            Then("it trims every text field and defaults to ACTIVE") {
                listing.title shouldBe "Bicycle"
                listing.description shouldBe "Barely used"
                listing.category shouldBe "sports"
                listing.status shouldBe ListingStatus.ACTIVE
            }
        }

        When("creating a listing with a zero or negative price") {
            Then("it throws InvalidListingException") {
                try {
                    service.createListing("seller_1", "Bike", "desc", BigDecimal.ZERO, "sports")
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }

        When("creating a listing with a blank title") {
            Then("it throws InvalidListingException") {
                try {
                    service.createListing("seller_1", "   ", "desc", BigDecimal("100"), "sports")
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }

        When("creating a listing with a title longer than the real 255-char DB column bound") {
            Then("it throws InvalidListingException rather than risking a raw DB insert failure") {
                try {
                    service.createListing("seller_1", "x".repeat(256), "desc", BigDecimal("100"), "sports")
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }
    }

    Given("an existing real listing") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )
        // getListing (called directly below, and internally by contactSeller) now also
        // real-increments the view count -- stub it here once for every When in this block.
        every { listingRepository.incrementViewCount(any()) } returns 1
        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "Bicycle", description = "desc",
            price = BigDecimal("15000"), category = "sports",
        )

        When("the real owner marks it sold") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { listingRepository.save(any()) } answers { firstArg() }

            val result = service.markSold("seller_1", "listing_1")

            Then("its status flips to SOLD") {
                result.status shouldBe ListingStatus.SOLD
            }
            Then("the real Karrot-Score-style trust badge is recomputed for the seller immediately") {
                io.mockk.verify(exactly = 1) { trustScoreService.computeScore("seller_1") }
            }
        }

        When("a buyer views the listing detail page") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { listingRepository.incrementViewCount("listing_1") } returns 1

            val result = service.getListing("listing_1")

            Then("it returns a real view count bumped by one, and atomically increments it in the database") {
                result.viewCount shouldBe 1
                io.mockk.verify(exactly = 1) { listingRepository.incrementViewCount("listing_1") }
            }
        }

        When("someone who doesn't own it tries to mark it sold") {
            val freshListing = Listing(
                id = "listing_2", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_2") } returns Optional.of(freshListing)

            Then("it throws ListingNotFoundException, not a 403 that would confirm the listing exists") {
                try {
                    service.markSold("stranger", "listing_2")
                    error("expected ListingNotFoundException")
                } catch (e: ListingNotFoundException) {
                    // expected
                }
            }
        }

        When("a real buyer contacts the seller") {
            val freshListing = Listing(
                id = "listing_3", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_3") } returns Optional.of(freshListing)
            val conversation = Conversation(id = "conversation_1", participantAId = "buyer_1", participantBId = "seller_1")
            every { messagingService.startOrGetConversation("buyer_1", "seller_1") } returns conversation

            val result = service.contactSeller("buyer_1", "listing_3")

            Then("it reuses the real MessagingService conversation, unmodified") {
                result.id shouldBe "conversation_1"
            }
        }

        When("the seller tries to contact themselves about their own listing") {
            val freshListing = Listing(
                id = "listing_4", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_4") } returns Optional.of(freshListing)
            every { messagingService.startOrGetConversation("seller_1", "seller_1") } throws
                SelfConversationException("Cannot start a conversation with yourself")

            Then("it throws OwnListingException, a real domain-specific error rather than leaking the messaging one") {
                try {
                    service.contactSeller("seller_1", "listing_4")
                    error("expected OwnListingException")
                } catch (e: OwnListingException) {
                    // expected
                }
            }
        }

        // Real bug found live (2026-08-02) -- see payEscrow's own doc comment: this
        // endpoint had no rate limit at all, unlike every other real "request a paid
        // service" creation method in this codebase.
        When("a real buyer exceeds the real pay-escrow rate limit") {
            val freshListing = Listing(
                id = "listing_5", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_5") } returns Optional.of(freshListing)
            every { rateLimiter.checkLimit("marketplace:pay-escrow:buyer_1", limit = 20, window = Duration.ofHours(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException before ever touching the ledger") {
                try {
                    service.payEscrow("buyer_1", "listing_5")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        // Real gap closed 2026-08-15 -- see MarketplaceEscrow.deliveryAddress's own doc
        // comment (당근마켓 바로구매-style shipped-item support, escrow previously only
        // ever assumed an in-person handoff). payEscrow's own happy path had no test at
        // all before this (the only existing payEscrow test above covers the rate-limit
        // failure path, never reaches the ledger).
        When("a real buyer pays escrow with a real delivery address for a shipped item") {
            val freshListing = Listing(
                id = "listing_6", sellerId = "seller_1", title = "Bike helmet", description = "desc",
                price = BigDecimal("5000"), category = "sports",
            )
            every { listingRepository.findById("listing_6") } returns Optional.of(freshListing)
            every { rateLimiter.checkLimit("marketplace:pay-escrow:buyer_2", limit = 20, window = Duration.ofHours(1)) } returns Unit
            val buyerWallet = Wallet(id = "wallet_buyer", userId = "buyer_2", accountNumber = "1", accountName = "Buyer", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"))
            val sellerWallet = Wallet(id = "wallet_seller", userId = "seller_1", accountNumber = "2", accountName = "Seller", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"))
            every { walletRepository.findByUserIdAndType("buyer_2", rw.itunda.core.domain.WalletType.MAIN) } returns buyerWallet
            every { walletRepository.findByUserIdAndType("seller_1", rw.itunda.core.domain.WalletType.MAIN) } returns sellerWallet
            val seller = User(id = "seller_1", phoneNumber = "0788000001", firstName = "Seller", lastName = "One", passwordHash = "x")
            every { userRepository.findById("seller_1") } returns Optional.of(seller)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns rw.itunda.core.ledger.LedgerPostResult("txn_1", emptyList())
            every { transactionRepository.save(any()) } answers { firstArg() }
            every { listingRepository.save(any()) } answers { firstArg() }
            val escrowSlot = slot<MarketplaceEscrow>()
            every { marketplaceEscrowRepository.save(capture(escrowSlot)) } answers { firstArg() }
            val conversation = Conversation(id = "conversation_2", participantAId = "buyer_2", participantBId = "seller_1")
            every { messagingService.startOrGetConversation("buyer_2", "seller_1") } returns conversation
            every { messagingService.sendMessage(any(), any(), any()) } returns mockk(relaxed = true)

            val escrow = service.payEscrow("buyer_2", "listing_6", "  123 Main St, Kigali  ")

            Then("the real delivery address is trimmed and persisted on the escrow") {
                escrow.deliveryAddress shouldBe "123 Main St, Kigali"
                escrowSlot.captured.deliveryAddress shouldBe "123 Main St, Kigali"
            }

            // Real gap closed 2026-08-17 -- see FraudRuleEngine's own doc comment:
            // marketplace-seller payments were a named, honestly-noted-but-unwired gap.
            Then("the real fraud engine is evaluated against the buyer and the real seller before the transaction is saved") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("buyer_2", "seller_1", BigDecimal("5000"), "txn_1") }
            }
        }

        When("a real buyer pays escrow with no delivery address (the original in-person case)") {
            val freshListing = Listing(
                id = "listing_7", sellerId = "seller_1", title = "Bike helmet", description = "desc",
                price = BigDecimal("5000"), category = "sports",
            )
            every { listingRepository.findById("listing_7") } returns Optional.of(freshListing)
            every { rateLimiter.checkLimit("marketplace:pay-escrow:buyer_3", limit = 20, window = Duration.ofHours(1)) } returns Unit
            val buyerWallet = Wallet(id = "wallet_buyer_3", userId = "buyer_3", accountNumber = "3", accountName = "Buyer", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"))
            val sellerWallet = Wallet(id = "wallet_seller_2", userId = "seller_1", accountNumber = "4", accountName = "Seller", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"))
            every { walletRepository.findByUserIdAndType("buyer_3", rw.itunda.core.domain.WalletType.MAIN) } returns buyerWallet
            every { walletRepository.findByUserIdAndType("seller_1", rw.itunda.core.domain.WalletType.MAIN) } returns sellerWallet
            val seller = User(id = "seller_1", phoneNumber = "0788000001", firstName = "Seller", lastName = "One", passwordHash = "x")
            every { userRepository.findById("seller_1") } returns Optional.of(seller)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns rw.itunda.core.ledger.LedgerPostResult("txn_2", emptyList())
            every { transactionRepository.save(any()) } answers { firstArg() }
            every { listingRepository.save(any()) } answers { firstArg() }
            every { marketplaceEscrowRepository.save(any()) } answers { firstArg() }
            val conversation = Conversation(id = "conversation_3", participantAId = "buyer_3", participantBId = "seller_1")
            every { messagingService.startOrGetConversation("buyer_3", "seller_1") } returns conversation
            every { messagingService.sendMessage(any(), any(), any()) } returns mockk(relaxed = true)

            val escrow = service.payEscrow("buyer_3", "listing_7", null)

            Then("deliveryAddress stays null, the original in-person flow is unaffected") {
                escrow.deliveryAddress shouldBe null
            }
        }

        // Real 당근마켓 끌어올리기 (bump to top of feed), 2026-08-10. Backdated
        // createdAt in both bump tests below -- a truly brand-new listing (createdAt =
        // now) is still inside its own 24h freshness window, so the cooldown correctly
        // blocks bumping it immediately too (it's already at the top; see
        // bumpListing's own doc comment). These tests are about an older listing that
        // genuinely wants a visibility refresh.
        When("the real owner bumps an older listing never bumped before") {
            val freshListing = Listing(
                id = "listing_6", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
                createdAt = java.time.Instant.now().minus(Duration.ofDays(2)),
            )
            every { listingRepository.findById("listing_6") } returns Optional.of(freshListing)
            every { listingRepository.save(any()) } answers { firstArg() }

            val before = java.time.Instant.now()
            val result = service.bumpListing("seller_1", "listing_6")

            Then("it sets bumpedAt to now") {
                val bumpedAt = result.bumpedAt ?: error("expected bumpedAt to be set")
                bumpedAt.isBefore(before).shouldBe(false)
            }
        }

        When("the real owner tries to bump the same listing again within the real 24h cooldown") {
            val recentlyBumped = Listing(
                id = "listing_7", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
                bumpedAt = java.time.Instant.now().minus(Duration.ofHours(1)),
            )
            every { listingRepository.findById("listing_7") } returns Optional.of(recentlyBumped)

            Then("it throws ListingBumpCooldownException before ever saving") {
                try {
                    service.bumpListing("seller_1", "listing_7")
                    error("expected ListingBumpCooldownException")
                } catch (e: ListingBumpCooldownException) {
                    io.mockk.verify(exactly = 0) { listingRepository.save(any()) }
                }
            }
        }

        When("a stranger tries to bump someone else's listing") {
            val freshListing = Listing(
                id = "listing_8", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_8") } returns Optional.of(freshListing)

            Then("it throws ListingNotFoundException, not a 403 that would confirm the listing exists") {
                try {
                    service.bumpListing("stranger", "listing_8")
                    error("expected ListingNotFoundException")
                } catch (e: ListingNotFoundException) {
                    // expected
                }
            }
        }

        When("a real buyer reports a listing for the first time") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { marketplaceListingReportRepository.findByListingIdAndReporterId("listing_1", "buyer_1") } returns null
            every { marketplaceListingReportRepository.save(any()) } answers { firstArg() }
            every { marketplaceListingReportRepository.countByListingId("listing_1") } returns 1

            val report = service.reportListing("buyer_1", "listing_1", MarketplaceReportReason.SCAM, "  looks fake  ")

            Then("it saves a real report with trimmed details and leaves the listing ACTIVE") {
                report.listingId shouldBe "listing_1"
                report.reporterId shouldBe "buyer_1"
                report.reason shouldBe MarketplaceReportReason.SCAM
                report.details shouldBe "looks fake"
                listing.status shouldBe ListingStatus.ACTIVE
                io.mockk.verify(exactly = 0) { listingRepository.save(any()) }
            }
        }

        When("the seller tries to report their own listing") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)

            Then("it throws OwnListingReportException before ever touching the report repository") {
                try {
                    service.reportListing("seller_1", "listing_1", MarketplaceReportReason.OTHER, null)
                    error("expected OwnListingReportException")
                } catch (e: OwnListingReportException) {
                    io.mockk.verify(exactly = 0) { marketplaceListingReportRepository.save(any()) }
                }
            }
        }

        When("the same buyer tries to report the same listing twice") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { marketplaceListingReportRepository.findByListingIdAndReporterId("listing_1", "buyer_1") } returns
                MarketplaceListingReport(id = "listing_report_existing", listingId = "listing_1", reporterId = "buyer_1", reason = MarketplaceReportReason.SPAM_OR_DUPLICATE)

            Then("it throws ListingAlreadyReportedException, never a second real row") {
                try {
                    service.reportListing("buyer_1", "listing_1", MarketplaceReportReason.SCAM, null)
                    error("expected ListingAlreadyReportedException")
                } catch (e: ListingAlreadyReportedException) {
                    io.mockk.verify(exactly = 0) { marketplaceListingReportRepository.save(any()) }
                }
            }
        }

        When("a listing's real distinct-reporter count reaches the real threshold") {
            val threatenedListing = Listing(
                id = "listing_9", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_9") } returns Optional.of(threatenedListing)
            every { marketplaceListingReportRepository.findByListingIdAndReporterId("listing_9", "buyer_3") } returns null
            every { marketplaceListingReportRepository.save(any()) } answers { firstArg() }
            every { marketplaceListingReportRepository.countByListingId("listing_9") } returns 3
            every { listingRepository.save(any()) } answers { firstArg() }

            service.reportListing("buyer_3", "listing_9", MarketplaceReportReason.PROHIBITED_ITEM, null)

            Then("the listing is silently auto-hidden -- real status REMOVED, no notification sent") {
                threatenedListing.status shouldBe ListingStatus.REMOVED
                io.mockk.verify(exactly = 1) { listingRepository.save(threatenedListing) }
            }
        }

        When("the real owner drops the price on a listing 2 real favoriters have saved") {
            val freshListing = Listing(
                id = "listing_10", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_10") } returns Optional.of(freshListing)
            every { listingRepository.save(any()) } answers { firstArg() }
            every { listingFavoriteRepository.findByListingId("listing_10") } returns listOf(
                rw.itunda.core.domain.ListingFavorite(id = "listing_favorite_1", userId = "favoriter_1", listingId = "listing_10"),
                rw.itunda.core.domain.ListingFavorite(id = "listing_favorite_2", userId = "favoriter_2", listingId = "listing_10"),
            )
            val notifSlot = mutableListOf<rw.itunda.core.domain.Notification>()
            every { notificationRepository.save(capture(notifSlot)) } answers { firstArg() }

            val result = service.updatePrice("seller_1", "listing_10", BigDecimal("12000"))

            Then("it saves the real new price and notifies exactly the 2 real favoriters, not the seller") {
                result.price shouldBe BigDecimal("12000")
                notifSlot.map { it.userId } shouldBe listOf("favoriter_1", "favoriter_2")
                notifSlot.forEach {
                    it.type shouldBe "LISTING_PRICE_DROP"
                    it.body shouldBe "\"Bicycle\" dropped from 15000 to 12000 RWF"
                }
                verify(exactly = 1) { pushNotificationService.sendToUser("favoriter_1", any(), any(), any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("favoriter_2", any(), any(), any()) }
            }
        }

        When("the real owner raises the price on a favorited listing") {
            val freshListing = Listing(
                id = "listing_11", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_11") } returns Optional.of(freshListing)
            every { listingRepository.save(any()) } answers { firstArg() }

            service.updatePrice("seller_1", "listing_11", BigDecimal("18000"))

            Then("no real favoriter is ever looked up, let alone notified -- only a real decrease qualifies") {
                io.mockk.verify(exactly = 0) { listingFavoriteRepository.findByListingId(any()) }
                io.mockk.verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("a stranger tries to change someone else's listing price") {
            val freshListing = Listing(
                id = "listing_12", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_12") } returns Optional.of(freshListing)

            Then("it throws ListingNotFoundException, not a 403 that would confirm the listing exists") {
                try {
                    service.updatePrice("stranger", "listing_12", BigDecimal("10000"))
                    error("expected ListingNotFoundException")
                } catch (e: ListingNotFoundException) {
                    io.mockk.verify(exactly = 0) { listingRepository.save(any()) }
                }
            }
        }

        When("the real owner tries to set a non-positive price") {
            Then("it throws InvalidListingPriceException before ever touching the repository") {
                try {
                    service.updatePrice("seller_1", "listing_1", BigDecimal.ZERO)
                    error("expected InvalidListingPriceException")
                } catch (e: InvalidListingPriceException) {
                    io.mockk.verify(exactly = 0) { listingRepository.findById(any()) }
                }
            }
        }

        When("the real owner tries to change the price of an already-SOLD listing") {
            val soldListing = Listing(
                id = "listing_13", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports", status = ListingStatus.SOLD,
            )
            every { listingRepository.findById("listing_13") } returns Optional.of(soldListing)

            Then("it throws ListingNotActiveException before ever saving") {
                try {
                    service.updatePrice("seller_1", "listing_13", BigDecimal("10000"))
                    error("expected ListingNotActiveException")
                } catch (e: ListingNotActiveException) {
                    io.mockk.verify(exactly = 0) { listingRepository.save(any()) }
                }
            }
        }
    }

    Given("browsing the real marketplace") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        When("no category filter is given") {
            every { listingRepository.findByStatusOrderByBoostedThenCreatedAtDesc(ListingStatus.ACTIVE, any(), any()) } returns
                mockk(relaxed = true)

            service.browse(PageRequest.of(0, 20), null)

            Then("it queries the unfiltered ACTIVE listing method, not the category one") {
                io.mockk.verify { listingRepository.findByStatusOrderByBoostedThenCreatedAtDesc(ListingStatus.ACTIVE, any(), any()) }
            }
        }
    }

    Given("a seller listing a real item with a real location") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        When("only one of latitude/longitude is given") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.createListing("seller_1", "Bike", "desc", BigDecimal("100"), "sports", latitude = -1.9441)
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("an out-of-range coordinate is given") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.createListing("seller_1", "Bike", "desc", BigDecimal("100"), "sports", latitude = 999.0, longitude = 30.0)
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("a real valid location is given") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.createListing("seller_1", "Bike", "desc", BigDecimal("100"), "sports", latitude = -1.9441, longitude = 30.0619)

            Then("it's saved on the real listing") {
                savedSlot.captured.latitude shouldBe -1.9441
                savedSlot.captured.longitude shouldBe 30.0619
            }
        }
    }

    Given("real listings at different real distances from a searcher") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        // Searcher at (-1.9441, 30.0619). Same longitude as both listings, only latitude
        // differs, so a real Haversine distance along a meridian is exact:
        // 6371km * (latitude difference in radians).
        val near = Listing(
            id = "listing_near", sellerId = "seller_1", title = "Near", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -1.9541, longitude = 30.0619, // ~1.11km away
        )
        val far = Listing(
            id = "listing_far", sellerId = "seller_1", title = "Far", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -2.9441, longitude = 30.0619, // ~111.2km away
        )
        // Deliberately returned out of distance order -- proves the service does the
        // real sorting, not just passing through whatever order the repository gave it.
        every { listingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ListingStatus.ACTIVE) } returns listOf(far, near)

        When("searching within a real 5km radius") {
            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("only the real near listing is returned") {
                page.content.map { it.id } shouldBe listOf("listing_near")
            }
        }

        When("searching within a real 200km radius") {
            val page = service.nearby(-1.9441, 30.0619, 200.0, PageRequest.of(0, 20))

            Then("both real listings are returned, closest first") {
                page.content.map { it.id } shouldBe listOf("listing_near", "listing_far")
            }
        }

        When("searching with an out-of-range coordinate") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.nearby(999.0, 30.0, 5.0, PageRequest.of(0, 20))
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("searching with a zero radius") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.nearby(-1.9441, 30.0619, 0.0, PageRequest.of(0, 20))
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }
    }

    Given("real OSRM road-distance ranking for Marketplace proximity search") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        // Both within Rwanda's bounding envelope, both within a real 5km straight-line
        // radius of the searcher -- Haversine says listingA is closer.
        val listingA = Listing(
            id = "listing_a", sellerId = "seller_1", title = "A", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -1.9541, longitude = 30.0619, // ~1.11km Haversine
        )
        val listingB = Listing(
            id = "listing_b", sellerId = "seller_1", title = "B", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -1.9641, longitude = 30.0619, // ~2.22km Haversine
        )
        every { listingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ListingStatus.ACTIVE) } returns
            listOf(listingA, listingB)

        When("OSRM is configured and returns a real road distance that reorders the Haversine ranking") {
            every { osrmRoutingClient.isConfigured } returns true
            // Road distance flips the order: B is closer by road than A, despite being
            // farther by straight line.
            every {
                osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-1.9541 to 30.0619, -1.9641 to 30.0619))
            } returns listOf(4.0, 1.0)

            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("real road distance, not straight-line distance, decides the order") {
                page.content.map { it.id } shouldBe listOf("listing_b", "listing_a")
            }
        }

        When("OSRM's real road distance pushes a Haversine-in-range listing outside the search radius") {
            every { osrmRoutingClient.isConfigured } returns true
            every {
                osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-1.9541 to 30.0619, -1.9641 to 30.0619))
            } returns listOf(1.0, 6.0)

            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("it's excluded even though it passed the Haversine pre-filter") {
                page.content.map { it.id } shouldBe listOf("listing_a")
            }
        }

        When("OSRM has no route for one candidate") {
            every { osrmRoutingClient.isConfigured } returns true
            every {
                osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-1.9541 to 30.0619, -1.9641 to 30.0619))
            } returns listOf(null, 2.5)

            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("that one candidate honestly falls back to its own Haversine distance, not a fabricated value") {
                // listingA's Haversine (~1.11km) still beats listingB's real road distance (2.5km).
                page.content.map { it.id } shouldBe listOf("listing_a", "listing_b")
            }
        }

        When("the searcher's own coordinate is outside Rwanda's bounding envelope") {
            every { osrmRoutingClient.isConfigured } returns true

            val page = service.nearby(0.0, 30.0, 500.0, PageRequest.of(0, 20))

            Then("OSRM is never consulted -- ranking falls straight back to Haversine, matching EatsOrderService's own guard against OSRM silently snapping an out-of-Rwanda point") {
                io.mockk.verify(exactly = 0) { osrmRoutingClient.routeDistancesKm(any(), any(), any()) }
                page.content.map { it.id } shouldBe listOf("listing_a", "listing_b")
            }
        }
    }

    Given("a real seller listing an item with real coordinates") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        When("the real coordinates reverse-geocode to a real neighborhood") {
            val savedSlot = slot<Listing>()
            every { nominatimGeocodingClient.reverseGeocode(-1.9536, 30.0605) } returns "Nyarugenge"
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.createListing("seller_1", "Sofa", "Real leather sofa", BigDecimal("50000"), "furniture", -1.9536, 30.0605)

            Then("the real neighborhood is cached on the listing at creation time, not recomputed later") {
                savedSlot.captured.neighborhood shouldBe "Nyarugenge"
            }
        }

        When("no real coordinates are given") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.createListing("seller_1", "Sofa", "Real leather sofa", BigDecimal("50000"), "furniture")

            Then("neighborhood stays null -- never a fabricated guess, and geocoding is never even called") {
                savedSlot.captured.neighborhood shouldBe null
                io.mockk.verify(exactly = 0) { nominatimGeocodingClient.reverseGeocode(any(), any()) }
            }
        }
    }

    Given("a real caller browsing their own real neighborhood") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        When("the caller has a real neighborhood set, no category filter") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = "Kimironko")
            val expectedPage = PageImpl(listOf(mockk<Listing>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every { listingRepository.findByStatusAndNeighborhoodInOrderByCreatedAtDesc(ListingStatus.ACTIVE, listOf("Kimironko"), any()) } returns expectedPage

            val page = service.myNeighborhood("user_1", null, PageRequest.of(0, 20))

            Then("it real-filters to exactly that neighborhood") {
                page shouldBe expectedPage
            }
        }

        When("the caller has a real neighborhood set, combined with a category filter") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = "Kimironko")
            val expectedPage = PageImpl(listOf(mockk<Listing>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every {
                listingRepository.findByStatusAndNeighborhoodInAndCategoryOrderByCreatedAtDesc(ListingStatus.ACTIVE, listOf("Kimironko"), "furniture", any())
            } returns expectedPage

            val page = service.myNeighborhood("user_1", "furniture", PageRequest.of(0, 20))

            Then("neighborhood and category combine, matching the established combinable-filter shape") {
                page shouldBe expectedPage
            }
        }

        When("the caller hasn't set a real neighborhood yet") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = null)
            every { userRepository.findById("user_1") } returns Optional.of(caller)

            Then("it throws NeighborhoodNotSetException rather than silently returning an empty page") {
                try {
                    service.myNeighborhood("user_1", null, PageRequest.of(0, 20))
                    error("expected NeighborhoodNotSetException")
                } catch (e: NeighborhoodNotSetException) {
                    // expected
                }
            }
        }
    }

    // Real scheduled escrow auto-release (2026-07-27) -- see
    // MarketplaceEscrow.AUTO_RELEASE_TIMEOUT's own doc comment for the full sourced
    // account, closing this feature's own previously-named deferred follow-up.
    Given("real escrows of every real age and status, checking which are due for real auto-release") {
        val listingRepository = mockk<ListingRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>()
        val listingLikeRepository = mockk<ListingLikeRepository>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        val overdue = MarketplaceEscrow(
            id = "escrow_a", listingId = "listing_a", buyerId = "buyer_a", sellerId = "seller_a",
            amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_a",
            createdAt = java.time.Instant.now().minus(java.time.Duration.ofDays(8)),
        )
        val notYetDue = MarketplaceEscrow(
            id = "escrow_b", listingId = "listing_b", buyerId = "buyer_b", sellerId = "seller_b",
            amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_b",
            createdAt = java.time.Instant.now().minus(java.time.Duration.ofDays(2)),
        )
        every { marketplaceEscrowRepository.findByStatus(rw.itunda.core.domain.MarketplaceEscrowStatus.HELD) } returns listOf(overdue, notYetDue)

        When("getEscrowsDueForAutoRelease runs") {
            val due = service.getEscrowsDueForAutoRelease()

            Then("it real-includes only the escrow past the real 7-day window, honestly excluding the too-recent one") {
                due shouldBe listOf(overdue)
            }
        }
    }

    Given("a real still-HELD escrow past the real auto-release window") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>()
        val listingLikeRepository = mockk<ListingLikeRepository>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        val escrow = MarketplaceEscrow(
            id = "escrow_c", listingId = "listing_c", buyerId = "buyer_c", sellerId = "seller_c",
            amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_c",
            createdAt = java.time.Instant.now().minus(java.time.Duration.ofDays(8)),
        )
        val sellerWallet = Wallet(id = "wallet_seller", userId = "seller_c", accountNumber = "ACC-S", accountName = "Seller", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO)
        every { marketplaceEscrowRepository.findById("escrow_c") } returns java.util.Optional.of(escrow)
        every { walletRepository.findByUserIdAndType("seller_c", rw.itunda.core.domain.WalletType.MAIN) } returns sellerWallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns rw.itunda.core.ledger.LedgerPostResult("ledgertxn_release_1", emptyList())
        every { marketplaceEscrowRepository.save(any()) } answers { firstArg() }
        every { listingRepository.findById("listing_c") } returns java.util.Optional.empty()
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix
        // used elsewhere in this codebase for this exact pitfall.
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("autoReleaseEscrow runs") {
            service.autoReleaseEscrow("escrow_c")

            Then("it real-releases to the seller, the same real path a manual buyer confirmation already uses") {
                escrow.status shouldBe rw.itunda.core.domain.MarketplaceEscrowStatus.RELEASED
                escrow.resolutionTransactionId shouldBe "ledgertxn_release_1"
            }
        }
    }

    Given("an escrow that's already been resolved by the time the real auto-release sweep reaches it") {
        val listingRepository = mockk<ListingRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>()
        val listingLikeRepository = mockk<ListingLikeRepository>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        val alreadyReleased = MarketplaceEscrow(
            id = "escrow_d", listingId = "listing_d", buyerId = "buyer_d", sellerId = "seller_d",
            amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_d",
            status = rw.itunda.core.domain.MarketplaceEscrowStatus.RELEASED,
        )
        every { marketplaceEscrowRepository.findById("escrow_d") } returns java.util.Optional.of(alreadyReleased)

        When("autoReleaseEscrow runs anyway (e.g. the buyer confirmed just before the sweep caught it)") {
            service.autoReleaseEscrow("escrow_d")

            Then("it's a real honest no-op -- never double-releasing or touching the ledger a second time") {
                io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real DISPUTED escrow an admin resolves in the seller's favor") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>()
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        val escrow = MarketplaceEscrow(
            id = "escrow_e", listingId = "listing_e", buyerId = "buyer_e", sellerId = "seller_e",
            amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_e",
            status = rw.itunda.core.domain.MarketplaceEscrowStatus.DISPUTED,
        )
        val listing = Listing(id = "listing_e", sellerId = "seller_e", title = "Sofa", description = "desc", price = BigDecimal("10000"), category = "furniture")
        val sellerWallet = Wallet(id = "wallet_seller_e", userId = "seller_e", accountNumber = "ACC-SE", accountName = "Seller", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO)
        every { marketplaceEscrowRepository.findById("escrow_e") } returns java.util.Optional.of(escrow)
        every { walletRepository.findByUserIdAndType("seller_e", rw.itunda.core.domain.WalletType.MAIN) } returns sellerWallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns rw.itunda.core.ledger.LedgerPostResult("ledgertxn_release_e", emptyList())
        every { marketplaceEscrowRepository.save(any()) } answers { firstArg() }
        every { listingRepository.findById("listing_e") } returns java.util.Optional.of(listing)
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("resolveDispute is called with release=true") {
            val result = service.resolveDispute("escrow_e", release = true)

            Then("it releases the real payout to the seller and notifies both real parties") {
                result.status shouldBe rw.itunda.core.domain.MarketplaceEscrowStatus.RELEASED
                result.resolutionTransactionId shouldBe "ledgertxn_release_e"

                val notifSlots = mutableListOf<rw.itunda.core.domain.Notification>()
                verify(exactly = 2) { notificationRepository.save(capture(notifSlots)) }
                val sellerNotif = notifSlots.single { it.userId == "seller_e" }
                sellerNotif.type shouldBe "MARKETPLACE_DISPUTE_RESOLVED"
                sellerNotif.body shouldBe "The dispute for \"Sofa\" was resolved in your favor. 9850 RWF has been credited to your wallet."
                val buyerNotif = notifSlots.single { it.userId == "buyer_e" }
                buyerNotif.type shouldBe "MARKETPLACE_DISPUTE_RESOLVED"
                buyerNotif.body shouldBe "The dispute for \"Sofa\" was resolved in the seller's favor. The payment has been released to them."

                verify(exactly = 1) { pushNotificationService.sendToUser("seller_e", any(), any(), any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("buyer_e", any(), any(), any()) }
            }
        }
    }

    Given("a real DISPUTED escrow an admin resolves in the buyer's favor") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>()
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val marketplaceListingReportRepository = mockk<MarketplaceListingReportRepository>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            walletRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            marketplaceListingReportRepository, listingFavoriteRepository, notificationRepository, pushNotificationService,
        )

        val escrow = MarketplaceEscrow(
            id = "escrow_f", listingId = "listing_f", buyerId = "buyer_f", sellerId = "seller_f",
            amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_f",
            status = rw.itunda.core.domain.MarketplaceEscrowStatus.DISPUTED,
        )
        val listing = Listing(
            id = "listing_f", sellerId = "seller_f", title = "Lamp", description = "desc", price = BigDecimal("10000"), category = "home",
            status = ListingStatus.SOLD, buyerId = "buyer_f",
        )
        val buyerWallet = Wallet(id = "wallet_buyer_f", userId = "buyer_f", accountNumber = "ACC-BF", accountName = "Buyer", type = rw.itunda.core.domain.WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO)
        every { marketplaceEscrowRepository.findById("escrow_f") } returns java.util.Optional.of(escrow)
        every { walletRepository.findByUserIdAndType("buyer_f", rw.itunda.core.domain.WalletType.MAIN) } returns buyerWallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns rw.itunda.core.ledger.LedgerPostResult("ledgertxn_refund_f", emptyList())
        every { marketplaceEscrowRepository.save(any()) } answers { firstArg() }
        every { listingRepository.findById("listing_f") } returns java.util.Optional.of(listing)
        every { listingRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("resolveDispute is called with release=false") {
            val result = service.resolveDispute("escrow_f", release = false)

            Then("it refunds the real buyer, reopens the listing, and notifies both real parties") {
                result.status shouldBe rw.itunda.core.domain.MarketplaceEscrowStatus.REFUNDED
                result.resolutionTransactionId shouldBe "ledgertxn_refund_f"
                listing.status shouldBe ListingStatus.ACTIVE
                listing.buyerId shouldBe null

                val notifSlots = mutableListOf<rw.itunda.core.domain.Notification>()
                verify(exactly = 2) { notificationRepository.save(capture(notifSlots)) }
                val buyerNotif = notifSlots.single { it.userId == "buyer_f" }
                buyerNotif.type shouldBe "MARKETPLACE_DISPUTE_RESOLVED"
                buyerNotif.body shouldBe "The dispute for \"Lamp\" was resolved in your favor. 10000 RWF has been refunded to your wallet."
                val sellerNotif = notifSlots.single { it.userId == "seller_f" }
                sellerNotif.type shouldBe "MARKETPLACE_DISPUTE_RESOLVED"
                sellerNotif.body shouldBe "The dispute for \"Lamp\" was resolved in the buyer's favor. The payment has been refunded to them."

                verify(exactly = 1) { pushNotificationService.sendToUser("buyer_f", any(), any(), any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("seller_f", any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
