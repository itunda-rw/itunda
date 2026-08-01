package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * One real candidate answer to a `KnowledgeQuestion` -- see that entity's own doc
 * comment for the full sourced account of Naver 지식iN's real 채택 (adoption)
 * mechanic. `isAdopted` is a real, queryable denormalization of
 * `KnowledgeQuestion.adoptedAnswerId` (kept in sync in the same transaction by
 * `KnowledgeService.adoptAnswer`), letting a client render "adopted" state without a
 * second lookup per answer -- same denormalize-for-read-convenience discipline
 * `CommunityPost.likeCount`/`commentCount` already establish.
 */
@Entity
@Table(name = "knowledge_answers")
class KnowledgeAnswer(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "question_id", nullable = false, length = 64)
    val questionId: String,

    @Column(name = "answerer_id", nullable = false, length = 64)
    val answererId: String,

    @Column(nullable = false, length = 4000)
    val body: String,

    @Column(name = "is_adopted", nullable = false)
    var isAdopted: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", questionId = "", answererId = "", body = "")
}
