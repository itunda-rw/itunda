package rw.itunda.commerce

// Real fix (2026-08-26): split out of OrderService.kt once that file grew past its
// file-size-lint baseline. Pure exception declarations, no behavior -- zero-risk
// mechanical move, same package so no import changes anywhere.

class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoAccountException(message: String) : RuntimeException(message)
class BuyerNoAccountException(message: String) : RuntimeException(message)
class EmptyOrderException(message: String) : RuntimeException(message)
class InvalidDeliveryAddressException(message: String) : RuntimeException(message)
class InvalidQuantityException(message: String) : RuntimeException(message)
class OrderProductNotFoundException(message: String) : RuntimeException(message)
class SelfOrderException(message: String) : RuntimeException(message)
class OrderNotFoundException(message: String) : RuntimeException(message)
class InvalidOrderStatusTransitionException(message: String) : RuntimeException(message)
class RiderNotRegisteredException(message: String) : RuntimeException(message)
class RiderNotAvailableException(message: String) : RuntimeException(message)
class RiderAlreadyOnDeliveryException(message: String) : RuntimeException(message)
class DeliveryAlreadyClaimedException(message: String) : RuntimeException(message)
class MinOrderAmountNotMetException(message: String) : RuntimeException(message)
class InsufficientProductStockException(message: String) : RuntimeException(message)
class ProductSoldOutException(message: String) : RuntimeException(message)
class SurplusDealExpiredException(message: String) : RuntimeException(message)
class MerchantNotAcceptingOrdersException(message: String) : RuntimeException(message)
