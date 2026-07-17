package rw.itunda.overview

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.LinkedAccountRepository

class LinkedAccountServiceTest : BehaviorSpec({

    Given("linking a real bank/MoMo account that verifies successfully") {
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val providerConnector = mockk<ProviderConnector>()
        // Real, not mocked -- pure and deterministic, same convention as this repo's
        // other real-not-mocked demo services (DemoNidaVerificationService,
        // DemoCardAuthorizationService).
        val demoExternalBalanceService = DemoExternalBalanceService()
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService)

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
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService)

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
        val service = LinkedAccountService(linkedAccountRepository, providerConnector, demoExternalBalanceService)

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
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
