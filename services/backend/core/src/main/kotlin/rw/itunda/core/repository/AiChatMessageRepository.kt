package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.AiChatMessage

interface AiChatMessageRepository : JpaRepository<AiChatMessage, String> {
    // Real context-replay for a stateless per-request llama-server call, and the
    // real chat-history endpoint -- same query, same bound (Pageable), never an
    // unbounded load. Callers needing "last N turns" pass Pageable.ofSize(N).
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<AiChatMessage>
}
