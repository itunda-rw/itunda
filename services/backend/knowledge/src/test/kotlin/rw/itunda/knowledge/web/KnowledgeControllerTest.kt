package rw.itunda.knowledge.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.KnowledgeAnswer
import rw.itunda.core.domain.KnowledgeQuestion
import rw.itunda.core.security.CurrentUser
import rw.itunda.knowledge.InvalidKnowledgeAnswerException
import rw.itunda.knowledge.InvalidKnowledgeQuestionException
import rw.itunda.knowledge.KnowledgeAnswerNotForQuestionException
import rw.itunda.knowledge.KnowledgeAnswerNotFoundException
import rw.itunda.knowledge.KnowledgeQuestionAlreadyHasAdoptedAnswerException
import rw.itunda.knowledge.KnowledgeQuestionNotFoundException
import rw.itunda.knowledge.KnowledgeService

/**
 * First test coverage for KnowledgeController's REST layer -- same gap class the
 * Certificate/Vehicle/Partners/Identity/Overview passes already found and fixed
 * once each. Covers controller-to-service delegation (the real caller-scoped
 * userId is always used, never a client-supplied one) and every real
 * @ExceptionHandler mapping.
 */
class KnowledgeControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a real question-post request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val question = KnowledgeQuestion(id = "knowledge_question_1", askerId = "user_1", category = "money", title = "Q", body = "B")
        every { service.postQuestion("user_1", "money", "Q", "B") } returns question

        When("posting") {
            val response = controller.postQuestion(PostKnowledgeQuestionRequest("money", "Q", "B"), currentUser)

            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { service.postQuestion("user_1", "money", "Q", "B") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("question") shouldBe question
            }
        }
    }

    Given("a real browse request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val page: Page<KnowledgeQuestion> = PageImpl(listOf(KnowledgeQuestion(id = "q1", askerId = "asker_1", category = "money", title = "Q", body = "B")))
        val pageable = PageRequest.of(0, 20)
        every { service.browse(pageable, "money") } returns page

        When("browsing a category") {
            val response = controller.browse("money", pageable)

            Then("it delegates with the requested category") {
                verify(exactly = 1) { service.browse(pageable, "money") }
                response.body?.get("questions") shouldBe page.content
            }
        }
    }

    Given("a real request for the caller's own questions") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val page: Page<KnowledgeQuestion> = PageImpl(listOf(KnowledgeQuestion(id = "q1", askerId = "user_1", category = "money", title = "Q", body = "B")))
        val pageable = PageRequest.of(0, 20)
        every { service.getMyQuestions("user_1", pageable) } returns page

        When("fetching them") {
            val response = controller.getMyQuestions(pageable, currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyQuestions("user_1", pageable) }
                response.body?.get("questions") shouldBe page.content
            }
        }
    }

    Given("a real request for the caller's own answers") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val page: Page<KnowledgeAnswer> = PageImpl(listOf(KnowledgeAnswer(id = "a1", questionId = "q1", answererId = "user_1", body = "A")))
        val pageable = PageRequest.of(0, 20)
        every { service.getMyAnswers("user_1", pageable) } returns page

        When("fetching them") {
            val response = controller.getMyAnswers(pageable, currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyAnswers("user_1", pageable) }
                response.body?.get("answers") shouldBe page.content
            }
        }
    }

    Given("a real reputation request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        every { service.getMyReputation("user_1") } returns 3L

        When("fetching it") {
            val response = controller.getMyReputation(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyReputation("user_1") }
                response.body?.get("adoptedAnswerCount") shouldBe 3L
            }
        }
    }

    Given("a real question-detail request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val question = KnowledgeQuestion(id = "q1", askerId = "asker_1", category = "money", title = "Q", body = "B")
        every { service.getQuestion("q1") } returns question

        When("fetching it") {
            val response = controller.getQuestion("q1")

            Then("it delegates by the requested id") {
                verify(exactly = 1) { service.getQuestion("q1") }
                response.body?.get("question") shouldBe question
            }
        }
    }

    Given("a real answers-for-question request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val answers = listOf(KnowledgeAnswer(id = "a1", questionId = "q1", answererId = "answerer_1", body = "A"))
        every { service.getAnswers("q1") } returns answers

        When("fetching them") {
            val response = controller.getAnswers("q1")

            Then("it delegates by the requested question id") {
                verify(exactly = 1) { service.getAnswers("q1") }
                response.body?.get("answers") shouldBe answers
            }
        }
    }

    Given("a real answer-post request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val answer = KnowledgeAnswer(id = "a1", questionId = "q1", answererId = "user_1", body = "A")
        every { service.postAnswer("user_1", "q1", "A") } returns answer

        When("posting") {
            val response = controller.postAnswer("q1", PostKnowledgeAnswerRequest("A"), currentUser)

            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { service.postAnswer("user_1", "q1", "A") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("answer") shouldBe answer
            }
        }
    }

    Given("a real adopt request") {
        val service = mockk<KnowledgeService>()
        val controller = KnowledgeController(service)
        val answer = KnowledgeAnswer(id = "a1", questionId = "q1", answererId = "answerer_1", body = "A", isAdopted = true)
        every { service.adoptAnswer("user_1", "q1", "a1") } returns answer

        When("adopting") {
            val response = controller.adoptAnswer("q1", "a1", currentUser)

            Then("it real-delegates scoped to the caller's own userId as the asker") {
                verify(exactly = 1) { service.adoptAnswer("user_1", "q1", "a1") }
                response.body?.get("answer") shouldBe answer
            }
        }
    }

    Given("a real categories request") {
        val controller = KnowledgeController(mockk())

        When("fetching them") {
            val response = controller.getCategories()

            Then("the real fixed category list is returned, unauthenticated") {
                response.body?.get("categories") shouldBe KnowledgeService.CATEGORIES
            }
        }
    }

    listOf(
        Triple(InvalidKnowledgeQuestionException("Bad request") as RuntimeException, HttpStatus.BAD_REQUEST, "INVALID_KNOWLEDGE_QUESTION"),
        Triple(InvalidKnowledgeAnswerException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_KNOWLEDGE_ANSWER"),
        Triple(KnowledgeQuestionNotFoundException("Not found"), HttpStatus.NOT_FOUND, "KNOWLEDGE_QUESTION_NOT_FOUND"),
        Triple(KnowledgeAnswerNotFoundException("Not found"), HttpStatus.NOT_FOUND, "KNOWLEDGE_ANSWER_NOT_FOUND"),
        Triple(KnowledgeAnswerNotForQuestionException("Bad request"), HttpStatus.BAD_REQUEST, "KNOWLEDGE_ANSWER_NOT_FOR_QUESTION"),
        Triple(KnowledgeQuestionAlreadyHasAdoptedAnswerException("Conflict"), HttpStatus.CONFLICT, "KNOWLEDGE_QUESTION_ALREADY_HAS_ADOPTED_ANSWER"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val controller = KnowledgeController(mockk())

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is InvalidKnowledgeQuestionException -> controller.handleInvalidQuestion(exception)
                    is InvalidKnowledgeAnswerException -> controller.handleInvalidAnswer(exception)
                    is KnowledgeQuestionNotFoundException -> controller.handleQuestionNotFound(exception)
                    is KnowledgeAnswerNotFoundException -> controller.handleAnswerNotFound(exception)
                    is KnowledgeAnswerNotForQuestionException -> controller.handleAnswerNotForQuestion(exception)
                    is KnowledgeQuestionAlreadyHasAdoptedAnswerException -> controller.handleAlreadyAdopted(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
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
