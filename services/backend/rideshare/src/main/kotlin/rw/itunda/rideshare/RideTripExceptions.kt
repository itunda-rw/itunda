package rw.itunda.rideshare

// Real fix (2026-08-26): split out of RideTripService.kt once that file grew past its
// file-size-lint baseline. Pure exception declarations, no behavior -- zero-risk
// mechanical move, same package so no import changes anywhere.

class InvalidRideLocationException(message: String) : RuntimeException(message)
class RideSelfTripException(message: String) : RuntimeException(message)
class RideTripNotFoundException(message: String) : RuntimeException(message)
class RideTripAlreadyClaimedException(message: String) : RuntimeException(message)
class RideDriverAlreadyOnTripException(message: String) : RuntimeException(message)
class RideDriverNotAvailableException(message: String) : RuntimeException(message)
class InvalidRideTripStatusTransitionException(message: String) : RuntimeException(message)
class RideNoActiveOfferException(message: String) : RuntimeException(message)
class InvalidScheduledRideTimeException(message: String) : RuntimeException(message)
class RideTooManyStopsException(message: String) : RuntimeException(message)
class RideNoRemainingStopsException(message: String) : RuntimeException(message)
class RidePinMismatchException(message: String) : RuntimeException(message)
class InvalidEarningsRangeException(message: String) : RuntimeException(message)
class RideTripNotCompletedException(message: String) : RuntimeException(message)
class RideTripAlreadyTippedException(message: String) : RuntimeException(message)
class RideTripTipWindowExpiredException(message: String) : RuntimeException(message)
class InvalidTipAmountException(message: String) : RuntimeException(message)
