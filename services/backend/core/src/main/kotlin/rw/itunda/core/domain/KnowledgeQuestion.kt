package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real Naver 지식iN (Knowledge iN)-style open-topic community Q&A -- sourced from
 * Naver's own real, iconic product (launched 2002, still live): a user posts a
 * question, other users submit competing real answers, and the asker picks one real
 * 채택 (adopted/best) answer. Genuinely distinct from `ProductInquiryService`'s own
 * narrow single-question/single-answer per-product buyer-seller thread -- this is a
 * general open-topic board with multiple candidate answers and a real adoption
 * mechanic, mirroring `CommunityPost`'s own field conventions (VARCHAR bounds,
 * category-as-string) but living in its own module since the concept (a real
 * public-reputation content mechanic) is distinct from Community's neighborhood-board
 * focus.
 */
@Entity
@Table(name = "knowledge_questions")
class KnowledgeQuestion(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "asker_id", nullable = false, length = 64)
    val askerId: String,

    @Column(nullable = false, length = 32)
    val category: String,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 4000)
    val body: String,

    // Set exactly once, by KnowledgeService.adoptAnswer -- see that method's own doc
    // comment for why this and the matching KnowledgeAnswer.isAdopted flag are updated
    // together in the same transaction.
    @Column(name = "adopted_answer_id", length = 64)
    var adoptedAnswerId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", askerId = "", category = "", title = "", body = "")
}
