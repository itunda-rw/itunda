package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.ProductInquiry
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.ProductInquiryRepository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidProductInquiryException(message: String) : RuntimeException(message)
class ProductInquiryNotFoundException(message: String) : RuntimeException(message)
class InvalidProductInquiryAnswerException(message: String) : RuntimeException(message)

/**
 * Real Coupang-style pre-purchase product Q&A -- see `ProductInquiry.kt`'s own doc
 * comment for the full account of why this is genuinely distinct from
 * `ProductReviewService` (no order/purchase required at all).
 */
@Service
class ProductInquiryService(
    private val productInquiryRepository: ProductInquiryRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val merchantRepository: MerchantRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(ProductInquiryService::class.java)

    @Transactional
    fun askQuestion(buyerId: String, productId: String, question: String): ProductInquiry {
        // Real gap found live (2026-09-14, zero-RateLimiter-class sweep): this class had
        // no RateLimiter at all -- KnowledgeService.postQuestion's own identical
        // "user posts a free-text question" shape already rate-limits at 10/hour, and an
        // unbounded askQuestion flood spams a real merchant with a real push per call.
        rateLimiter.checkLimit("commerce:product-inquiry:$buyerId", limit = 10, window = Duration.ofHours(1))
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isEmpty() || trimmedQuestion.length > 500) {
            throw InvalidProductInquiryException("Question must be between 1 and 500 characters")
        }
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { OrderProductNotFoundException("Product not found") }

        val saved = productInquiryRepository.save(
            ProductInquiry(
                id = "product_inquiry_${UUID.randomUUID()}", productId = productId, merchantId = product.merchantId,
                buyerId = buyerId, question = trimmedQuestion,
            ),
        )

        // Real "new question" alert for the merchant -- same real gap this session
        // already found and fixed for a new order arriving (EatsOrderService/
        // OrderService.placeOrder): a seller should learn a real customer asked
        // something without having to poll for it.
        try {
            val merchant = merchantRepository.findById(product.merchantId).orElse(null)
            if (merchant != null) {
                val title = "New question about ${product.name}"
                notificationRepository.save(
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "NEW_PRODUCT_INQUIRY",
                        title = title, body = trimmedQuestion, isRead = false,
                        createdAt = Instant.now(), dataJson = "{\"inquiryId\":\"${saved.id}\"}",
                    ),
                )
                // Real push (item 140) -- this doc comment above already claimed this
                // was fixed alongside the new-order push gaps (items 122-124), but the
                // actual push call was never added -- found via a repo-wide save-vs-push
                // count sweep.
                pushNotificationService.sendToUser(merchant.ownerUserId, title, trimmedQuestion)
            }
        } catch (e: Exception) {
            // Non-critical -- the real question was already saved successfully.
            log.warn("Failed to notify merchant of new product inquiry {}", saved.id, e)
        }

        return saved
    }

    fun getProductInquiries(productId: String, pageable: Pageable): Page<ProductInquiry> =
        productInquiryRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)

    fun getMyInquiries(buyerId: String, pageable: Pageable): Page<ProductInquiry> =
        productInquiryRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    // Real owner-side answer -- editable, matches EatsReviewService/
    // ProductReviewService/MerchantBookingReviewService's own exact reply simplicity
    // (re-answering overwrites, no separate versioning).
    @Transactional
    fun answerQuestion(ownerUserId: String, inquiryId: String, answer: String): ProductInquiry {
        val trimmedAnswer = answer.trim()
        if (trimmedAnswer.isEmpty() || trimmedAnswer.length > 1000) {
            throw InvalidProductInquiryAnswerException("Answer must be between 1 and 1000 characters")
        }
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val inquiry = productInquiryRepository.findById(inquiryId)
            .orElseThrow { ProductInquiryNotFoundException("Question not found") }
        if (inquiry.merchantId != merchant.id) {
            throw ProductInquiryNotFoundException("Question not found")
        }
        inquiry.answer = trimmedAnswer
        inquiry.answeredAt = Instant.now()
        val saved = productInquiryRepository.save(inquiry)
        val title = "${merchant.businessName} answered your question"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = inquiry.buyerId, type = "PRODUCT_INQUIRY_ANSWERED",
                title = title, body = trimmedAnswer, isRead = false,
                createdAt = Instant.now(), dataJson = "{\"inquiryId\":\"${saved.id}\"}",
            ),
        )
        // Real push (item 123) -- same "seller replied" urgency as
        // MerchantBookingReviewService's owner-reply notify.
        pushNotificationService.sendToUser(inquiry.buyerId, title, trimmedAnswer)
        return saved
    }
}
