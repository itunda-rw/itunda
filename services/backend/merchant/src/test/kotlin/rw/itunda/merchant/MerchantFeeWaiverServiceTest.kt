package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal

/**
 * First test coverage for the real Naver Pay 영세 가맹점 수수료 지원 (small-merchant
 * fee-waiver support program) -- see MerchantFeeWaiverService's own doc comment for the
 * full sourced account.
 */
class MerchantFeeWaiverServiceTest : BehaviorSpec({

    fun transaction(amount: BigDecimal) = Transaction(
        id = "txn_${amount}", referenceNumber = "REF$amount", senderId = "payer", recipientId = "owner_1",
        amount = amount, fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED, description = "test",
    )

    Given("a real small merchant well under the real 30-day volume threshold") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val transactionRepository = mockk<TransactionRepository>()
        val service = MerchantFeeWaiverService(merchantRepository, merchantService, transactionRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)
        every { merchantService.getMyMerchant("owner_1") } returns merchant
        every { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween("owner_1", TransactionType.PAYMENT, any(), any()) } returns
            listOf(transaction(BigDecimal("50000")), transaction(BigDecimal("30000")))
        every { merchantRepository.save(any()) } answers { firstArg() }

        When("applying for a real fee waiver") {
            val result = service.applyForFeeWaiver("owner_1")

            Then("it real-grants a full 0% waiver") {
                result.feeRateOverride shouldBe BigDecimal.ZERO
                verify(exactly = 1) { merchantRepository.save(match { it.feeRateOverride == BigDecimal.ZERO }) }
            }
        }
    }

    Given("a real merchant at or above the real 30-day volume threshold") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val transactionRepository = mockk<TransactionRepository>()
        val service = MerchantFeeWaiverService(merchantRepository, merchantService, transactionRepository)

        val merchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", accountId = "account_2", businessName = "Big Shop", status = MerchantStatus.ACTIVE)
        every { merchantService.getMyMerchant("owner_2") } returns merchant
        every { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween("owner_2", TransactionType.PAYMENT, any(), any()) } returns
            listOf(transaction(BigDecimal("500000")))

        When("applying for a real fee waiver") {
            Then("it throws MerchantNotEligibleForFeeWaiverException before ever saving anything") {
                try {
                    service.applyForFeeWaiver("owner_2")
                    error("expected MerchantNotEligibleForFeeWaiverException")
                } catch (e: MerchantNotEligibleForFeeWaiverException) {
                    verify(exactly = 0) { merchantRepository.save(any()) }
                }
            }
        }
    }

    Given("a real merchant who already has a real active fee waiver") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val transactionRepository = mockk<TransactionRepository>()
        val service = MerchantFeeWaiverService(merchantRepository, merchantService, transactionRepository)

        val merchant = Merchant(
            id = "merchant_3", ownerUserId = "owner_3", accountId = "account_3", businessName = "Waived Shop",
            status = MerchantStatus.ACTIVE, feeRateOverride = BigDecimal.ZERO,
        )
        every { merchantService.getMyMerchant("owner_3") } returns merchant

        When("applying again") {
            Then("it throws MerchantAlreadyWaivedException, never touching real transaction history") {
                try {
                    service.applyForFeeWaiver("owner_3")
                    error("expected MerchantAlreadyWaivedException")
                } catch (e: MerchantAlreadyWaivedException) {
                    verify(exactly = 0) { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
