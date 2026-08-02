package rw.itunda.overview

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.LinkedAccountRepository
import java.time.Duration

class LinkedAccountServiceTest : BehaviorSpec({

    Given("linking a real bank/MoMo account that verifies successfully") {
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val providerConnector = mockk<ProviderConnector>()
        // Real, not mocked -- pure and deterministic, same convention as this repo's
        // other real-not-mocked demo services (DemoNidaVerificationService,
        // DemoCardAuthorizationService).
        val demoExternalBalanceService = DemoExternalBalanceService()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService, rateLimiter)

        every { providerConnector.attempt(any(), any()) } returns Unit
        every { linkedAccountRepository.save(any()) } answers { firstArg() }

        When("linking") {
            val account = service.link("user_1", "MTN MoMo", "0788123456")

            Then("it's real LINKED, and a real demo balance is generated, not left null") {
                account.status shouldBe LinkedAccountStatus.LINKED
                account.demoBalance shouldNotBe null
                account.demoBalanceCurrency shouldBe "RWF"
            }
        }
    }

    Given("linking an account where the simulated provider call declines") {
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val providerConnector = mockk<ProviderConnector>()
        val demoExternalBalanceService = DemoExternalBalanceService()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService, rateLimiter)

        every { providerConnector.attempt(any(), any()) } throws ProviderDeclinedException("MTN Mobile Money declined: Account verification for MTN MoMo")
        every { linkedAccountRepository.save(any()) } answers { firstArg() }

        When("linking") {
            val account = service.link("user_2", "MTN MoMo", "0788999999")

            Then("it's real VERIFICATION_FAILED, and no demo balance is generated -- a failed link never got real consent") {
                account.status shouldBe LinkedAccountStatus.VERIFICATION_FAILED
                account.demoBalance shouldBe null
                account.demoBalanceCurrency shouldBe null
                account.failureReason shouldNotBe null
            }
        }
    }

    Given("the same account number linked twice") {
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val providerConnector = mockk<ProviderConnector>()
        val demoExternalBalanceService = DemoExternalBalanceService()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService, rateLimiter)

        every { providerConnector.attempt(any(), any()) } returns Unit
        every { linkedAccountRepository.save(any()) } answers { firstArg() }

        When("linking it twice") {
            val first = service.link("user_3", "MTN MoMo", "0788123456")
            val second = service.link("user_3", "MTN MoMo", "0788123456")

            Then("the real demo balance is deterministic -- the same account always gets the same value, not randomly flaky") {
                first.demoBalance shouldBe second.demoBalance
            }
        }
    }

    // Real IDOR fix (2026-08-02): unlink() used to throw LinkedAccountNotOwnedException
    // (403) for a real-but-not-owned accountId, confirming the id was real to anyone
    // who guesses or enumerates it -- accountId is a real URL path variable
    // (POST /api/v1/accounts/link/{accountId}/unlink), directly probeable. Now the
    // same LinkedAccountNotFoundException (404) as a genuinely bogus id.
    Given("an attacker attempting to unlink someone else's linked account") {
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val providerConnector = mockk<ProviderConnector>()
        val demoExternalBalanceService = DemoExternalBalanceService()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService, rateLimiter)
        val othersAccount = rw.itunda.core.domain.LinkedAccount(
            id = "linked_1", userId = "owner_1", provider = "MTN MoMo",
            externalAccountNumberMasked = "•••• 1234", status = LinkedAccountStatus.LINKED,
        )
        every { linkedAccountRepository.findById("linked_1") } returns java.util.Optional.of(othersAccount)

        When("the attacker tries to unlink it") {
            Then("it real-404s, never revealing that linked_1 is a real account someone else owns") {
                try {
                    service.unlink("attacker", "linked_1")
                    error("expected LinkedAccountNotFoundException")
                } catch (e: LinkedAccountNotFoundException) {
                    verify(exactly = 0) { linkedAccountRepository.save(any()) }
                }
            }
        }
    }

    Given("a real user exceeds the real account-linking rate limit") {
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val providerConnector = mockk<ProviderConnector>()
        val demoExternalBalanceService = DemoExternalBalanceService()
        val rateLimiter = mockk<RateLimiter>()
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService, rateLimiter)
        every { rateLimiter.checkLimit("accounts:link:user_9", limit = 10, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to link another real account") {
            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security sweep, before ever calling the real provider") {
                try {
                    service.link("user_9", "MTN MoMo", "0788123456")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { providerConnector.attempt(any(), any()) }
                    verify(exactly = 0) { linkedAccountRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
