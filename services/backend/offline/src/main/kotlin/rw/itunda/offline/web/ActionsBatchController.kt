package rw.itunda.offline.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.batch.BatchActionHandler
import rw.itunda.core.security.CurrentUser

data class BatchActionRequest(
    val clientActionId: String,
    val type: String,
    val idempotencyKey: String,
    val body: Map<String, Any?> = emptyMap(),
)

data class BatchRequest(val actions: List<BatchActionRequest>)

/**
 * Real backend half of "offline queue for pending actions" -- see
 * docs/TOSS_PARITY_MATRIX.md's Offline row for the full account, including why the mobile
 * half (local persistence + connectivity-triggered replay) is explicitly out of scope for
 * this pass: it doesn't exist at all today, not even a stub, and can't be built-and-verified
 * without a mobile build/device pass this session doesn't have.
 *
 * A mobile client queues actions locally while offline (each with its own client-generated
 * idempotencyKey, exactly like every other money-moving endpoint already requires) and
 * replays the whole queue here in one call once connectivity returns. Each action is
 * dispatched independently through its real domain service via BatchActionHandler -- the
 * same IdempotencyService.replayOrExecute path a normal single-action call would use, so
 * resubmitting the same queue twice (a client retrying the whole batch after a partial
 * network failure) replays already-completed actions instead of double-executing them.
 * One action failing doesn't fail the batch -- each result carries its own status, mirroring
 * exactly the status code its own single-action REST endpoint would have returned.
 *
 * Deliberately excludes account transfer: AccountService's quote/confirm split has a real
 * 60-second quote expiry, so a transfer confirm queued offline would be confirming a stale
 * or nonexistent quote by the time it replays -- that's real conflict handling this endpoint
 * doesn't attempt to solve, not an oversight.
 */
@RestController
@RequestMapping("/api/v1/actions")
class ActionsBatchController(handlers: List<BatchActionHandler>) {
    private val handlersByType = handlers.associateBy { it.actionType }

    @GetMapping("/types")
    fun supportedTypes(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "types" to handlersByType.keys.sorted()))

    @PostMapping("/batch")
    fun batch(
        @RequestBody request: BatchRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val results = request.actions.map { action ->
            val handler = handlersByType[action.type]
            val (status, body) = handler?.handle(currentUser.userId, action.idempotencyKey, action.body)
                ?: (400 to mapOf<String, Any?>("success" to false, "error" to mapOf("code" to "UNKNOWN_ACTION_TYPE", "message" to "No handler for action type '${action.type}'")))
            mapOf("clientActionId" to action.clientActionId, "type" to action.type, "status" to status, "body" to body)
        }
        return ResponseEntity.ok(mapOf("success" to true, "results" to results))
    }
}
