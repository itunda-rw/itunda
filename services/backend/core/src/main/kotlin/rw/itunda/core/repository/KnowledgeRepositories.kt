package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.KnowledgeAnswer
import rw.itunda.core.domain.KnowledgeQuestion

interface KnowledgeQuestionRepository : JpaRepository<KnowledgeQuestion, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<KnowledgeQuestion>
    fun findByCategoryOrderByCreatedAtDesc(category: String, pageable: Pageable): Page<KnowledgeQuestion>
    fun findByAskerIdOrderByCreatedAtDesc(askerId: String, pageable: Pageable): Page<KnowledgeQuestion>
}

interface KnowledgeAnswerRepository : JpaRepository<KnowledgeAnswer, String> {
    fun findByQuestionIdOrderByCreatedAtAsc(questionId: String): List<KnowledgeAnswer>
    fun findByAnswererIdOrderByCreatedAtDesc(answererId: String, pageable: Pageable): Page<KnowledgeAnswer>
    fun countByAnswererIdAndIsAdoptedTrue(answererId: String): Long
}
