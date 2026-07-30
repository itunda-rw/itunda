package rw.itunda.app.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import rw.itunda.core.idempotency.InvalidIdempotencyKeyException
import rw.itunda.core.web.ApiError

/** Keeps a malformed shared idempotency header from becoming a controller-specific 500. */
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
