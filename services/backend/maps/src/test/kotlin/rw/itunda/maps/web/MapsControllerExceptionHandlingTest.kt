package rw.itunda.maps.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.maps.BookmarkNotFoundException
import rw.itunda.maps.InvalidBookmarkColorException
import rw.itunda.maps.InvalidBookmarkFolderException
import rw.itunda.maps.InvalidBookmarkNameException
import rw.itunda.maps.InvalidLiveLocationCoordinateException
import rw.itunda.maps.InvalidLiveLocationShareDurationException
import rw.itunda.maps.InvalidMapsCategoryException
import rw.itunda.maps.InvalidMapsCoordinateException
import rw.itunda.maps.InvalidMapsItineraryException
import rw.itunda.maps.LiveLocationShareEndedException
import rw.itunda.maps.LiveLocationShareNotFoundException
import rw.itunda.maps.LiveLocationShareRecipientNotFoundException
import rw.itunda.maps.LiveLocationShareSelfException
import rw.itunda.maps.LiveLocationShareService
import rw.itunda.maps.LiveLocationTooManyActiveSharesException
import rw.itunda.maps.MapsPlaceDetailService
import rw.itunda.maps.MapsService
import rw.itunda.maps.RouteNotFoundException
import rw.itunda.merchant.MerchantNotFoundException

/**
 * Exception-handler coverage for MapsController, split out of MapsControllerTest.kt
 * (endpoint delegation) once that file crossed the 500-line guideline -- same
 * "extract before growing further" precedent LoansServiceRateLimitTest.kt already
 * established for this sweep.
 */
class MapsControllerExceptionHandlingTest : BehaviorSpec({

    fun controller(
        mapsService: MapsService = mockk(),
        liveLocationShareService: LiveLocationShareService = mockk(),
        mapsPlaceDetailService: MapsPlaceDetailService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = MapsController(mapsService, liveLocationShareService, mapsPlaceDetailService, idempotencyService)

    listOf(
        Triple(LiveLocationShareNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "LOCATION_SHARE_NOT_FOUND"),
        Triple(LiveLocationShareSelfException("Bad request"), HttpStatus.BAD_REQUEST, "SELF_LOCATION_SHARE_NOT_ALLOWED"),
        Triple(LiveLocationShareRecipientNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LOCATION_SHARE_RECIPIENT_NOT_FOUND"),
        Triple(LiveLocationTooManyActiveSharesException("Bad request"), HttpStatus.BAD_REQUEST, "TOO_MANY_ACTIVE_LOCATION_SHARES"),
        Triple(InvalidLiveLocationShareDurationException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_LOCATION_SHARE_DURATION"),
        Triple(LiveLocationShareEndedException("Gone"), HttpStatus.GONE, "LOCATION_SHARE_ENDED"),
        Triple(InvalidLiveLocationCoordinateException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES"),
        Triple(InvalidMapsCoordinateException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES"),
        Triple(MerchantNotFoundException("Not found"), HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND"),
        Triple(InvalidMapsItineraryException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_ITINERARY"),
        Triple(InvalidMapsCategoryException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_CATEGORY"),
        Triple(InvalidBookmarkNameException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_BOOKMARK_NAME"),
        Triple(InvalidBookmarkFolderException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_BOOKMARK_FOLDER"),
        Triple(InvalidBookmarkColorException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_BOOKMARK_COLOR"),
        Triple(BookmarkNotFoundException("Not found"), HttpStatus.NOT_FOUND, "BOOKMARK_NOT_FOUND"),
        Triple(RouteNotFoundException("Not found"), HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is LiveLocationShareNotFoundException -> ctl.handleLiveLocationShareNotFound(exception)
                    is LiveLocationShareSelfException -> ctl.handleLiveLocationShareSelf(exception)
                    is LiveLocationShareRecipientNotFoundException -> ctl.handleLiveLocationShareRecipientNotFound(exception)
                    is LiveLocationTooManyActiveSharesException -> ctl.handleLiveLocationTooManyActiveShares(exception)
                    is InvalidLiveLocationShareDurationException -> ctl.handleInvalidLiveLocationShareDuration(exception)
                    is LiveLocationShareEndedException -> ctl.handleLiveLocationShareEnded(exception)
                    is InvalidLiveLocationCoordinateException -> ctl.handleInvalidLiveLocationCoordinate(exception)
                    is InvalidMapsCoordinateException -> ctl.handleInvalidCoordinate(exception)
                    is MerchantNotFoundException -> ctl.handleMerchantNotFound(exception)
                    is InvalidMapsItineraryException -> ctl.handleInvalidItinerary(exception)
                    is InvalidMapsCategoryException -> ctl.handleInvalidCategory(exception)
                    is InvalidBookmarkNameException -> ctl.handleInvalidBookmarkName(exception)
                    is InvalidBookmarkFolderException -> ctl.handleInvalidBookmarkFolder(exception)
                    is InvalidBookmarkColorException -> ctl.handleInvalidBookmarkColor(exception)
                    is BookmarkNotFoundException -> ctl.handleBookmarkNotFound(exception)
                    is RouteNotFoundException -> ctl.handleRouteNotFound(exception)
                    is IdempotencyConflictException -> ctl.handleIdempotencyConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleIdempotencyInProgress(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
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

    // Real bug fix (Maps product-completeness pass, 2026-09-07) -- this message used to
    // say "mode must be DRIVING or WALKING," omitting the fully-supported third BIKING
    // mode (added 2026-08-28).
    Given("a real invalid travel-mode value") {
        val ctl = controller()

        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleInvalidMode(mockk<MethodArgumentTypeMismatchException>(relaxed = true))

            Then("it maps to 400 with a message naming all 3 real supported modes, not a stale 2-mode list") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "INVALID_TRAVEL_MODE"
                response.body?.message shouldBe "mode must be DRIVING, WALKING, or BIKING"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
