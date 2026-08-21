package rw.itunda.splitbill.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.domain.SplitBillMode
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.messaging.GroupMemberNotFoundException
import rw.itunda.messaging.GroupNeedsMoreMembersException
import rw.itunda.splitbill.SplitBillAlreadyPaidException
import rw.itunda.splitbill.SplitBillAlreadySettledException
import rw.itunda.splitbill.SplitBillDescriptionRequiredException
import rw.itunda.splitbill.SplitBillInvalidAmountException
import rw.itunda.splitbill.SplitBillInvalidReceiptUrlException
import rw.itunda.splitbill.SplitBillInvalidVarianceLevelException
import rw.itunda.splitbill.SplitBillMaxRoundsReachedException
import rw.itunda.splitbill.SplitBillNeedsParticipantsException
import rw.itunda.splitbill.SplitBillNoPendingParticipantsException
import rw.itunda.splitbill.SplitBillNoAccountException
import rw.itunda.splitbill.SplitBillNotFoundException
import rw.itunda.splitbill.SplitBillParticipantNotGroupMemberException
import rw.itunda.splitbill.SplitBillService
import java.math.BigDecimal

data class AttachSplitBillReceiptRequest(val imageUrl: String)

data class CreateSplitBillRequest(
    val totalAmount: BigDecimal,
    val description: String,
    val participantUserIds: List<String>,
    // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25) -- see
    // SplitBillService.ladderSplit's own doc comment. Both optional; omitting mode
    // (or leaving it EVEN) is the unchanged v1 behavior.
    val mode: SplitBillMode = SplitBillMode.EVEN,
    val ladderVarianceLevel: Int? = null,
)

// Real 1:1-chat split-bill request (2026-08-09) -- no participantUserIds field, unlike
// CreateSplitBillRequest above: the other person is fixed by the {otherUserId} path
// variable, since this endpoint is for exactly two people, not an existing group.
data class CreateDirectSplitBillRequest(
    val totalAmount: BigDecimal,
    val description: String,
    val mode: SplitBillMode = SplitBillMode.EVEN,
    val ladderVarianceLevel: Int? = null,
)

// Real KakaoPay-style "정산하기" (settlement/split-bill), chat-embedded in an existing
// group conversation -- see SplitBillService's own doc comment.
@RestController
@RequestMapping("/api/v1/split-bills")
class SplitBillController(private val splitBillService: SplitBillService, private val idempotencyService: IdempotencyService) {

    @PostMapping("/conversations/{groupConversationId}")
    fun createSplitBill(
        @PathVariable groupConversationId: String,
        @RequestBody request: CreateSplitBillRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/split-bills/conversations/$groupConversationId", idempotencyKey, request) {
            val result = splitBillService.createSplitBill(
                currentUser.userId, groupConversationId, request.totalAmount, request.description, request.participantUserIds,
                request.mode, request.ladderVarianceLevel,
            )
            201 to mapOf("success" to true, "splitBill" to result.splitBill, "participants" to result.participants)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real 1:1-chat split-bill entry point (2026-08-09) -- see
    // SplitBillService.createDirectSplitBill's own doc comment for the full account:
    // resolves (or creates) a hidden 2-person group between the caller and
    // [otherUserId] first, then runs the exact same real split-bill logic
    // [createSplitBill] above does for a named group.
    @PostMapping("/direct/{otherUserId}")
    fun createDirectSplitBill(
        @PathVariable otherUserId: String,
        @RequestBody request: CreateDirectSplitBillRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/split-bills/direct/$otherUserId", idempotencyKey, request) {
            val result = splitBillService.createDirectSplitBill(
                currentUser.userId, otherUserId, request.totalAmount, request.description,
                request.mode, request.ladderVarianceLevel,
            )
            201 to mapOf("success" to true, "splitBill" to result.splitBill, "participants" to result.participants)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real read-only counterpart to POST /direct/{otherUserId} above -- see
    // SplitBillService.getDirectSplitBills's own doc comment. Never creates a hidden
    // group; an empty list when the two people have never split a bill before.
    @GetMapping("/direct/{otherUserId}")
    fun getDirectSplitBills(
        @PathVariable otherUserId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val results = splitBillService.getDirectSplitBills(currentUser.userId, otherUserId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "splitBills" to results.map { mapOf("splitBill" to it.splitBill, "participants" to it.participants) },
            ),
        )
    }

    @GetMapping("/{id}")
    fun getSplitBill(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val result = splitBillService.getSplitBill(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true, "splitBill" to result.splitBill, "participants" to result.participants))
    }

