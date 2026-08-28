package rw.itunda.notifications

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.NotificationRepository
import java.time.Instant

class ServiceChannelServiceTest : BehaviorSpec({

    fun notification(id: String, type: String, isRead: Boolean = false) = Notification(
        id = id, userId = "user_1", type = type, title = "Title $id", body = "Body $id",
        isRead = isRead, createdAt = Instant.now(), dataJson = null,
    )

    Given("a real user with a mix of real notification types") {
        val notificationRepository = mockk<NotificationRepository>()
        val service = ServiceChannelService(notificationRepository)
        val pageable = PageRequest.of(0, 20)

        every { notificationRepository.findByUserIdOrderByCreatedAtDesc("user_1", pageable) } returns
            PageImpl(
                listOf(
                    notification("n1", "MONEY_RECEIVED"),
                    notification("n2", "FRAUD_ALERT"),
                    notification("n3", "SOME_UNMAPPED_TYPE_NOT_YET_ROUTED"),
                ),
                pageable, 3,
            )

        When("fetching the real service-channel thread") {
            val page = service.getServiceChannelThread("user_1", pageable)

            Then("each real notification maps to a bubble, with a real CTA route where one is mapped") {
                page.content[0].id shouldBe "n1"
                page.content[0].ctaRoute shouldBe "/bank/transactions"
                page.content[1].ctaRoute shouldBe "/settings/security"
            }
            Then("an unmapped type gets no fabricated CTA route -- null, not a guess") {
                page.content[2].ctaRoute shouldBe null
            }
        }
    }
})
