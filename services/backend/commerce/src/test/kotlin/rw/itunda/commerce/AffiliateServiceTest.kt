package rw.itunda.commerce

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AffiliateCommission
import rw.itunda.core.domain.AffiliateLink
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AffiliateCommissionRepository
import rw.itunda.core.repository.AffiliateLinkRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal

/**
 * First test coverage for real 쿠팡파트너스 (Coupang Partners)-style affiliate links
 * -- see AffiliateLink.kt's own doc comment for the full sourced account. Mirrors
 * DesignatedDriverServiceTest's own established mocking conventions.
 */
class AffiliateServiceTest : BehaviorSpec({

    fun newService(
        affiliateLinkRepository: AffiliateLinkRepository = mockk(),
        affiliateCommissionRepository: AffiliateCommissionRepository = mockk(),
        merchantProductRepository: MerchantProductRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = AffiliateService(affiliateLinkRepository, affiliateCommissionRepository, merchantProductRepository, accountRepository, ledgerService, rateLimiter)

    Given("a real product a user wants to promote") {
        val affiliateLinkRepository = mockk<AffiliateLinkRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val service = newService(affiliateLinkRepository = affiliateLinkRepository, merchantProductRepository = merchantProductRepository)

        every { merchantProductRepository.existsById("product_1") } returns true
        every { affiliateLinkRepository.findByCode(any()) } returns null
        val savedSlot = slot<AffiliateLink>()
        every { affiliateLinkRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the user creates a real affiliate link") {
            val result = service.createLink("user_1", "product_1")

            Then("a real link row is saved with a generated code") {
                result.userId shouldBe "user_1"
                result.productId shouldBe "product_1"
                result.code.isNotBlank() shouldBe true
                savedSlot.captured.userId shouldBe "user_1"
            }
        }
    }

    Given("a nonexistent product") {
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val service = newService(merchantProductRepository = merchantProductRepository)
        every { merchantProductRepository.existsById("no_such_product") } returns false

        When("a user tries to create a link for it") {
            Then("it real-404s before ever touching the link table") {
                try {
                    service.createLink("user_1", "no_such_product")
                    throw AssertionError("expected AffiliateProductNotFoundException")
                } catch (e: AffiliateProductNotFoundException) {
                    e.message shouldBe "Product not found"
                }
            }
        }
    }

    Given("a real order placed through someone else's affiliate link") {
        val affiliateLinkRepository = mockk<AffiliateLinkRepository>()
        val affiliateCommissionRepository = mockk<AffiliateCommissionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            affiliateLinkRepository = affiliateLinkRepository, affiliateCommissionRepository = affiliateCommissionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val link = AffiliateLink(id = "affiliate_link_1", userId = "referrer_1", productId = "product_1", code = "AFABC123")
        val referrerAccount = Account(
            id = "account_referrer", userId = "referrer_1", accountNumber = "1000000009", accountName = "Referrer",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { affiliateLinkRepository.findByCode("AFABC123") } returns link
        every { accountRepository.findByUserIdAndType("referrer_1", AccountType.MAIN) } returns referrerAccount
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_commission", emptyList())
        val savedSlot = slot<AffiliateCommission>()
        every { affiliateCommissionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the order completes") {
            service.payCommissionIfReferred("AFABC123", "order_1", "buyer_1", BigDecimal("10000"))

            Then("a real 3% commission is paid to the referrer") {
                savedSlot.captured.referrerId shouldBe "referrer_1"
                savedSlot.captured.commissionAmount shouldBe BigDecimal("300.00")
                savedSlot.captured.payoutTransactionId shouldBe "ledgertxn_commission"
            }
        }
    }

    Given("a real order placed through the buyer's own affiliate link") {
        val affiliateLinkRepository = mockk<AffiliateLinkRepository>()
        val affiliateCommissionRepository = mockk<AffiliateCommissionRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            affiliateLinkRepository = affiliateLinkRepository, affiliateCommissionRepository = affiliateCommissionRepository,
            ledgerService = ledgerService,
        )

        val link = AffiliateLink(id = "affiliate_link_1", userId = "buyer_1", productId = "product_1", code = "AFSELF01")
        every { affiliateLinkRepository.findByCode("AFSELF01") } returns link

        When("the order completes") {
            service.payCommissionIfReferred("AFSELF01", "order_1", "buyer_1", BigDecimal("10000"))

            Then("no self-referral commission is paid") {
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real link being resolved by whoever clicked it") {
        val affiliateLinkRepository = mockk<AffiliateLinkRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = newService(affiliateLinkRepository = affiliateLinkRepository, rateLimiter = rateLimiter)

        val link = AffiliateLink(id = "affiliate_link_1", userId = "referrer_1", productId = "product_1", code = "AFABC123", clickCount = 5)
        every { rateLimiter.checkLimit("affiliate:resolve:AFABC123", limit = 30, window = any()) } returns Unit
        every { affiliateLinkRepository.findByCode("AFABC123") } returns link
        every { affiliateLinkRepository.save(any()) } answers { firstArg() }

        When("it resolves within the real per-code limit") {
            val result = service.resolveLink("AFABC123")

            Then("a real click is recorded") {
                result.clickCount shouldBe 6
            }
        }
    }

    Given("a real code hammered past its resolve rate limit") {
        val affiliateLinkRepository = mockk<AffiliateLinkRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = newService(affiliateLinkRepository = affiliateLinkRepository, rateLimiter = rateLimiter)

        every { rateLimiter.checkLimit("affiliate:resolve:AFHOT001", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

        When("one more resolve attempt arrives") {
            Then("it real-429s before ever touching the link table, unauthenticated caller or not") {
                try {
                    service.resolveLink("AFHOT001")
                    throw AssertionError("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { affiliateLinkRepository.findByCode(any()) }
                }
            }
        }
    }

    Given("an order with no referral code") {
        val ledgerService = mockk<LedgerService>()
        val service = newService(ledgerService = ledgerService)

        When("the order completes") {
            service.payCommissionIfReferred(null, "order_1", "buyer_1", BigDecimal("10000"))

            Then("nothing happens") {
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }
})
