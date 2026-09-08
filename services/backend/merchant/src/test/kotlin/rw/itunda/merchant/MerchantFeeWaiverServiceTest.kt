package rw.itunda.merchant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import java.util.Optional

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

    // Real ops review (Merchant product-completeness pass) -- see
    // MerchantFeeWaiverService.getRevocationCandidates's own doc comment.
    Given("two waived merchants, one who has outgrown the threshold and one who hasn't") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val transactionRepository = mockk<TransactionRepository>()
        val service = MerchantFeeWaiverService(merchantRepository, merchantService, transactionRepository)

        val outgrown = Merchant(
            id = "merchant_4", ownerUserId = "owner_4", accountId = "account_4", businessName = "Outgrown Shop",
            status = MerchantStatus.ACTIVE, feeRateOverride = BigDecimal.ZERO,
        )
        val stillSmall = Merchant(
            id = "merchant_5", ownerUserId = "owner_5", accountId = "account_5", businessName = "Still Small Shop",
            status = MerchantStatus.ACTIVE, feeRateOverride = BigDecimal.ZERO,
        )
        every { merchantRepository.findByFeeRateOverride(BigDecimal.ZERO) } returns listOf(outgrown, stillSmall)
        every { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween("owner_4", TransactionType.PAYMENT, any(), any()) } returns
            listOf(transaction(BigDecimal("500000")))
        every { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween("owner_5", TransactionType.PAYMENT, any(), any()) } returns
            listOf(transaction(BigDecimal("50000")))

        When("listing revocation candidates") {
            val candidates = service.getRevocationCandidates()

            Then("only the merchant who has genuinely outgrown the threshold appears") {
                candidates.size shouldBe 1
                candidates[0]["merchantId"] shouldBe "merchant_4"
            }
        }
    }

    Given("a real merchant with an active fee waiver being revoked by an admin") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val transactionRepository = mockk<TransactionRepository>()
        val service = MerchantFeeWaiverService(merchantRepository, merchantService, transactionRepository)

        val merchant = Merchant(
            id = "merchant_6", ownerUserId = "owner_6", accountId = "account_6", businessName = "Waived Shop 2",
            status = MerchantStatus.ACTIVE, feeRateOverride = BigDecimal.ZERO,
        )
        every { merchantRepository.findById("merchant_6") } returns Optional.of(merchant)
        every { merchantRepository.save(any()) } answers { firstArg() }

        When("the admin revokes it") {
            val result = service.revokeFeeWaiver("merchant_6", "admin_6")

            Then("the waiver is real-cleared back to null, not just zeroed again") {
                result.feeRateOverride shouldBe null
            }

            Then("it records which admin revoked it, not a silent field clear") {
                result.feeWaiverRevokedBy shouldBe "admin_6"
                result.feeWaiverRevokedAt shouldNotBe null
            }
        }
    }

    Given("a real merchant with no active fee waiver") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val transactionRepository = mockk<TransactionRepository>()
        val service = MerchantFeeWaiverService(merchantRepository, merchantService, transactionRepository)

        val merchant = Merchant(id = "merchant_7", ownerUserId = "owner_7", accountId = "account_7", businessName = "Never Waived Shop", status = MerchantStatus.ACTIVE)
        every { merchantRepository.findById("merchant_7") } returns Optional.of(merchant)

        When("an admin tries to revoke it anyway") {
            Then("the status guard fires -- nothing to revoke") {
                shouldThrow<MerchantNotWaivedException> { service.revokeFeeWaiver("merchant_7", "admin_7") }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
