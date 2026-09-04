package rw.itunda.merchant

// Real fix (2026-08-26): split out of MerchantService.kt once that file grew past its
// file-size-lint baseline. Pure exception declarations, no behavior -- zero-risk
// mechanical move, same package so no import changes anywhere.

class MerchantAlreadyRegisteredException(message: String) : RuntimeException(message)
class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoAccountException(message: String) : RuntimeException(message)
class InvalidBusinessNameException(message: String) : RuntimeException(message)
class InvalidCoordinatesException(message: String) : RuntimeException(message)
class InvalidCategoryException(message: String) : RuntimeException(message)
class InvalidClosedWeekdaysException(message: String) : RuntimeException(message)
class InvalidCashbackRateException(message: String) : RuntimeException(message)
class InvalidPhotoUrlException(message: String) : RuntimeException(message)
class InvalidMinOrderAmountException(message: String) : RuntimeException(message)
class InvalidPhoneNumberException(message: String) : RuntimeException(message)
class InvalidOpeningHoursException(message: String) : RuntimeException(message)
class InvalidAvgPrepTimeException(message: String) : RuntimeException(message)
class InvalidPickupDiscountException(message: String) : RuntimeException(message)
class PaymentIntentNotFoundException(message: String) : RuntimeException(message)
class PaymentIntentNotPayableException(message: String) : RuntimeException(message)
class SelfPaymentException(message: String) : RuntimeException(message)
// Real customer-presented payment code (2026-08-11) -- see CustomerPaymentCode.kt's
// own doc comment for the real KakaoPay/Toss Pay flow this closes: customer shows a
// code, merchant scans it, no typing on either side.
class CustomerPaymentCodeNotFoundException(message: String) : RuntimeException(message)
class CustomerPaymentCodeNotPayableException(message: String) : RuntimeException(message)
class PaymentCodeAccountNotEligibleException(message: String) : RuntimeException(message)
class CardDeclinedException(message: String) : RuntimeException(message)
class InvalidWebhookUrlException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class InvalidCheckoutRequestException(message: String) : RuntimeException(message)
class PaymentIntentNotRefundableException(message: String) : RuntimeException(message)
class InvalidCancelRequestException(message: String) : RuntimeException(message)
class InvalidReportRangeException(message: String) : RuntimeException(message)
