package rw.itunda.knowledge

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.KnowledgeAnswer
import rw.itunda.core.domain.KnowledgeQuestion
import rw.itunda.core.repository.KnowledgeAnswerRepository
import rw.itunda.core.repository.KnowledgeQuestionRepository
import java.time.Duration
import java.util.UUID

class InvalidKnowledgeQuestionException(message: String) : RuntimeException(message)
class InvalidKnowledgeAnswerException(message: String) : RuntimeException(message)
class KnowledgeQuestionNotFoundException(message: String) : RuntimeException(message)
class KnowledgeAnswerNotFoundException(message: String) : RuntimeException(message)
class KnowledgeAnswerNotForQuestionException(message: String) : RuntimeException(message)
class KnowledgeQuestionAlreadyHasAdoptedAnswerException(message: String) : RuntimeException(message)

data class KnowledgeCategory(val id: String, val label: String)

/**
 * Real Naver 지식iN (Knowledge iN)-style open-topic community Q&A -- see
 * `KnowledgeQuestion`/`KnowledgeAnswer`'s own doc comments for the full sourced
 * account. Genuinely distinct in shape from this session's trip/rental/booking
 * features (Rideshare/Bike/Parking/Bus): no wallet movement, no location, no asset --
 * a real content + social-reputation mechanic instead.
 *
 * v1, honestly scoped: real question/answer posting and browsing, real one-time
 * asker-only answer adoption, a real read-time-computed adopted-answer count as the
 * reputation signal (same "compute fresh on every call, don't cache a running
 * counter" discipline `TrustScoreService` already establishes for a derived score --
 * an answer's adoption status can only ever move from false to true, never reverse, so
 * this stays cheap without needing a cached counter's staleness risk). No point-to-
 * wallet conversion this pass (a real, named, deliberately deferred follow-up --
 * `RewardsController`'s existing mission-points-to-wallet conversion is the real
 * precedent to reuse if this is ever built).
 */
@Service
class KnowledgeService(
    private val questionRepository: KnowledgeQuestionRepository,
    private val answerRepository: KnowledgeAnswerRepository,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        val CATEGORIES = listOf(
            KnowledgeCategory("general", "General"),
            KnowledgeCategory("money", "Money & banking"),
            KnowledgeCategory("tech", "Tech"),
            KnowledgeCategory("life", "Everyday life"),
            KnowledgeCategory("business", "Business"),
        )
        private val CATEGORY_IDS = CATEGORIES.map { it.id }.toSet()
    }

    @Transactional
    fun postQuestion(askerId: String, category: String, title: String, body: String): KnowledgeQuestion {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isEmpty() || trimmedBody.isEmpty()) {
            throw InvalidKnowledgeQuestionException("Title and body are both required")
        }
        // Real bound matching CommunityPost's own VARCHAR(200)/VARCHAR(4000) precedent
        // -- this DB's real STRICT_TRANS_TABLES mode throws a raw 500 on an over-length
        // insert rather than truncating.
        if (trimmedTitle.length > 200 || trimmedBody.length > 4000) {
            throw InvalidKnowledgeQuestionException("Title must be 200 characters or fewer, body 4000 or fewer")
        }
        if (category !in CATEGORY_IDS) {
            throw InvalidKnowledgeQuestionException("Unknown category")
        }
        // Real anti-spam limit, matching CommunityService.createPost's own 10/hour
        // convention for user-generated post creation.
        rateLimiter.checkLimit("knowledge:question:$askerId", limit = 10, window = Duration.ofHours(1))

        return questionRepository.save(
            KnowledgeQuestion(
                id = "knowledge_question_${UUID.randomUUID()}", askerId = askerId, category = category,
                title = trimmedTitle, body = trimmedBody,
            ),
        )
    }

    fun browse(pageable: Pageable, category: String?): Page<KnowledgeQuestion> =
        if (category.isNullOrBlank()) {
            questionRepository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            questionRepository.findByCategoryOrderByCreatedAtDesc(category, pageable)
        }

    fun getMyQuestions(askerId: String, pageable: Pageable): Page<KnowledgeQuestion> =
        questionRepository.findByAskerIdOrderByCreatedAtDesc(askerId, pageable)

    fun getMyAnswers(answererId: String, pageable: Pageable): Page<KnowledgeAnswer> =
        answerRepository.findByAnswererIdOrderByCreatedAtDesc(answererId, pageable)

    fun getQuestion(questionId: String): KnowledgeQuestion =
        questionRepository.findById(questionId).orElseThrow { KnowledgeQuestionNotFoundException("Question not found") }

    fun getAnswers(questionId: String): List<KnowledgeAnswer> {
        if (!questionRepository.existsById(questionId)) throw KnowledgeQuestionNotFoundException("Question not found")
        return answerRepository.findByQuestionIdOrderByCreatedAtAsc(questionId)
    }

    @Transactional
    fun postAnswer(answererId: String, questionId: String, body: String): KnowledgeAnswer {
        val question = questionRepository.findById(questionId).orElseThrow { KnowledgeQuestionNotFoundException("Question not found") }
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw InvalidKnowledgeAnswerException("Answer cannot be empty")
        }
        if (trimmed.length > 4000) {
            throw InvalidKnowledgeAnswerException("Answer must be 4000 characters or fewer")
        }
        // Real anti-spam limit, matching CommunityService.addComment's own 30/hour
        // convention for reply-shaped content creation.
        rateLimiter.checkLimit("knowledge:answer:$answererId", limit = 30, window = Duration.ofHours(1))

        return answerRepository.save(
            KnowledgeAnswer(id = "knowledge_answer_${UUID.randomUUID()}", questionId = question.id, answererId = answererId, body = trimmed),
        )
    }

    /**
     * Real 채택 (adoption) -- only the question's own real asker can adopt (same
     * "don't reveal a resource exists to someone who shouldn't act on it" IDOR
     * discipline `CommunityService.requireAuthor` already establishes: a non-asker
     * gets the same 404 a truly-missing question would, not a 403 that confirms the
     * question exists), and only once per question -- a real 409 on a second attempt,
     * matching `DesignatedDriverService`'s own single-claim discipline for a different
     * one-time state transition.
     */
    @Transactional
    fun adoptAnswer(askerId: String, questionId: String, answerId: String): KnowledgeAnswer {
        val question = questionRepository.findById(questionId).orElseThrow { KnowledgeQuestionNotFoundException("Question not found") }
        if (question.askerId != askerId) {
            throw KnowledgeQuestionNotFoundException("Question not found")
        }
        if (question.adoptedAnswerId != null) {
            throw KnowledgeQuestionAlreadyHasAdoptedAnswerException("This question already has an adopted answer")
        }
        val answer = answerRepository.findById(answerId).orElseThrow { KnowledgeAnswerNotFoundException("Answer not found") }
        if (answer.questionId != questionId) {
            throw KnowledgeAnswerNotForQuestionException("This answer does not belong to this question")
        }
        answer.isAdopted = true
        answerRepository.save(answer)
        question.adoptedAnswerId = answer.id
        questionRepository.save(question)
        return answer
    }

    // Real read-time-computed reputation -- see this class's own doc comment for why
    // this stays a live COUNT rather than a cached running counter.
    fun getMyReputation(userId: String): Long = answerRepository.countByAnswererIdAndIsAdoptedTrue(userId)
}
