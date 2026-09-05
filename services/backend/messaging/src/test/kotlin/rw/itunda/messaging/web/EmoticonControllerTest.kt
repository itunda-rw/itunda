package rw.itunda.messaging.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.UserEmoticonPack
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.messaging.EmoticonService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * this whole controller had NO Idempotency-Key infrastructure at all, despite both
 * purchasePack and giftPack being real money-moving purchases guarded by
 * EmoticonPackAlreadyOwnedException. A lost-response retry after a successful
 * purchase/gift used to hit a confusing conflict for an action that actually
 * already succeeded. This file exists to make sure that wiring can't silently
 * regress.
 */
class EmoticonControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time pack purchase") {
        val service = mockk<EmoticonService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = EmoticonController(service, idempotencyService)

        val owned = mockk<UserEmoticonPack>(relaxed = true)
        every { service.purchasePack("user_1", "pack_1") } returns owned

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/emoticons/packs/pack_1/purchase", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("purchasing the pack") {
            val response = controller.purchasePack("pack_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/emoticons/packs/pack_1/purchase", "key-1", any(), any())
                }
                verify(exactly = 1) { service.purchasePack("user_1", "pack_1") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("ownedPack") shouldBe owned
            }
        }
    }

    Given("a retried pack purchase using the same Idempotency-Key as a completed one") {
        val service = mockk<EmoticonService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = EmoticonController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/emoticons/packs/pack_1/purchase", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "ownedPack" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.purchasePack("pack_1", "key-1", currentUser)

            Then("the cached response is returned and the pack is never purchased again") {
                response.body?.get("ownedPack") shouldBe "cached-result"
                verify(exactly = 0) { service.purchasePack(any(), any()) }
            }
        }
    }

    Given("a first-time pack gift") {
        val service = mockk<EmoticonService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = EmoticonController(service, idempotencyService)

        val gifted = mockk<UserEmoticonPack>(relaxed = true)
        every { service.giftPack("user_1", "+250788111222", "pack_1") } returns gifted

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/emoticons/packs/pack_1/gift", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("gifting the pack") {
            val response = controller.giftPack("pack_1", GiftEmoticonPackRequest("+250788111222"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/emoticons/packs/pack_1/gift", "key-1", any(), any())
                }
                verify(exactly = 1) { service.giftPack("user_1", "+250788111222", "pack_1") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("giftedPack") shouldBe gifted
            }
        }
    }

    Given("a retried pack gift using the same Idempotency-Key as a completed one") {
        val service = mockk<EmoticonService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = EmoticonController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/emoticons/packs/pack_1/gift", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "giftedPack" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.giftPack("pack_1", GiftEmoticonPackRequest("+250788111222"), "key-1", currentUser)

            Then("the cached response is returned and the pack is never gifted again") {
                response.body?.get("giftedPack") shouldBe "cached-result"
                verify(exactly = 0) { service.giftPack(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
