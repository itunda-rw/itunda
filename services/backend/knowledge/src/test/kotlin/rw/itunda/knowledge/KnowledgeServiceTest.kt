package rw.itunda.knowledge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.KnowledgeAnswer
import rw.itunda.core.domain.KnowledgeQuestion
import rw.itunda.core.repository.KnowledgeAnswerRepository
import rw.itunda.core.repository.KnowledgeQuestionRepository
import java.util.Optional

/**
 * First test coverage for real Naver 지식iN (Knowledge iN)-style open-topic Q&A -- see
 * KnowledgeService's own doc comment for the full sourced account. Mirrors this
 * session's established BehaviorSpec/mockk conventions (e.g. BusServiceTest).
 */
class KnowledgeServiceTest : BehaviorSpec({

    fun newService(
        questionRepository: KnowledgeQuestionRepository = mockk(),
        answerRepository: KnowledgeAnswerRepository = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = KnowledgeService(questionRepository, answerRepository, rateLimiter)

    Given("a real user posting a real question") {
        val questionRepository = mockk<KnowledgeQuestionRepository>()
        val savedSlot = slot<KnowledgeQuestion>()
        every { questionRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(questionRepository = questionRepository)

        When("posting a real question in a known category") {
            val result = service.postQuestion("asker_1", "money", "How do I open a savings goal?", "I want to save for a trip.")

            Then("a real question row is saved with no adopted answer yet") {
                result.askerId shouldBe "asker_1"
                result.category shouldBe "money"
                result.adoptedAnswerId shouldBe null
                savedSlot.captured.title shouldBe "How do I open a savings goal?"
            }
        }
    }

    Given("a real question with no adopted answer yet") {
        val questionRepository = mockk<KnowledgeQuestionRepository>()
        val answerRepository = mockk<KnowledgeAnswerRepository>()
        val service = newService(questionRepository = questionRepository, answerRepository = answerRepository)

        val question = KnowledgeQuestion(id = "knowledge_question_1", askerId = "asker_1", category = "money", title = "Q", body = "B")
        val answer1 = KnowledgeAnswer(id = "knowledge_answer_1", questionId = "knowledge_question_1", answererId = "answerer_1", body = "A1")

        every { questionRepository.findById("knowledge_question_1") } returns Optional.of(question)
        every { answerRepository.findById("knowledge_answer_1") } returns Optional.of(answer1)
        every { answerRepository.save(any()) } answers { firstArg() }
        every { questionRepository.save(any()) } answers { firstArg() }

        When("a stranger (not the asker) tries to adopt an answer") {
            Then("a real not-found response fires, not a 403 that would confirm the question exists") {
                try {
                    service.adoptAnswer("someone_else", "knowledge_question_1", "knowledge_answer_1")
                    throw AssertionError("expected KnowledgeQuestionNotFoundException")
                } catch (e: KnowledgeQuestionNotFoundException) {
                    e.message shouldBe "Question not found"
                }
            }
        }

        When("the real asker adopts a real answer") {
            val result = service.adoptAnswer("asker_1", "knowledge_question_1", "knowledge_answer_1")

            Then("the answer is marked adopted and the question records it") {
                result.isAdopted shouldBe true
                question.adoptedAnswerId shouldBe "knowledge_answer_1"
            }
        }
    }

    Given("a real question that already has an adopted answer") {
        val questionRepository = mockk<KnowledgeQuestionRepository>()
        val answerRepository = mockk<KnowledgeAnswerRepository>()
        val service = newService(questionRepository = questionRepository, answerRepository = answerRepository)

        val question = KnowledgeQuestion(
            id = "knowledge_question_1", askerId = "asker_1", category = "money", title = "Q", body = "B",
            adoptedAnswerId = "knowledge_answer_1",
        )
        val answer2 = KnowledgeAnswer(id = "knowledge_answer_2", questionId = "knowledge_question_1", answererId = "answerer_2", body = "A2")

        every { questionRepository.findById("knowledge_question_1") } returns Optional.of(question)
        every { answerRepository.findById("knowledge_answer_2") } returns Optional.of(answer2)

        When("the asker tries to adopt a second answer for the same question") {
            Then("a real conflict fires") {
                try {
                    service.adoptAnswer("asker_1", "knowledge_question_1", "knowledge_answer_2")
                    throw AssertionError("expected KnowledgeQuestionAlreadyHasAdoptedAnswerException")
                } catch (e: KnowledgeQuestionAlreadyHasAdoptedAnswerException) {
                    e.message shouldBe "This question already has an adopted answer"
                }
            }
        }
    }

    Given("a real user with two real adopted answers in their history") {
        val answerRepository = mockk<KnowledgeAnswerRepository>()
        every { answerRepository.countByAnswererIdAndIsAdoptedTrue("answerer_1") } returns 2L
        val service = newService(answerRepository = answerRepository)

        When("checking their real reputation") {
            val result = service.getMyReputation("answerer_1")

            Then("the real adopted-answer count is returned") {
                result shouldBe 2L
            }
        }
    }
})
