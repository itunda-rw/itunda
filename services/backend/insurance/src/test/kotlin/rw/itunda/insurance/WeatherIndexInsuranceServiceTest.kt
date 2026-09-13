package rw.itunda.insurance

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SeasonRainfallIndex
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.WeatherIndexCropType
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.domain.WeatherIndexPolicyStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SeasonRainfallIndexRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.WeatherIndexPolicyRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for the real Rwanda NAIS-style parametric crop weather-index
 * insurance -- see WeatherIndexInsuranceService's own doc comment for the full sourced
 * account. Structurally distinct from InsuranceServiceTest's claims-based coverage: no
 * individual claim is ever filed here, so the batch-payout-evaluation tests below are the
 * real center of gravity.
 *
 * Each distinct starting state gets its own top-level Given block, not a sibling When --
 * this session's own hard-won Kotest lesson: sibling When blocks under the same Given share
 * ONE mutable entity created in the Given block, and a mutation in one leaks into siblings.
 */
class WeatherIndexInsuranceServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: BigDecimal = BigDecimal("1000000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = balance, availableBalance = balance,
    )

    fun policy(
        id: String,
        userId: String,
        district: String = "Nyagatare",
        season: String = "2026B",
        status: WeatherIndexPolicyStatus = WeatherIndexPolicyStatus.ENROLLED,
        insuredAmount: BigDecimal = BigDecimal("100000"),
        premiumAmount: BigDecimal = BigDecimal("6000"),
    ) = WeatherIndexPolicy(
        id = id, userId = userId, cropType = WeatherIndexCropType.MAIZE, district = district, season = season,
        insuredAmount = insuredAmount, premiumAmount = premiumAmount, status = status,
    )

    // payoutExecutor is a REAL WeatherIndexPayoutExecutor (not mocked) wired against the
    // SAME mocked repositories/ledgerService passed in -- see this session's own
    // build-time bug fix in WeatherIndexInsuranceService.publishSeasonIndex's doc comment
    // for why the per-policy payout logic now lives in a genuinely separate bean. Every
    // existing every{} stub against weatherIndexPolicyRepository/accountRepository/
    // ledgerService below still applies transparently, since the executor calls those
    // exact same mock instances.
    fun newService(
        weatherIndexPolicyRepository: WeatherIndexPolicyRepository = mockk(),
        seasonRainfallIndexRepository: SeasonRainfallIndexRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk<NotificationRepository>(relaxed = true).also { repo -> every { repo.save(any()) } answers { firstArg() } },
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        payoutExecutor: WeatherIndexPayoutExecutor = WeatherIndexPayoutExecutor(weatherIndexPolicyRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService),
    ) = WeatherIndexInsuranceService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService, rateLimiter, payoutExecutor)

    Given("a farmer with a MAIN account enrolling in Maize cover (6% flat rate)") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService, rateLimiter)

        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { weatherIndexPolicyRepository.save(any()) } answers { firstArg() }

        When("enrolling with 100,000 RWF insured amount") {
            val enrolled = service.enroll("user_1", WeatherIndexCropType.MAIZE, "Nyagatare", "2026B", BigDecimal("100000"))

            Then("it computes the correct 6,000 RWF premium (6% of insured amount)") {
                enrolled.premiumAmount shouldBe BigDecimal("6000.00")
                enrolled.insuredAmount shouldBe BigDecimal("100000")
                enrolled.status shouldBe WeatherIndexPolicyStatus.ENROLLED
            }
            Then("it debits the MAIN account and credits insurance_premium_revenue for exactly the premium") {
                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_main"
                accountLeg.direction shouldBe LedgerDirection.DEBIT
                accountLeg.amount shouldBe BigDecimal("6000.00")
                val revenueLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_REVENUE }
                revenueLeg.accountId shouldBe "insurance_premium_revenue"
                revenueLeg.direction shouldBe LedgerDirection.CREDIT
                revenueLeg.amount shouldBe BigDecimal("6000.00")
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
            }
        }
    }

    Given("a farmer cancelling their own ENROLLED policy") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        val enrolledPolicy = policy("wip_1", "user_1", premiumAmount = BigDecimal("6000"))
        every { weatherIndexPolicyRepository.findById("wip_1") } returns Optional.of(enrolledPolicy)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cancel", emptyList())
        every { weatherIndexPolicyRepository.save(any()) } answers { firstArg() }

        When("cancelling it") {
            val cancelled = service.cancel("user_1", "wip_1")

            Then("it fully refunds the premium: DEBIT insurance_premium_revenue / CREDIT account") {
                val revenueLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_REVENUE }
                revenueLeg.direction shouldBe LedgerDirection.DEBIT
                revenueLeg.amount shouldBe BigDecimal("6000")
                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_main"
                accountLeg.direction shouldBe LedgerDirection.CREDIT
                accountLeg.amount shouldBe BigDecimal("6000")
            }
            Then("it marks the policy CANCELLED") {
                cancelled.status shouldBe WeatherIndexPolicyStatus.CANCELLED
            }
        }
    }

    Given("a farmer trying to cancel a policy that already resolved (PAYOUT_TRIGGERED)") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        every { weatherIndexPolicyRepository.findById("wip_2") } returns Optional.of(policy("wip_2", "user_1", status = WeatherIndexPolicyStatus.PAYOUT_TRIGGERED))

        When("cancelling it") {
            Then("it throws WeatherIndexPolicyNotCancellableException and never touches the ledger") {
                try {
                    service.cancel("user_1", "wip_2")
                    error("expected WeatherIndexPolicyNotCancellableException")
                } catch (e: WeatherIndexPolicyNotCancellableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("an attacker trying to view or cancel someone else's policy") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        every { weatherIndexPolicyRepository.findById("wip_owned") } returns Optional.of(policy("wip_owned", "owner_1"))

        When("the attacker calls getPolicy") {
            Then("it real-404s -- same 404-not-403 IDOR pattern used everywhere else") {
                try {
                    service.getPolicy("attacker", "wip_owned")
                    error("expected WeatherIndexPolicyNotFoundException")
                } catch (e: WeatherIndexPolicyNotFoundException) {
                    // expected
                }
            }
        }

        When("the attacker calls cancel") {
            Then("it also real-404s, never leaking a 403 or touching the ledger") {
                try {
                    service.cancel("attacker", "wip_owned")
                    error("expected WeatherIndexPolicyNotFoundException")
                } catch (e: WeatherIndexPolicyNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("an ADMIN trying to double-publish the same district+season") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        every { seasonRainfallIndexRepository.findByDistrictAndSeason("Nyagatare", "2026B") } returns
            SeasonRainfallIndex(id = "sri_1", district = "Nyagatare", season = "2026B", rainfallIndexPercent = 40.0, droughtThresholdPercent = 60.0, publishedByAdminId = "admin_1")

        When("publishing again for Nyagatare 2026B") {
            Then("it real-409s and never touches any policy") {
                try {
                    service.publishSeasonIndex("admin_2", "Nyagatare", "2026B", 30.0, 60.0)
                    error("expected SeasonRainfallIndexAlreadyPublishedException")
                } catch (e: SeasonRainfallIndexAlreadyPublishedException) {
                    verify(exactly = 0) { weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus(any(), any(), any()) }
                }
            }
        }
    }

    Given("Nyagatare 2026B has 2 ENROLLED policies and the published rainfall index is below the drought threshold") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = newService(
            weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val policyA = policy("wip_a", "farmer_a", insuredAmount = BigDecimal("100000"))
        val policyB = policy("wip_b", "farmer_b", insuredAmount = BigDecimal("200000"))

        every { seasonRainfallIndexRepository.findByDistrictAndSeason("Nyagatare", "2026B") } returns null
        every { seasonRainfallIndexRepository.save(any()) } answers { firstArg() }
        every { weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus("Nyagatare", "2026B", WeatherIndexPolicyStatus.ENROLLED) } returns listOf(policyA, policyB)
        every { accountRepository.findByUserIdAndType("farmer_a", AccountType.MAIN) } returns account("account_a", "farmer_a")
        every { accountRepository.findByUserIdAndType("farmer_b", AccountType.MAIN) } returns account("account_b", "farmer_b")
        val allLegs = mutableListOf<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(allLegs)) } returns LedgerPostResult("ledgertxn_payout", emptyList())
        every { weatherIndexPolicyRepository.save(any()) } answers { firstArg() }

        When("publishing rainfall index 30% against a 60% drought threshold") {
            val index = service.publishSeasonIndex("admin_1", "Nyagatare", "2026B", 30.0, 60.0)

            Then("it saves the real published index") {
                index.rainfallIndexPercent shouldBe 30.0
                index.droughtThresholdPercent shouldBe 60.0
                index.publishedByAdminId shouldBe "admin_1"
            }
            Then("BOTH policies are paid out in full and marked PAYOUT_TRIGGERED") {
                policyA.status shouldBe WeatherIndexPolicyStatus.PAYOUT_TRIGGERED
                policyB.status shouldBe WeatherIndexPolicyStatus.PAYOUT_TRIGGERED
                (policyA.payoutAt != null) shouldBe true
                (policyB.payoutAt != null) shouldBe true
            }
            Then("each policy's payout posts the correct DEBIT insurance_claims_expense / CREDIT account legs") {
                allLegs.size shouldBe 2
                val legsForA = allLegs.first { legs -> legs.any { it.accountId == "account_a" } }
                val expenseLegA = legsForA.first { it.accountType == LedgerAccountType.INSURANCE_CLAIMS_EXPENSE }
                expenseLegA.direction shouldBe LedgerDirection.DEBIT
                expenseLegA.amount shouldBe BigDecimal("100000")
                val accountLegA = legsForA.first { it.accountType == LedgerAccountType.WALLET }
                accountLegA.direction shouldBe LedgerDirection.CREDIT
                accountLegA.amount shouldBe BigDecimal("100000")

                val legsForB = allLegs.first { legs -> legs.any { it.accountId == "account_b" } }
                val expenseLegB = legsForB.first { it.accountType == LedgerAccountType.INSURANCE_CLAIMS_EXPENSE }
                expenseLegB.amount shouldBe BigDecimal("200000")
                val accountLegB = legsForB.first { it.accountType == LedgerAccountType.WALLET }
                accountLegB.amount shouldBe BigDecimal("200000")
            }

            // Real sibling-asymmetry fix (2026-09-13) -- InsuranceService.decideClaim
            // notifies on both outcomes; this sibling payout path notified on neither.
            Then("both farmers are notified their real drought payout landed") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "farmer_a" && it.type == "WEATHER_INDEX_PAYOUT_TRIGGERED" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "farmer_b" && it.type == "WEATHER_INDEX_PAYOUT_TRIGGERED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("farmer_a", "Drought payout received", any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("farmer_b", "Drought payout received", any()) }
            }
        }
    }

    Given("Nyagatare 2026B has 1 ENROLLED policy and the published rainfall index is ABOVE the drought threshold") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = newService(
            weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val policyC = policy("wip_c", "farmer_c", insuredAmount = BigDecimal("100000"))
        every { seasonRainfallIndexRepository.findByDistrictAndSeason("Nyagatare", "2026B") } returns null
        every { seasonRainfallIndexRepository.save(any()) } answers { firstArg() }
        every { weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus("Nyagatare", "2026B", WeatherIndexPolicyStatus.ENROLLED) } returns listOf(policyC)
        every { weatherIndexPolicyRepository.save(any()) } answers { firstArg() }

        When("publishing rainfall index 80% against a 60% drought threshold (good rains, no drought)") {
            service.publishSeasonIndex("admin_1", "Nyagatare", "2026B", 80.0, 60.0)

            Then("the policy is marked SEASON_ENDED_NO_PAYOUT with no payoutAt and zero ledger calls") {
                policyC.status shouldBe WeatherIndexPolicyStatus.SEASON_ENDED_NO_PAYOUT
                policyC.payoutAt shouldBe null
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
            }

            // Real sibling-asymmetry fix (2026-09-13) -- "every real insurer notifies
            // on both outcomes," matching InsuranceService.decideClaim's own reasoning.
            Then("the farmer is still notified the season ended with no payout") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "farmer_c" && it.type == "WEATHER_INDEX_SEASON_ENDED_NO_PAYOUT" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("farmer_c", "Crop insurance season ended", any()) }
            }
        }
    }

    Given("a policy in a DIFFERENT district is untouched by an unrelated publish") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        // Bugumbura policy is simply never returned by the district+season-scoped query --
        // the real isolation guarantee is that publishSeasonIndex only ever asks the
        // repository for THIS exact district+season, never touching unrelated rows.
        val untouchedPolicy = policy("wip_other", "farmer_other", district = "Bugesera", season = "2026B")
        every { seasonRainfallIndexRepository.findByDistrictAndSeason("Nyagatare", "2026B") } returns null
        every { seasonRainfallIndexRepository.save(any()) } answers { firstArg() }
        every { weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus("Nyagatare", "2026B", WeatherIndexPolicyStatus.ENROLLED) } returns emptyList()

        When("publishing for Nyagatare 2026B") {
            service.publishSeasonIndex("admin_1", "Nyagatare", "2026B", 30.0, 60.0)

            Then("the Bugesera policy's status and payoutAt are never touched") {
                untouchedPolicy.status shouldBe WeatherIndexPolicyStatus.ENROLLED
                untouchedPolicy.payoutAt shouldBe null
                verify(exactly = 0) { weatherIndexPolicyRepository.save(untouchedPolicy) }
            }
        }
    }

    Given("a policy whose farmer has no MAIN account during a drought-triggered publish") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        val accountlessPolicy = policy("wip_accountless", "farmer_no_account", insuredAmount = BigDecimal("50000"))
        val fundedPolicy = policy("wip_funded", "farmer_funded", insuredAmount = BigDecimal("50000"))
        every { seasonRainfallIndexRepository.findByDistrictAndSeason("Nyagatare", "2026B") } returns null
        every { seasonRainfallIndexRepository.save(any()) } answers { firstArg() }
        every { weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus("Nyagatare", "2026B", WeatherIndexPolicyStatus.ENROLLED) } returns listOf(accountlessPolicy, fundedPolicy)
        every { accountRepository.findByUserIdAndType("farmer_no_account", AccountType.MAIN) } returns null
        every { accountRepository.findByUserIdAndType("farmer_funded", AccountType.MAIN) } returns account("account_funded", "farmer_funded")
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_partial", emptyList())
        every { weatherIndexPolicyRepository.save(any()) } answers { firstArg() }

        When("publishing a drought-triggered index") {
            service.publishSeasonIndex("admin_1", "Nyagatare", "2026B", 20.0, 60.0)

            Then("the accountless farmer's policy is skipped (still ENROLLED) but the batch doesn't fail") {
                accountlessPolicy.status shouldBe WeatherIndexPolicyStatus.ENROLLED
            }
            Then("the funded farmer's policy is still paid out correctly") {
                fundedPolicy.status shouldBe WeatherIndexPolicyStatus.PAYOUT_TRIGGERED
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
            }
        }
    }

    // Real bug found in this feature's own build-time review (2026-08-02): the first
    // draft wrapped the whole per-policy payout loop in ONE @Transactional method with a
    // try/catch per policy -- but a RuntimeException escaping any nested
    // @Transactional-participating call (e.g. a genuine optimistic-lock conflict) marks
    // the WHOLE ambient Spring transaction rollback-only regardless of the try/catch,
    // silently reverting every OTHER farmer's already-"successful" payout plus the
    // newly-published index row. Fixed by moving per-policy settlement to
    // WeatherIndexPayoutExecutor, a genuinely separate @Service bean called through its
    // own real transactional proxy. This test proves the architectural fix at the unit
    // level: one policy's settlement throwing doesn't stop the loop from processing the
    // next policy, and the index row (saved before the loop even starts) is unaffected.
    Given("one policy's settlement throws mid-batch while a sibling policy in the same publish is healthy") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val payoutExecutor = mockk<WeatherIndexPayoutExecutor>()
        val service = newService(
            weatherIndexPolicyRepository = weatherIndexPolicyRepository,
            seasonRainfallIndexRepository = seasonRainfallIndexRepository,
            payoutExecutor = payoutExecutor,
        )

        val brokenPolicy = policy("wip_broken", "farmer_broken", insuredAmount = BigDecimal("50000"))
        val healthyPolicy = policy("wip_healthy", "farmer_healthy", insuredAmount = BigDecimal("50000"))
        every { seasonRainfallIndexRepository.findByDistrictAndSeason("Nyagatare", "2026B") } returns null
        every { seasonRainfallIndexRepository.save(any()) } answers { firstArg() }
        every { weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus("Nyagatare", "2026B", WeatherIndexPolicyStatus.ENROLLED) } returns listOf(brokenPolicy, healthyPolicy)
        every { payoutExecutor.settleOnePolicy(brokenPolicy, true) } throws org.springframework.orm.ObjectOptimisticLockingFailureException(WeatherIndexPolicy::class.java, "wip_broken")
        every { payoutExecutor.settleOnePolicy(healthyPolicy, true) } returns true

        When("publishing a drought-triggered index") {
            val index = service.publishSeasonIndex("admin_1", "Nyagatare", "2026B", 20.0, 60.0)

            Then("publishSeasonIndex itself doesn't throw -- the broken policy's failure is genuinely contained") {
                index.district shouldBe "Nyagatare"
                index.season shouldBe "2026B"
            }
            Then("the healthy sibling policy's settlement was still attempted, proving the loop survives past the broken one") {
                verify(exactly = 1) { payoutExecutor.settleOnePolicy(healthyPolicy, true) }
                verify(exactly = 1) { payoutExecutor.settleOnePolicy(brokenPolicy, true) }
            }
        }
    }

    Given("a farmer trying to enroll with an insured amount above itunda's honest ceiling") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService)

        When("enrolling with 600,000 RWF (above the 500,000 ceiling)") {
            Then("it throws InvalidWeatherIndexEnrollmentException before touching the ledger") {
                try {
                    service.enroll("user_1", WeatherIndexCropType.MAIZE, "Nyagatare", "2026B", BigDecimal("600000"))
                    error("expected InvalidWeatherIndexEnrollmentException")
                } catch (e: InvalidWeatherIndexEnrollmentException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real user exceeds the real weather-index enrollment rate limit") {
        val weatherIndexPolicyRepository = mockk<WeatherIndexPolicyRepository>()
        val seasonRainfallIndexRepository = mockk<SeasonRainfallIndexRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>()
        val service = newService(weatherIndexPolicyRepository, seasonRainfallIndexRepository, accountRepository, ledgerService, rateLimiter)

        every { rateLimiter.checkLimit("weather-index:enroll:user_9", limit = 10, window = any()) } throws RateLimitExceededException("Too many requests")

        When("they try to enroll again") {
            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.enroll("user_9", WeatherIndexCropType.MAIZE, "Nyagatare", "2026B", BigDecimal("100000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
