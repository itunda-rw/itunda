package rw.itunda.realestate.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewAlreadySubmittedException
import rw.itunda.core.review.HoodReviewNoCounterpartyException
import rw.itunda.core.review.HoodReviewNotPartyException
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.review.HoodReviewTransactionNotCompletedException
import rw.itunda.core.review.HoodReviewTransactionNotFoundException
import rw.itunda.realestate.CounterpartyNotFoundException
import rw.itunda.realestate.FavoritePropertyListingNotFoundException
import rw.itunda.realestate.InsufficientComparablesException
import rw.itunda.realestate.InvalidPropertyCoordinatesException
import rw.itunda.realestate.InvalidPropertyListingException
import rw.itunda.realestate.InvalidPropertyOfferAmountException
import rw.itunda.realestate.OwnPropertyListingException
import rw.itunda.realestate.OwnPropertyOfferException
import rw.itunda.realestate.PropertyListingFavoriteService
import rw.itunda.realestate.PropertyListingNotAvailableException
import rw.itunda.realestate.PropertyListingNotFoundException
import rw.itunda.realestate.PropertyListingService
import rw.itunda.realestate.PropertyOfferAlreadyResolvedException
import rw.itunda.realestate.PropertyOfferNotFoundException
import rw.itunda.realestate.PropertyOwnershipService
import rw.itunda.realestate.PropertyOwnershipSubmissionAlreadyPendingException
import rw.itunda.realestate.PropertyPriceOfferService
import rw.itunda.realestate.RealEstateNeighborhoodNotSetException

/**
 * Exception-handler coverage for PropertyListingController, split out of
 * PropertyListingControllerTest.kt (endpoint delegation) to stay under the
 * 500-line guideline, same precedent MapsControllerTest.kt/
 * MapsControllerExceptionHandlingTest.kt already established.
 */
class PropertyListingControllerExceptionHandlingTest : BehaviorSpec({

    fun controller() = PropertyListingController(
        mockk<PropertyListingService>(), mockk<PropertyPriceOfferService>(), mockk<PropertyListingFavoriteService>(),
        mockk<UserRepository>(), mockk<HoodReviewService>(), mockk<PropertyOwnershipService>(),
    )

    listOf(
        Triple(PropertyOfferNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"),
        Triple(InvalidPropertyOfferAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_OFFER_AMOUNT"),
        Triple(PropertyOfferAlreadyResolvedException("Conflict"), HttpStatus.CONFLICT, "OFFER_ALREADY_RESOLVED"),
        Triple(OwnPropertyOfferException("Bad request"), HttpStatus.BAD_REQUEST, "OWN_OFFER"),
        Triple(PropertyListingNotFoundException("Not found"), HttpStatus.NOT_FOUND, "PROPERTY_LISTING_NOT_FOUND"),
        Triple(InvalidPropertyListingException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_PROPERTY_LISTING"),
        Triple(PropertyListingNotAvailableException("Conflict"), HttpStatus.CONFLICT, "PROPERTY_LISTING_NOT_AVAILABLE"),
        Triple(OwnPropertyListingException("Bad request"), HttpStatus.BAD_REQUEST, "OWN_PROPERTY_LISTING"),
        Triple(InvalidPropertyCoordinatesException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(RealEstateNeighborhoodNotSetException("Bad request"), HttpStatus.BAD_REQUEST, "NEIGHBORHOOD_NOT_SET"),
        Triple(FavoritePropertyListingNotFoundException("Not found"), HttpStatus.NOT_FOUND, "PROPERTY_LISTING_NOT_FOUND"),
        Triple(CounterpartyNotFoundException("Not found"), HttpStatus.NOT_FOUND, "COUNTERPARTY_NOT_FOUND"),
        Triple(HoodReviewTransactionNotFoundException("Not found"), HttpStatus.NOT_FOUND, "PROPERTY_LISTING_NOT_FOUND"),
        Triple(HoodReviewTransactionNotCompletedException("Conflict"), HttpStatus.CONFLICT, "REVIEW_TRANSACTION_NOT_COMPLETED"),
        Triple(HoodReviewNoCounterpartyException("Bad request"), HttpStatus.BAD_REQUEST, "REVIEW_NO_COUNTERPARTY"),
        Triple(HoodReviewNotPartyException("Not found"), HttpStatus.NOT_FOUND, "REVIEW_NOT_PARTY"),
        Triple(HoodReviewAlreadySubmittedException("Conflict"), HttpStatus.CONFLICT, "REVIEW_ALREADY_SUBMITTED"),
        Triple(PropertyOwnershipSubmissionAlreadyPendingException("Conflict"), HttpStatus.CONFLICT, "OWNERSHIP_VERIFICATION_ALREADY_PENDING"),
        Triple(InsufficientComparablesException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_COMPARABLES"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is PropertyOfferNotFoundException -> ctl.handleOfferNotFound(exception)
                    is InvalidPropertyOfferAmountException -> ctl.handleInvalidOfferAmount(exception)
                    is PropertyOfferAlreadyResolvedException -> ctl.handleOfferAlreadyResolved(exception)
                    is OwnPropertyOfferException -> ctl.handleOwnOffer(exception)
                    is PropertyListingNotFoundException -> ctl.handleNotFound(exception)
                    is InvalidPropertyListingException -> ctl.handleInvalid(exception)
                    is PropertyListingNotAvailableException -> ctl.handleNotAvailable(exception)
                    is OwnPropertyListingException -> ctl.handleOwnListing(exception)
                    is InvalidPropertyCoordinatesException -> ctl.handleInvalidCoordinates(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is RealEstateNeighborhoodNotSetException -> ctl.handleNeighborhoodNotSet(exception)
                    is FavoritePropertyListingNotFoundException -> ctl.handleFavoriteNotFound(exception)
                    is CounterpartyNotFoundException -> ctl.handleCounterpartyNotFound(exception)
                    is HoodReviewTransactionNotFoundException -> ctl.handleReviewTransactionNotFound(exception)
                    is HoodReviewTransactionNotCompletedException -> ctl.handleReviewTransactionNotCompleted(exception)
                    is HoodReviewNoCounterpartyException -> ctl.handleReviewNoCounterparty(exception)
                    is HoodReviewNotPartyException -> ctl.handleReviewNotParty(exception)
                    is HoodReviewAlreadySubmittedException -> ctl.handleReviewAlreadySubmitted(exception)
                    is PropertyOwnershipSubmissionAlreadyPendingException -> ctl.handleOwnershipAlreadyPending(exception)
                    is InsufficientComparablesException -> ctl.handleInsufficientComparables(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
