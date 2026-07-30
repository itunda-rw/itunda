package rw.itunda.core.fraud

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.FraudRule
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.FraudFlagRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

class FraudRuleEngineTest : BehaviorSpec({

    fun txn(
        senderId: String,
        recipientId: String,
        amount: String,
        createdAt: Instant,
        status: TransactionStatus = TransactionStatus.COMPLETED,
    ) = Transaction(
        id = "t_${(0..999999).random()}", referenceNumber = "REF-${(0..999999).random()}",
        senderId = senderId, recipientId = recipientId, amount = BigDecimal(amount), fee = BigDecimal.ZERO,
        currency = "RWF", type = TransactionType.TRANSFER, status = status,
        description = "test", createdAt = createdAt,
    )

    Given("a user with a clean history sending a small amount to a known recipient") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(fraudFlagRepository, transactionRepository)

        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_1", "user_1") } returns listOf(
            txn("user_1", "user_2", "5000", Instant.now().minusSeconds(3600)),
        )

        When("evaluating a small, familiar payment") {
            val flags = engine.evaluate("user_1", "user_2", BigDecimal("5000"), "txn_new")

            Then("no rule triggers") {
                flags shouldBe emptyList()
            }
        }
    }

    Given("a user sending an amount at the high-value threshold") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(fraudFlagRepository, transactionRepository)

        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_2", "user_2") } returns emptyList()
        every { fraudFlagRepository.save(any()) } answers { firstArg() }

        When("evaluating a 100,000 RWF payment") {
            val flags = engine.evaluate("user_2", "user_9", BigDecimal("100000"), "txn_hv")

            Then("HIGH_VALUE triggers, and NEW_RECIPIENT triggers too since there's no prior history") {
                flags.map { it.rule }.toSet() shouldBe setOf(FraudRule.HIGH_VALUE, FraudRule.NEW_RECIPIENT)
                flags.first { it.rule == FraudRule.HIGH_VALUE }.ruleParameters shouldBe "{\"highValueThreshold\":\"100000\"}"
                flags.first { it.rule == FraudRule.NEW_RECIPIENT }.ruleParameters shouldBe "{\"minimumAmount\":\"0\"}"
            }
        }
    }

    Given("a user who has sent 3 transactions in the last 5 minutes") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(fraudFlagRepository, transactionRepository)

        val now = Instant.now()
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_3", "user_3") } returns listOf(
            txn("user_3", "user_4", "100", now.minusSeconds(30)),
            txn("user_3", "user_4", "100", now.minusSeconds(60)),
            txn("user_3", "user_4", "100", now.minusSeconds(90)),
        )
        every { fraudFlagRepository.save(any()) } answers { firstArg() }

        When("evaluating a 4th small payment to the same, already-known recipient") {
            val flags = engine.evaluate("user_3", "user_4", BigDecimal("100"), "txn_velocity")

            Then("only VELOCITY triggers -- not HIGH_VALUE, not NEW_RECIPIENT") {
                flags.map { it.rule } shouldBe listOf(FraudRule.VELOCITY)
                flags.single().ruleParameters shouldBe "{\"windowMinutes\":5,\"transactionThreshold\":3,\"observedTransactions\":3}"
            }
        }
    }

    Given("a user sending to a recipient with no prior history, but transactions are old") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(fraudFlagRepository, transactionRepository)

        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_5", "user_5") } returns listOf(
            txn("user_5", "user_6", "500", Instant.now().minusSeconds(7200)),
        )
        every { fraudFlagRepository.save(any()) } answers { firstArg() }

        When("evaluating a payment to a brand-new recipient") {
            val flags = engine.evaluate("user_5", "user_7", BigDecimal("500"), "txn_new_recipient")

            Then("only NEW_RECIPIENT triggers -- prior activity is too old for VELOCITY") {
                flags.map { it.rule } shouldBe listOf(FraudRule.NEW_RECIPIENT)
            }
        }
    }

    Given("a user with failed attempts to a recipient") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(fraudFlagRepository, transactionRepository)
        val now = Instant.now()

        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_6", "user_6") } returns listOf(
            txn("user_6", "user_7", "100", now.minusSeconds(30), TransactionStatus.FAILED),
            txn("user_6", "user_7", "100", now.minusSeconds(60), TransactionStatus.CANCELLED),
            txn("user_6", "user_7", "100", now.minusSeconds(90), TransactionStatus.FAILED),
        )
        every { fraudFlagRepository.save(any()) } answers { firstArg() }

        When("they make their first completed payment to that recipient") {
            val flags = engine.evaluate("user_6", "user_7", BigDecimal("100"), "txn_settled")

            Then("failed attempts neither trigger velocity nor hide the new-recipient signal") {
                flags.map { it.rule } shouldBe listOf(FraudRule.NEW_RECIPIENT)
            }
        }
    }

    Given("a risk policy with a higher high-value threshold") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(
            fraudFlagRepository,
            transactionRepository,
            highValueThreshold = BigDecimal("200000"),
            velocityWindowMinutes = 5,
            velocityThreshold = 3,
        )
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_8", "user_8") } returns emptyList()

        When("a 150,000 RWF transaction is evaluated") {
            val flags = engine.evaluate("user_8", null, BigDecimal("150000"), "txn_threshold")

            Then("the configured policy applies instead of the built-in default") {
                flags shouldBe emptyList()
            }
        }

        Then("the active policy can be surfaced to authorized reviewers") {
            engine.activePolicy().highValueThreshold shouldBe BigDecimal("200000")
            engine.activePolicy().velocityWindowMinutes shouldBe 5L
            engine.activePolicy().velocityThreshold shouldBe 3
            engine.activePolicy().newRecipientMinimumAmount shouldBe BigDecimal.ZERO
        }
    }

    Given("an invalid fraud policy") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()

        When("the velocity threshold is zero") {
            Then("application configuration fails fast") {
                try {
                    FraudRuleEngine(
                        fraudFlagRepository,
                        transactionRepository,
                        highValueThreshold = BigDecimal("100000"),
                        velocityWindowMinutes = 5,
                        velocityThreshold = 0,
                    )
                    error("expected invalid fraud configuration")
                } catch (e: IllegalArgumentException) {
                    e.message shouldBe "itunda.fraud.velocity-threshold must be positive"
                }
            }
        }
    }

    Given("a policy that suppresses low-value new-recipient noise") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val engine = FraudRuleEngine(
            fraudFlagRepository,
            transactionRepository,
            highValueThreshold = BigDecimal("100000"),
            velocityWindowMinutes = 5,
            velocityThreshold = 3,
            newRecipientMinimumAmount = BigDecimal("10000"),
        )
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_9", "user_9") } returns emptyList()
        every { fraudFlagRepository.save(any()) } answers { firstArg() }

        When("a small payment goes to a first-time recipient") {
            val flags = engine.evaluate("user_9", "user_10", BigDecimal("500"), "txn_small_new_recipient")

            Then("it is not added to the manual-review queue") {
                flags shouldBe emptyList()
            }
        }

        When("the first payment meets the configured minimum") {
            val flags = engine.evaluate("user_9", "user_10", BigDecimal("10000"), "txn_threshold_new_recipient")

            Then("it retains the new-recipient signal") {
                flags.map { it.rule } shouldBe listOf(FraudRule.NEW_RECIPIENT)
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
