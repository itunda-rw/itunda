package rw.itunda.wallet

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real bug found live (2026-08-02): WalletService.confirmTransfer used to gate on a
 * plain `quote.status == PENDING` field read, then write `quote.status = CONFIRMED`
 * only after the provider call and ledger post -- with no atomicity between the two,
 * two concurrent confirmTransfer calls for the same quoteId could both pass the check
 * before either wrote CONFIRMED, and both go on to post the real ledger legs, a
 * genuine double-spend of one quote. This test proves [QuoteStore.claim] closes that
 * race directly, with real concurrent threads (not a sequential simulation) racing
 * for the same quote.
 */
class QuoteStoreTest : BehaviorSpec({

    Given("a real PENDING quote, raced by many real concurrent threads calling claim()") {
        val store = QuoteStore()
        val quote = store.create("user_1", "wallet_1", "+250788111111", BigDecimal("1000"), BigDecimal("10"), "RWF")

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val goLatch = CountDownLatch(1)
        val successCount = AtomicInteger(0)

        When("every thread races to claim() the exact same quoteId at the same instant") {
            val futures = (1..threadCount).map {
                executor.submit {
                    readyLatch.countDown()
                    goLatch.await()
                    if (store.claim(quote.id, Instant.now()) != null) successCount.incrementAndGet()
                }
            }
            readyLatch.await(5, TimeUnit.SECONDS)
            goLatch.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            executor.shutdown()

            Then("exactly one thread wins the claim, never zero, never more than one") {
                successCount.get() shouldBe 1
            }
            Then("the quote ends up CLAIMED, not corrupted by the losing threads' no-op writes") {
                store.get(quote.id)?.status shouldBe QuoteStatus.CLAIMED
            }
        }
    }

    Given("a claimed quote whose downstream provider call then declines") {
        val store = QuoteStore()
        val quote = store.create("user_1", "wallet_1", "+250788111111", BigDecimal("1000"), BigDecimal("10"), "RWF")
        store.claim(quote.id, Instant.now())

        When("the claim is released") {
            store.releaseClaim(quote.id)

            Then("the quote is back to PENDING, real-retryable rather than permanently stranded") {
                store.get(quote.id)?.status shouldBe QuoteStatus.PENDING
            }
        }
    }

    Given("a quote that's PENDING but already past its own expiresAt") {
        val store = QuoteStore()
        val quote = store.create("user_1", "wallet_1", "+250788111111", BigDecimal("1000"), BigDecimal("10"), "RWF")

        When("claim() is called with a clock reading well after expiry") {
            val result = store.claim(quote.id, quote.expiresAt.plusSeconds(1))

            Then("it real-expires the quote atomically instead of claiming it") {
                result shouldBe null
                store.get(quote.id)?.status shouldBe QuoteStatus.EXPIRED
            }
        }
    }

    Given("a quote that's already CONFIRMED") {
        val store = QuoteStore()
        val quote = store.create("user_1", "wallet_1", "+250788111111", BigDecimal("1000"), BigDecimal("10"), "RWF")
        store.claim(quote.id, Instant.now())
        store.get(quote.id)?.status = QuoteStatus.CONFIRMED

        When("claim() is called again") {
            val result = store.claim(quote.id, Instant.now())

            Then("it returns null rather than reopening an already-confirmed quote") {
                result shouldBe null
            }
        }
    }
})
