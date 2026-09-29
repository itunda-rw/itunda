package rw.itunda.marketplace.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewAlreadySubmittedException
import rw.itunda.core.review.HoodReviewNoCounterpartyException
import rw.itunda.core.review.HoodReviewNotPartyException
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.review.HoodReviewTransactionNotCompletedException
import rw.itunda.core.review.HoodReviewTransactionNotFoundException
import rw.itunda.marketplace.BuyerNoAccountException
import rw.itunda.marketplace.BuyerNotFoundException
import rw.itunda.marketplace.FavoriteListingNotFoundException
import rw.itunda.marketplace.HideListingNotFoundException
import rw.itunda.marketplace.InvalidBoostDurationException
import rw.itunda.marketplace.InvalidCoordinatesException
import rw.itunda.marketplace.InvalidDisputeReasonException
import rw.itunda.marketplace.InvalidEscrowStatusException
import rw.itunda.marketplace.InvalidListingException
import rw.itunda.marketplace.InvalidListingPriceException
import rw.itunda.marketplace.InvalidOfferAmountException
import rw.itunda.marketplace.KeywordAlertService
import rw.itunda.marketplace.ListingBumpCooldownException
import rw.itunda.marketplace.ListingFavoriteService
import rw.itunda.marketplace.ListingHideService
import rw.itunda.marketplace.ListingNotActiveException
import rw.itunda.marketplace.ListingNotFoundException
import rw.itunda.marketplace.MarketplaceEscrowNotFoundException
import rw.itunda.marketplace.MarketplaceService
import rw.itunda.marketplace.NeighborhoodNotSetException
import rw.itunda.marketplace.OfferAlreadyResolvedException
import rw.itunda.marketplace.OwnListingException
import rw.itunda.marketplace.OwnOfferException
import rw.itunda.marketplace.PriceOfferNotFoundException
import rw.itunda.marketplace.PriceOfferService
import rw.itunda.marketplace.SellerNoAccountException

/**
 * Exception-handler coverage for MarketplaceController, split out of
 * MarketplaceControllerTest.kt (endpoint delegation) to stay under the 500-line
 * guideline, same precedent MapsControllerTest.kt/
 * MapsControllerExceptionHandlingTest.kt already established.
 */