    @GetMapping("/conversations/{groupConversationId}")
    fun getSplitBillsForGroup(
        @PathVariable groupConversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val results = splitBillService.getSplitBillsForGroup(currentUser.userId, groupConversationId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "splitBills" to results.map { mapOf("splitBill" to it.splitBill, "participants" to it.participants) },
            ),
        )
    }

    // Real photo receipt attach (2026-07-28) -- see SplitBillService.attachReceipt's own
    // doc comment. Not money-moving, no Idempotency-Key requirement, same discipline
    // MerchantBookingController's own non-money-moving writes already establish.
    @PostMapping("/{id}/receipt")
    fun attachReceipt(
        @PathVariable id: String,
        @RequestBody request: AttachSplitBillReceiptRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val splitBill = splitBillService.attachReceipt(currentUser.userId, id, request.imageUrl)
        return ResponseEntity.ok(mapOf("success" to true, "splitBill" to splitBill))
    }

    // Real up-to-5 settlement-round escalation (2026-07-28) -- see
    // SplitBillService.requestNextRound's own doc comment. Not money-moving, no
    // Idempotency-Key requirement, same discipline attachReceipt above establishes.
    @PostMapping("/{id}/next-round")
    fun requestNextRound(
        @PathVariable id: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val splitBill = splitBillService.requestNextRound(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true, "splitBill" to splitBill))
    }

    @PostMapping("/{id}/pay")
    fun payShare(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/split-bills/$id/pay", idempotencyKey, id) {
            val participant = splitBillService.payShare(currentUser.userId, id)
            200 to mapOf("success" to true, "participant" to participant)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real 1:1-chat split-bill handlers (2026-08-09) -- GroupMessagingService
    // .getOrCreateDirectSplitGroup, called from createDirectSplitBill above, throws
    // these; same status codes GroupMessagingController's own handlers already use for
    // the identical exceptions.
    @ExceptionHandler(GroupNeedsMoreMembersException::class)
    fun handleGroupNeedsMoreMembers(ex: GroupNeedsMoreMembersException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_NEEDS_MORE_MEMBERS", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupMemberNotFoundException::class)
    fun handleGroupMemberNotFound(ex: GroupMemberNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MEMBER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SplitBillNotFoundException::class)
    fun handleNotFound(ex: SplitBillNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("SPLIT_BILL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SplitBillInvalidAmountException::class)
    fun handleInvalidAmount(ex: SplitBillInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillDescriptionRequiredException::class)
    fun handleDescriptionRequired(ex: SplitBillDescriptionRequiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("DESCRIPTION_REQUIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillNeedsParticipantsException::class)
    fun handleNeedsParticipants(ex: SplitBillNeedsParticipantsException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SPLIT_BILL_NEEDS_PARTICIPANTS", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillInvalidVarianceLevelException::class)
    fun handleInvalidVarianceLevel(ex: SplitBillInvalidVarianceLevelException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LADDER_VARIANCE_LEVEL", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillInvalidReceiptUrlException::class)
    fun handleInvalidReceiptUrl(ex: SplitBillInvalidReceiptUrlException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RECEIPT_URL", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillParticipantNotGroupMemberException::class)
    fun handleParticipantNotGroupMember(ex: SplitBillParticipantNotGroupMemberException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("PARTICIPANT_NOT_GROUP_MEMBER", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillAlreadyPaidException::class)
    fun handleAlreadyPaid(ex: SplitBillAlreadyPaidException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SPLIT_BILL_SHARE_ALREADY_PAID", ex.message ?: "Conflict"))

    @ExceptionHandler(SplitBillNoAccountException::class)
    fun handleNoAccount(ex: SplitBillNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SplitBillAlreadySettledException::class)
    fun handleAlreadySettled(ex: SplitBillAlreadySettledException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SPLIT_BILL_ALREADY_SETTLED", ex.message ?: "Conflict"))

    @ExceptionHandler(SplitBillNoPendingParticipantsException::class)
    fun handleNoPendingParticipants(ex: SplitBillNoPendingParticipantsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SPLIT_BILL_NO_PENDING_PARTICIPANTS", ex.message ?: "Conflict"))

    @ExceptionHandler(SplitBillMaxRoundsReachedException::class)
    fun handleMaxRoundsReached(ex: SplitBillMaxRoundsReachedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SPLIT_BILL_MAX_ROUNDS_REACHED", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
