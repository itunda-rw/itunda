package rw.itunda.core.web

import rw.itunda.core.domain.HoodTransactionReview
import java.time.Instant

/**
 * Real API-shaped view of a [HoodTransactionReview] (2026-07-24) -- the entity itself
 * stores `goodPoints`/`uncomfortablePoints` as a pipe-separated `VARCHAR` (see that
 * class's own doc comment for why: a small preset tag set doesn't need real relational
 * modeling), but a client should receive real JSON arrays, not a string it has to split
 * itself. Same "reshape for the real API contract, don't leak storage format" discipline
 * `TrustScoreSupport.trustScores` already established for its own map shape.
 */
data class HoodReviewResponseDto(
    val id: String,
    val transactionType: String,
    val transactionId: String,
    val reviewerId: String,
    val revieweeId: String,
    val goodPoints: List<String>,
    val uncomfortablePoints: List<String>,
    val createdAt: Instant,
)

fun HoodTransactionReview.toResponseDto() = HoodReviewResponseDto(
    id = id,
    transactionType = transactionType.name,
    transactionId = transactionId,
    reviewerId = reviewerId,
    revieweeId = revieweeId,
    goodPoints = goodPointList(),
    uncomfortablePoints = uncomfortablePointList(),
    createdAt = createdAt,
)
