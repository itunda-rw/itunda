package rw.itunda.community.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.community.CommunityMeetupJoinException
import rw.itunda.community.CommunityNeighborhoodNotSetException
import rw.itunda.community.CommunityPostNotFoundException
import rw.itunda.community.CommunityService
import rw.itunda.community.InvalidCommunityCommentException
import rw.itunda.community.InvalidCommunityCoordinatesException
import rw.itunda.community.InvalidCommunityPostException
import rw.itunda.community.InvalidGroupBuyFinalizeException
import rw.itunda.community.InvalidMeetupException
import rw.itunda.community.InvalidMeetupScheduleException
import rw.itunda.community.MeetupAttendanceAlreadyCheckedInException
import rw.itunda.community.MeetupAttendanceNotAMemberException
import rw.itunda.community.MeetupFullException
import rw.itunda.community.MeetupSessionNotFoundException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.splitbill.SplitBillDescriptionRequiredException
import rw.itunda.splitbill.SplitBillInvalidAmountException
import rw.itunda.splitbill.SplitBillNeedsParticipantsException
import rw.itunda.splitbill.SplitBillParticipantNotGroupMemberException

/**
 * Exception-handler coverage for CommunityController, split out of
 * CommunityControllerTest.kt (endpoint delegation) to stay under the 500-line
 * guideline, same precedent MapsControllerTest.kt/
 * MapsControllerExceptionHandlingTest.kt already established.
 */
class CommunityControllerExceptionHandlingTest : BehaviorSpec({

    fun controller() = CommunityController(mockk<CommunityService>(), mockk<IdempotencyService>())

    listOf(
        Triple(CommunityMeetupJoinException("Bad request") as RuntimeException, HttpStatus.BAD_REQUEST, "COMMUNITY_MEETUP_JOIN_INVALID"),
        Triple(CommunityPostNotFoundException("Not found"), HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND"),
        Triple(InvalidCommunityPostException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COMMUNITY_POST"),
        Triple(InvalidCommunityCommentException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COMMUNITY_COMMENT"),
        Triple(InvalidCommunityCoordinatesException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(CommunityNeighborhoodNotSetException("Bad request"), HttpStatus.BAD_REQUEST, "NEIGHBORHOOD_NOT_SET"),
        Triple(InvalidMeetupException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_MEETUP"),
        Triple(MeetupFullException("Conflict"), HttpStatus.CONFLICT, "MEETUP_FULL"),
        Triple(InvalidMeetupScheduleException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_MEETUP_SCHEDULE"),
        Triple(MeetupSessionNotFoundException("Not found"), HttpStatus.NOT_FOUND, "MEETUP_SESSION_NOT_FOUND"),
        Triple(MeetupAttendanceAlreadyCheckedInException("Conflict"), HttpStatus.CONFLICT, "ALREADY_CHECKED_IN"),
        Triple(MeetupAttendanceNotAMemberException("Not found"), HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"),
        Triple(InvalidGroupBuyFinalizeException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_GROUP_BUY_FINALIZE"),
        Triple(SplitBillInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(SplitBillDescriptionRequiredException("Bad request"), HttpStatus.BAD_REQUEST, "DESCRIPTION_REQUIRED"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(SplitBillNeedsParticipantsException("Bad request"), HttpStatus.BAD_REQUEST, "NEEDS_PARTICIPANTS"),
        Triple(SplitBillParticipantNotGroupMemberException("Bad request"), HttpStatus.BAD_REQUEST, "PARTICIPANT_NOT_GROUP_MEMBER"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is CommunityMeetupJoinException -> ctl.handleMeetupJoin(exception)
                    is CommunityPostNotFoundException -> ctl.handleNotFound(exception)
                    is InvalidCommunityPostException -> ctl.handleInvalidPost(exception)
                    is InvalidCommunityCommentException -> ctl.handleInvalidComment(exception)
                    is InvalidCommunityCoordinatesException -> ctl.handleInvalidCoordinates(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is CommunityNeighborhoodNotSetException -> ctl.handleNeighborhoodNotSet(exception)
                    is InvalidMeetupException -> ctl.handleInvalidMeetup(exception)
                    is MeetupFullException -> ctl.handleMeetupFull(exception)
                    is InvalidMeetupScheduleException -> ctl.handleInvalidSchedule(exception)
                    is MeetupSessionNotFoundException -> ctl.handleSessionNotFound(exception)
                    is MeetupAttendanceAlreadyCheckedInException -> ctl.handleAlreadyCheckedIn(exception)
                    is MeetupAttendanceNotAMemberException -> ctl.handleNotAMember(exception)
                    is InvalidGroupBuyFinalizeException -> ctl.handleInvalidGroupBuyFinalize(exception)
                    is SplitBillInvalidAmountException -> ctl.handleSplitBillInvalidAmount(exception)
                    is SplitBillDescriptionRequiredException -> ctl.handleSplitBillDescriptionRequired(exception)
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    is SplitBillNeedsParticipantsException -> ctl.handleSplitBillNeedsParticipants(exception)
                    is SplitBillParticipantNotGroupMemberException -> ctl.handleSplitBillParticipantNotGroupMember(exception)
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
