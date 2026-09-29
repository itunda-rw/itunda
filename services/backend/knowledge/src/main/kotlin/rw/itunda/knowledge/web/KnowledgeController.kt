package rw.itunda.knowledge.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.knowledge.InvalidKnowledgeAnswerException
import rw.itunda.knowledge.InvalidKnowledgeQuestionException
import rw.itunda.knowledge.KnowledgeAnswerNotForQuestionException
import rw.itunda.knowledge.KnowledgeAnswerNotFoundException
import rw.itunda.knowledge.KnowledgeQuestionAlreadyHasAdoptedAnswerException
import rw.itunda.knowledge.KnowledgeQuestionNotFoundException
import rw.itunda.knowledge.KnowledgeService

data class PostKnowledgeQuestionRequest(val category: String, val title: String, val body: String)
data class PostKnowledgeAnswerRequest(val body: String)

// Real Naver 지식iN (Knowledge iN)-style open-topic community Q&A -- see
// KnowledgeService's own doc comment for the full sourced account. Normal itunda-user
// JWT gate.
@RestController
@RequestMapping("/api/v1/knowledge")
class KnowledgeController(private val knowledgeService: KnowledgeService) {

    @GetMapping("/categories")
    fun getCategories(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "categories" to KnowledgeService.CATEGORIES))

    @PostMapping("/questions")
    fun postQuestion(
        @RequestBody request: PostKnowledgeQuestionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val question = knowledgeService.postQuestion(currentUser.userId, request.category, request.title, request.body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "question" to question))
    }

    @GetMapping("/questions")
    fun browse(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = knowledgeService.browse(pageable, category)
        return ResponseEntity.ok(mapOf("success" to true, "questions" to page.content) + pageMeta(page))
    }

    @GetMapping("/questions/my-questions")
    fun getMyQuestions(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = knowledgeService.getMyQuestions(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "questions" to page.content) + pageMeta(page))
    }

    @GetMapping("/answers/my-answers")
    fun getMyAnswers(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = knowledgeService.getMyAnswers(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "answers" to page.content) + pageMeta(page))
    }

    @GetMapping("/reputation/me")
    fun getMyReputation(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "adoptedAnswerCount" to knowledgeService.getMyReputation(currentUser.userId)))

    @GetMapping("/questions/{questionId}")
    fun getQuestion(@PathVariable questionId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "question" to knowledgeService.getQuestion(questionId)))

    @GetMapping("/questions/{questionId}/answers")
    fun getAnswers(@PathVariable questionId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "answers" to knowledgeService.getAnswers(questionId)))

    @PostMapping("/questions/{questionId}/answers")
    fun postAnswer(
        @PathVariable questionId: String,
        @RequestBody request: PostKnowledgeAnswerRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val answer = knowledgeService.postAnswer(currentUser.userId, questionId, request.body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "answer" to answer))
    }

    @PostMapping("/questions/{questionId}/answers/{answerId}/adopt")
    fun adoptAnswer(
        @PathVariable questionId: String,
        @PathVariable answerId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "answer" to knowledgeService.adoptAnswer(currentUser.userId, questionId, answerId)))

    @ExceptionHandler(InvalidKnowledgeQuestionException::class)
    fun handleInvalidQuestion(ex: InvalidKnowledgeQuestionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_KNOWLEDGE_QUESTION", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidKnowledgeAnswerException::class)
    fun handleInvalidAnswer(ex: InvalidKnowledgeAnswerException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_KNOWLEDGE_ANSWER", ex.message ?: "Bad request"))

    @ExceptionHandler(KnowledgeQuestionNotFoundException::class)
    fun handleQuestionNotFound(ex: KnowledgeQuestionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("KNOWLEDGE_QUESTION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(KnowledgeAnswerNotFoundException::class)
    fun handleAnswerNotFound(ex: KnowledgeAnswerNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("KNOWLEDGE_ANSWER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(KnowledgeAnswerNotForQuestionException::class)
    fun handleAnswerNotForQuestion(ex: KnowledgeAnswerNotForQuestionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("KNOWLEDGE_ANSWER_NOT_FOR_QUESTION", ex.message ?: "Bad request"))

    @ExceptionHandler(KnowledgeQuestionAlreadyHasAdoptedAnswerException::class)
    fun handleAlreadyAdopted(ex: KnowledgeQuestionAlreadyHasAdoptedAnswerException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("KNOWLEDGE_QUESTION_ALREADY_HAS_ADOPTED_ANSWER", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
