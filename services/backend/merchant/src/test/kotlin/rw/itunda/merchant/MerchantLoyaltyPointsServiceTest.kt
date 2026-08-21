package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantLoyaltyAccount
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantLoyaltyAccountRepository
import java.math.BigDecimal
import java.time.Instant

class MerchantLoyaltyPointsServiceTest : BehaviorSpec({

    val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)

    Given("a customer with no prior real points at a merchant") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns null

        When("checking their real balance") {
            Then("it is honestly zero, not a real database row") {
                service.getBalance("merchant_1", "customer_1") shouldBe BigDecimal.ZERO
            }
        }

        When("they complete a real 10,000 RWF payment") {
            val savedSlot = slot<MerchantLoyaltyAccount>()
            every { merchantLoyaltyAccountRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.accrue(merchant, "customer_1", BigDecimal("10000"))

            Then("it real-creates a new account earning the real 1% accrual rate") {
                savedSlot.captured.merchantId shouldBe "merchant_1"
                savedSlot.captured.customerId shouldBe "customer_1"
                savedSlot.captured.pointBalance shouldBe BigDecimal("100.00")
            }
        }
    }

    // Each scenario below gets its own freshly-constructed 500-point account rather
    // than sharing one mutable object across sibling `When` blocks -- the real
    // service call under test genuinely mutates that object's own `pointBalance`
    // field in place, so a shared instance would leak one scenario's real mutation
    // into the next one's starting balance. Caught live: my own first run of this
    // file failed two real assertions this way (a "redeem more than the balance"
    // case that no longer failed because an earlier sibling test's accrual had
    // already raised the shared balance past the redemption amount, and a
    // "redeem 200" case that landed on 500 instead of the expected 300 for the
    // identical reason) -- fixed by giving every mutating scenario its own account,
    // not a bug in MerchantLoyaltyPointsService itself.
    fun freshAccount() = MerchantLoyaltyAccount(id = "acct_1", merchantId = "merchant_1", customerId = "customer_1", pointBalance = BigDecimal("500.00"))

    Given("a customer with a real existing 500-point balance at a merchant") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns freshAccount()

        When("checking their real balance") {
            Then("it returns the real existing balance") {
                service.getBalance("merchant_1", "customer_1") shouldBe BigDecimal("500.00")
            }
        }

        When("they redeem a real amount of points within their real balance and below the real payment amount") {
            Then("it returns the real requested redemption amount, uncapped") {
                service.validateAndComputeRedemption("merchant_1", "customer_1", BigDecimal("300.00"), BigDecimal("10000")) shouldBe BigDecimal("300.00")
            }
        }

        When("they try to redeem more real points than the real payment itself is worth") {
            Then("it caps the real redemption at the real payment amount, never making a payment go negative") {
                service.validateAndComputeRedemption("merchant_1", "customer_1", BigDecimal("500.00"), BigDecimal("300")) shouldBe BigDecimal("300")
            }
        }
    }

    Given("a customer with a real existing 500-point balance, completing another real payment") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        val account = freshAccount()
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns account
        every { merchantLoyaltyAccountRepository.save(any()) } answers { firstArg() }

        When("they complete another real 20,000 RWF payment") {
            service.accrue(merchant, "customer_1", BigDecimal("20000"))

            Then("it real-adds the newly earned points to the real existing balance, on the same real account row") {
                account.pointBalance shouldBe BigDecimal("700.00")
                verify(exactly = 1) { merchantLoyaltyAccountRepository.save(account) }
            }
        }
    }

    Given("a customer with a real existing 500-point balance, redeeming too many points") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns freshAccount()

        When("they try to redeem more real points than they actually have") {
            Then("it throws InsufficientLoyaltyPointsException rather than letting the balance go negative") {
                try {
                    service.validateAndComputeRedemption("merchant_1", "customer_1", BigDecimal("600.00"), BigDecimal("10000"))
                    error("expected InsufficientLoyaltyPointsException")
                } catch (e: InsufficientLoyaltyPointsException) {
                    // expected
                }
            }
        }
    }

    Given("a customer with a real existing 500-point balance, whose real redemption is recorded after a real successful payment") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns freshAccount()
        val savedSlot = slot<MerchantLoyaltyAccount>()
        every { merchantLoyaltyAccountRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("200 real points are redeemed") {
            service.recordRedemption("merchant_1", "customer_1", BigDecimal("200.00"))

            Then("it real-debits exactly the redeemed amount from the real existing balance") {
                savedSlot.captured.pointBalance shouldBe BigDecimal("300.00")
            }
        }
    }

    Given("a customer with zero real points at a merchant, attempting to redeem anyway") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_2") } returns null

        When("real redemption is attempted against a real account that was never created") {
            Then("it throws InsufficientLoyaltyPointsException, not a real NullPointerException") {
                try {
                    service.recordRedemption("merchant_1", "customer_2", BigDecimal("50.00"))
                    error("expected InsufficientLoyaltyPointsException")
                } catch (e: InsufficientLoyaltyPointsException) {
                    // expected
                }
            }
        }
    }

    // Section 189: real point EXPIRY, itunda's own self-declared inactivity window --
    // see MerchantLoyaltyPointsService's own doc comment for the full honest account of
    // why this window is itunda's own choice, not a sourced Toss Place number. Every
    // scenario below builds its own fresh, independently-timestamped account (the same
    // "no shared mutable fixture across sibling When blocks" discipline this file's own
    // freshAccount() comment already established) rather than reusing one across cases.
    fun dormantAccount() = MerchantLoyaltyAccount(
        id = "acct_dormant",
        merchantId = "merchant_1",
        customerId = "customer_1",
        pointBalance = BigDecimal("500.00"),
        updatedAt = Instant.now().minus(MerchantLoyaltyPointsService.EXPIRY_WINDOW).minusSeconds(3600),
    )

    fun freshlyActiveAccount() = MerchantLoyaltyAccount(
        id = "acct_active",
        merchantId = "merchant_1",
        customerId = "customer_1",
        pointBalance = BigDecimal("500.00"),
        updatedAt = Instant.now().minus(MerchantLoyaltyPointsService.EXPIRY_WINDOW).plusSeconds(3600),
    )

    Given("a customer whose real 500-point balance has sat untouched past the real expiry window") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns dormantAccount()

        When("checking their real balance") {
            Then("it is honestly zero, even though the real stale row itself still says 500") {
                service.getBalance("merchant_1", "customer_1") shouldBe BigDecimal.ZERO
            }
        }

        When("they try to redeem points against the real stale balance") {
            Then("it throws InsufficientLoyaltyPointsException with the real expired (zero) balance, not the stale 500") {
                try {
                    service.validateAndComputeRedemption("merchant_1", "customer_1", BigDecimal("10.00"), BigDecimal("5000"))
                    error("expected InsufficientLoyaltyPointsException")
                } catch (e: InsufficientLoyaltyPointsException) {
                    e.message shouldBe "You only have 0 points at this store"
                }
            }
        }
    }

    Given("a customer whose real dormant 500-point balance is re-recorded on redemption without going through validateAndComputeRedemption first") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns dormantAccount()

        When("recordRedemption is called directly against the real stale row") {
            Then("it independently re-derives the real expired balance and throws rather than trusting the raw stale field") {
                try {
                    service.recordRedemption("merchant_1", "customer_1", BigDecimal("10.00"))
                    error("expected InsufficientLoyaltyPointsException")
                } catch (e: InsufficientLoyaltyPointsException) {
                    e.message shouldBe "You only have 0 points at this store"
                }
            }
        }
    }

    Given("a customer whose real dormant 500-point balance earns a new real purchase after the real expiry window") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        val account = dormantAccount()
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns account
        val savedSlot = slot<MerchantLoyaltyAccount>()
        every { merchantLoyaltyAccountRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("they complete a real 10,000 RWF payment") {
            service.accrue(merchant, "customer_1", BigDecimal("10000"))

            Then("the real stale 500 is reset to zero first, so the new balance is only the real newly-earned 100, not 600") {
                savedSlot.captured.pointBalance shouldBe BigDecimal("100.00")
            }
        }
    }

    Given("a customer whose real 500-point balance is still within the real expiry window") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        every { merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId("merchant_1", "customer_1") } returns freshlyActiveAccount()

        When("checking their real balance") {
            Then("it is still the real full 500, not expired yet") {
                service.getBalance("merchant_1", "customer_1") shouldBe BigDecimal("500.00")
            }
        }
    }

    Given("MerchantLoyaltyPointsExpiryScheduler's own per-item expiry method, a real dormant account due for expiry") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        val account = dormantAccount()
        every { merchantLoyaltyAccountRepository.findById("acct_dormant") } returns java.util.Optional.of(account)
        val savedSlot = slot<MerchantLoyaltyAccount>()
        every { merchantLoyaltyAccountRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("expireIfDue runs against it") {
            service.expireIfDue("acct_dormant")

            Then("it real-zeroes the real stale balance and persists it") {
                savedSlot.captured.pointBalance shouldBe BigDecimal.ZERO
                verify(exactly = 1) { merchantLoyaltyAccountRepository.save(account) }
            }
        }
    }

    Given("MerchantLoyaltyPointsExpiryScheduler's own per-item expiry method, a real account whose activity moments earlier already reset its clock") {
        val merchantLoyaltyAccountRepository = mockk<MerchantLoyaltyAccountRepository>()
        val service = MerchantLoyaltyPointsService(merchantLoyaltyAccountRepository)
        val account = freshlyActiveAccount()
        every { merchantLoyaltyAccountRepository.findById("acct_active") } returns java.util.Optional.of(account)

        When("expireIfDue re-checks it right before acting") {
            service.expireIfDue("acct_active")

            Then("it is a real no-op -- the fresh activity is honored, never saved over") {
                verify(exactly = 0) { merchantLoyaltyAccountRepository.save(any()) }
            }
        }
    }
})
