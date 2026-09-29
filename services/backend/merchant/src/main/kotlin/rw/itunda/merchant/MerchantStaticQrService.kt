package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal

class InvalidStaticQrAmountException(message: String) : RuntimeException(message)

/**
 * Real Kakao Pay 정액 QR (static/fixed merchant QR) -- sourced from Kakao Pay's own
 * real, currently-live small-merchant product (a free, printable QR kit; roughly 3,000
 * real participating stores; zero merchant fee to accept it): one PERMANENT QR code
 * printed and displayed at the till, the same code used for every sale, with no
 * merchant-side app interaction needed at the moment of sale at all.
 *
 * Genuinely, structurally distinct from itunda's existing merchant QR
 * (`MerchantService.generateQr`) -- not a rename of it. The existing flow is
 * merchant-initiated and per-transaction: the merchant opens the app and generates a
 * fresh, amount-preset `PaymentIntent` for every single sale, which only that one real
 * customer scans and pays. This flow inverts who initiates and when the amount is set:
 * the CUSTOMER scans the merchant's one unchanging `merchantId`-keyed code, types in
 * their own amount, and pays directly -- exactly Kakao's own real target use case, a
 * vendor without a POS (or without an actively open app) at the moment of sale, e.g. a
 * market stall.
 *
 * Deliberately thin: this creates a real `PaymentIntent` (reusing
 * `MerchantService.createIntent`, the same real entity/expiry every existing QR payment
 * already uses) from the customer-provided amount, then immediately hands off to the
 * already-proven `MerchantService.collect` for the actual money movement -- same real
 * fee, fraud, cashback, coupon, webhook, and push-notification handling every other
 * collection channel already gets, none of it duplicated here.
 */
@Service
class MerchantStaticQrService(
    private val merchantRepository: MerchantRepository,
    private val merchantService: MerchantService,
) {
    @Transactional
    fun payByStaticQr(payerUserId: String, merchantId: String, amount: BigDecimal, description: String?): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidStaticQrAmountException("Amount must be greater than zero")
        }
        val merchant = merchantRepository.findById(merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.status != MerchantStatus.ACTIVE) {
            // Real 404 (not 403) -- same "don't reveal a resource exists to someone who
            // shouldn't see it" discipline every other IDOR check in this backend uses.
            throw MerchantNotFoundException("Merchant not found")
        }
        val trimmedDescription = description?.trim()?.take(500)?.ifBlank { null } ?: "Static QR payment"
        val intent = merchantService.createIntent(merchant.id, amount, trimmedDescription)
        return merchantService.collect(payerUserId, intent.id, channel = "STATIC_QR")
    }
}
