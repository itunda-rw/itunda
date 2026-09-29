package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real AI chatbot channel message (itunda Talk redesign, 2026-08-28, matching the
 * "ChatGPT for Kakao" reference) -- one row per real turn (user or assistant), real
 * conversation persistence so a stateless per-request llama-server call can be given
 * real prior context by replaying the last N rows, matching this codebase's own
 * "no @OneToMany, flat FK entity" convention.
 */
@Entity
@Table(name = "ai_chat_messages")
class AiChatMessage(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    // "user" | "assistant" -- the exact two roles AiSummaryClient.completeChat's
    // message array expects, kept as a free string (not an enum) to pass straight
    // through without translation.
    @Column(nullable = false, length = 16)
    val role: String,

    @Column(nullable = false, length = 2000)
    val content: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", role = "", content = "")
}
