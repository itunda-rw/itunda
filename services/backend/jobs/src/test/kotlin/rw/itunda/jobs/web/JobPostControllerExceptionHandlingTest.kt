package rw.itunda.jobs.web

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
import rw.itunda.jobs.FavoriteJobPostNotFoundException
import rw.itunda.jobs.InvalidJobApplicationException
import rw.itunda.jobs.InvalidJobCoordinatesException
import rw.itunda.jobs.InvalidJobPostException
import rw.itunda.jobs.JobApplicationAlreadyPendingException
import rw.itunda.jobs.JobApplicationNotFoundException
import rw.itunda.jobs.JobApplicationNotPendingException
import rw.itunda.jobs.JobApplicationService
import rw.itunda.jobs.JobPostFavoriteService
import rw.itunda.jobs.JobPostNotFoundException
import rw.itunda.jobs.JobPostNotOpenException
import rw.itunda.jobs.JobPostService
import rw.itunda.jobs.JobsNeighborhoodNotSetException
import rw.itunda.jobs.OwnJobPostException
import rw.itunda.jobs.WorkerNotFoundException

/**
 * Exception-handler coverage for JobPostController, split out of
 * JobPostControllerTest.kt (endpoint delegation) to stay under the 500-line
 * guideline, same precedent MapsControllerTest.kt/
 * MapsControllerExceptionHandlingTest.kt already established.
 */
class JobPostControllerExceptionHandlingTest : BehaviorSpec({

    fun controller() = JobPostController(
        mockk<JobPostService>(), mockk<JobPostFavoriteService>(), mockk<UserRepository>(),
        mockk<HoodReviewService>(), mockk<JobApplicationService>(),
    )

    listOf(
        Triple(JobPostNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "JOB_POST_NOT_FOUND"),
        Triple(InvalidJobPostException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_JOB_POST"),
        Triple(JobPostNotOpenException("Conflict"), HttpStatus.CONFLICT, "JOB_POST_NOT_OPEN"),
        Triple(OwnJobPostException("Bad request"), HttpStatus.BAD_REQUEST, "OWN_JOB_POST"),
        Triple(InvalidJobCoordinatesException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(JobsNeighborhoodNotSetException("Bad request"), HttpStatus.BAD_REQUEST, "NEIGHBORHOOD_NOT_SET"),
        Triple(FavoriteJobPostNotFoundException("Not found"), HttpStatus.NOT_FOUND, "JOB_POST_NOT_FOUND"),
        Triple(WorkerNotFoundException("Not found"), HttpStatus.NOT_FOUND, "WORKER_NOT_FOUND"),
        Triple(HoodReviewTransactionNotFoundException("Not found"), HttpStatus.NOT_FOUND, "JOB_POST_NOT_FOUND"),
        Triple(HoodReviewTransactionNotCompletedException("Conflict"), HttpStatus.CONFLICT, "REVIEW_TRANSACTION_NOT_COMPLETED"),
        Triple(HoodReviewNoCounterpartyException("Bad request"), HttpStatus.BAD_REQUEST, "REVIEW_NO_COUNTERPARTY"),
        Triple(HoodReviewNotPartyException("Not found"), HttpStatus.NOT_FOUND, "REVIEW_NOT_PARTY"),
        Triple(HoodReviewAlreadySubmittedException("Conflict"), HttpStatus.CONFLICT, "REVIEW_ALREADY_SUBMITTED"),
        Triple(InvalidJobApplicationException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_JOB_APPLICATION"),
        Triple(JobApplicationAlreadyPendingException("Conflict"), HttpStatus.CONFLICT, "JOB_APPLICATION_ALREADY_PENDING"),
        Triple(JobApplicationNotFoundException("Not found"), HttpStatus.NOT_FOUND, "JOB_APPLICATION_NOT_FOUND"),
        Triple(JobApplicationNotPendingException("Conflict"), HttpStatus.CONFLICT, "JOB_APPLICATION_NOT_PENDING"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is JobPostNotFoundException -> ctl.handleNotFound(exception)
                    is InvalidJobPostException -> ctl.handleInvalid(exception)
                    is JobPostNotOpenException -> ctl.handleNotOpen(exception)
                    is OwnJobPostException -> ctl.handleOwnPost(exception)
                    is InvalidJobCoordinatesException -> ctl.handleInvalidCoordinates(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is JobsNeighborhoodNotSetException -> ctl.handleNeighborhoodNotSet(exception)
                    is FavoriteJobPostNotFoundException -> ctl.handleFavoriteNotFound(exception)
                    is WorkerNotFoundException -> ctl.handleWorkerNotFound(exception)
                    is HoodReviewTransactionNotFoundException -> ctl.handleReviewTransactionNotFound(exception)
                    is HoodReviewTransactionNotCompletedException -> ctl.handleReviewTransactionNotCompleted(exception)
                    is HoodReviewNoCounterpartyException -> ctl.handleReviewNoCounterparty(exception)
                    is HoodReviewNotPartyException -> ctl.handleReviewNotParty(exception)
                    is HoodReviewAlreadySubmittedException -> ctl.handleReviewAlreadySubmitted(exception)
                    is InvalidJobApplicationException -> ctl.handleInvalidApplication(exception)
                    is JobApplicationAlreadyPendingException -> ctl.handleApplicationAlreadyPending(exception)
                    is JobApplicationNotFoundException -> ctl.handleApplicationNotFound(exception)
                    is JobApplicationNotPendingException -> ctl.handleApplicationNotPending(exception)
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