class MarketplaceControllerExceptionHandlingTest : BehaviorSpec({

    fun controller() = MarketplaceController(
        mockk<MarketplaceService>(), mockk<PriceOfferService>(), mockk<ListingFavoriteService>(),
        mockk<ListingHideService>(), mockk<UserRepository>(), mockk<HoodReviewService>(),
        mockk<IdempotencyService>(), mockk<KeywordAlertService>(),
    )

    listOf(
        Triple(PriceOfferNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"),
        Triple(InvalidOfferAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_OFFER_AMOUNT"),
        Triple(OfferAlreadyResolvedException("Conflict"), HttpStatus.CONFLICT, "OFFER_ALREADY_RESOLVED"),
        Triple(OwnOfferException("Bad request"), HttpStatus.BAD_REQUEST, "OWN_OFFER"),
        Triple(ListingNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND"),
        Triple(InvalidListingException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_LISTING"),
        Triple(InvalidListingPriceException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_LISTING_PRICE"),
        Triple(ListingNotActiveException("Conflict"), HttpStatus.CONFLICT, "LISTING_NOT_ACTIVE"),
        Triple(OwnListingException("Bad request"), HttpStatus.BAD_REQUEST, "OWN_LISTING"),
        Triple(ListingBumpCooldownException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "BUMP_COOLDOWN"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(InvalidCoordinatesException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES"),
        Triple(NeighborhoodNotSetException("Bad request"), HttpStatus.BAD_REQUEST, "NEIGHBORHOOD_NOT_SET"),
        Triple(FavoriteListingNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND"),
        Triple(HideListingNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND"),
        Triple(BuyerNotFoundException("Not found"), HttpStatus.NOT_FOUND, "BUYER_NOT_FOUND"),
        Triple(HoodReviewTransactionNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND"),
        Triple(HoodReviewTransactionNotCompletedException("Conflict"), HttpStatus.CONFLICT, "REVIEW_TRANSACTION_NOT_COMPLETED"),
        Triple(HoodReviewNoCounterpartyException("Bad request"), HttpStatus.BAD_REQUEST, "REVIEW_NO_COUNTERPARTY"),
        Triple(HoodReviewNotPartyException("Not found"), HttpStatus.NOT_FOUND, "REVIEW_NOT_PARTY"),
        Triple(HoodReviewAlreadySubmittedException("Conflict"), HttpStatus.CONFLICT, "REVIEW_ALREADY_SUBMITTED"),
        Triple(InvalidBoostDurationException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_BOOST_DURATION"),
        Triple(SellerNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(BuyerNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(MarketplaceEscrowNotFoundException("Not found"), HttpStatus.NOT_FOUND, "ESCROW_NOT_FOUND"),
        Triple(InvalidEscrowStatusException("Conflict"), HttpStatus.CONFLICT, "INVALID_ESCROW_STATUS"),
        Triple(InvalidDisputeReasonException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_DISPUTE_REASON"),
        Triple(InsufficientFundsException("Insufficient"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(AccountFrozenException("Frozen"), HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is PriceOfferNotFoundException -> ctl.handleOfferNotFound(exception)
                    is InvalidOfferAmountException -> ctl.handleInvalidOfferAmount(exception)
                    is OfferAlreadyResolvedException -> ctl.handleOfferAlreadyResolved(exception)
                    is OwnOfferException -> ctl.handleOwnOffer(exception)
                    is ListingNotFoundException -> ctl.handleNotFound(exception)
                    is InvalidListingException -> ctl.handleInvalid(exception)
                    is InvalidListingPriceException -> ctl.handleInvalidPrice(exception)
                    is ListingNotActiveException -> ctl.handleNotActive(exception)
                    is OwnListingException -> ctl.handleOwnListing(exception)
                    is ListingBumpCooldownException -> ctl.handleBumpCooldown(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is InvalidCoordinatesException -> ctl.handleInvalidCoordinates(exception)
                    is NeighborhoodNotSetException -> ctl.handleNeighborhoodNotSet(exception)
                    is FavoriteListingNotFoundException -> ctl.handleFavoriteListingNotFound(exception)
                    is HideListingNotFoundException -> ctl.handleHideListingNotFound(exception)
                    is BuyerNotFoundException -> ctl.handleBuyerNotFound(exception)
                    is HoodReviewTransactionNotFoundException -> ctl.handleReviewTransactionNotFound(exception)
                    is HoodReviewTransactionNotCompletedException -> ctl.handleReviewTransactionNotCompleted(exception)
                    is HoodReviewNoCounterpartyException -> ctl.handleReviewNoCounterparty(exception)
                    is HoodReviewNotPartyException -> ctl.handleReviewNotParty(exception)
                    is HoodReviewAlreadySubmittedException -> ctl.handleReviewAlreadySubmitted(exception)
                    is InvalidBoostDurationException -> ctl.handleInvalidBoostDuration(exception)
                    is SellerNoAccountException -> ctl.handleSellerNoAccount(exception)
                    is BuyerNoAccountException -> ctl.handleBuyerNoAccount(exception)
                    is MarketplaceEscrowNotFoundException -> ctl.handleEscrowNotFound(exception)
                    is InvalidEscrowStatusException -> ctl.handleInvalidEscrowStatus(exception)
                    is InvalidDisputeReasonException -> ctl.handleInvalidDisputeReason(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is AccountFrozenException -> ctl.handleAccountFrozen(exception)
                    is IdempotencyConflictException -> ctl.handleIdempotencyConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleIdempotencyInProgress(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }

    Given("a real MissingRequestHeaderException") {
        val ctl = controller()

        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleMissingHeader(mockk<MissingRequestHeaderException>(relaxed = true))

            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
