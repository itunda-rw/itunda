package rw.itunda.core.realtime

import rw.itunda.core.domain.Message

/**
 * Real live-transport hook for messaging (2026-07-18) -- the "separate, genuinely
 * larger infrastructure concern" `MessagingService`'s own doc comment named as the
 * natural next step after poll-based delivery. Lives in `:core` (not `:messaging`)
 * so the real implementation (`rw.itunda.app.websocket.MessagingWebSocketHandler`,
 * which needs `spring-boot-starter-websocket` and app-level wiring) can live in
 * `:app` without `:messaging` depending on `:app` -- the same dependency-direction
 * discipline this session's module boundaries already follow everywhere else.
 *
 * Deliberately synchronous, in-process, single-instance: a message is pushed
 * directly to whatever WebSocket session the recipient's own JVM process is
 * holding open, not routed through Kafka/the outbox. A Kafka-relayed push would
 * still be real, but the 2s `OutboxRelay` poll interval would make it barely
 * faster than the existing 4s HTTP poll it's meant to replace -- and this repo's own
 * Kafka connectivity has been genuinely flaky in local/dev environments (hostname
 * resolution failures against the private-cloud broker), which would make
 * "real-time" delivery silently depend on infrastructure this backend doesn't
 * reliably have yet. Honestly scoped:
 * this only reaches a recipient whose WebSocket session is held open on the SAME
 * backend instance that processed the send -- a real, named limitation for any
 * future multi-instance/horizontally-scaled deployment (a session-affinity load
 * balancer or a pub/sub fan-out across instances would be the right fix then, not
 * attempted here since itunda currently runs single-instance).
 */
interface RealtimeMessagePublisher {
    fun publishNewMessage(conversationId: String, recipientUserId: String, message: Message)
}
