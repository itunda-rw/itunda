package rw.itunda.core.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import rw.itunda.core.idempotency.InvalidIdempotencyKeyException

/**
 * Keeps a malformed shared idempotency header from becoming a controller-specific 500.
 *
 * Real gap found live (2026-09-14, sibling-asymmetry sweep): this was the repo's only
 * `@RestControllerAdvice`, but it lived in `rw.itunda.app.web` -- a package none of the
 * 14 independently-deployable extracted services (`insurance-service`, `loans-service`,
 * `card-service`, etc.) ever scan (each one's own `@ComponentScan` includes
 * `rw.itunda.core`/`rw.itunda.auth`/`rw.itunda.security` plus its own module, never
 * `rw.itunda.app`). A malformed `Idempotency-Key` header, or the exact
 * `ObjectOptimisticLockingFailureException` -> 409 conversion this codebase's own doc
 * comments repeatedly describe relying on as settled infrastructure (see
 * `P2pDelayedTransfer.kt`/`IkiminaService.kt`), silently became a raw, unhandled 500 in
 * every extracted service, never in the still-monolithic `:app`. Moved into
 * `rw.itunda.core.web` (already scanned by every service, including `:app` itself,
 * whose own `scanBasePackages = ["rw.itunda"]` is a superset) so the fix applies
 * everywhere with no per-service change needed.
 */
@RestControllerAdvice
class IdempotencyExceptionHandler {
    @ExceptionHandler(InvalidIdempotencyKeyException::class)
    fun handleInvalidKey(ex: InvalidIdempotencyKeyException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_IDEMPOTENCY_KEY", ex.message ?: "Invalid Idempotency-Key"))

    /** A concurrent state transition is a retryable conflict, never an opaque 500. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleConcurrentUpdate(): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RESOURCE_STATE_CHANGED", "This resource changed concurrently; refresh its state and retry"))
}
