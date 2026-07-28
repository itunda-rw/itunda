package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.ProductInquiry
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.ProductInquiryRepository
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
) {
    @Transactional
    fun askQuestion(buyerId: String, productId: String, question: String): ProductInquiry {
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
                notificationRepository.save(
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "NEW_PRODUCT_INQUIRY",
                        title = "New question about ${product.name}", body = trimmedQuestion, isRead = false,
                        createdAt = Instant.now(), dataJson = "{\"inquiryId\":\"${saved.id}\"}",
                    ),
                )
            }
        } catch (e: Exception) {
            // Non-critical -- the real question was already saved successfully.
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
