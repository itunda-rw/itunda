package rw.itunda.p2p

import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// Real fix (2026-08-26): split out of P2pService.kt once that file grew past its
// file-size-lint baseline. The real Toss/KakaoBank-style money-sent/money-received
// push notifications are a genuinely distinct concern from the core P2P transfer
// logic -- both entry points (notifyMoneyReceived/notifyMoneySent) are best-effort
// side effects, never allowed to affect the already-completed money movement.
@Component
class P2pNotificationService(
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(P2pNotificationService::class.java)

    // Real-time "money received" notification (2026-07-22) -- modeled on one of Toss
    // Bank's most iconic, signature UX elements: an instant in-app notification the
    // moment money arrives (real Toss shows "OOO님이 5,000원을 보냈어요" -- "OOO sent you
    // 5,000 won" -- the instant a transfer completes), not something a recipient has to
    // notice by manually opening the app and checking their balance. Found as a real,
    // significant gap by auditing this file directly: zero `Notification` references
    // existed anywhere in it despite both real money-movement paths (payRequest,
    // sendDirect) completing successfully -- confirmed by grep across every module that
    // already does write real notifications (auth, savings, account budget alerts,
    // messaging, commerce, community, agents), `p2p` and `merchant` were the two
    // conspicuously absent ones for money actually arriving in someone's account.
    // Was written recipient-only, on the unsourced assumption that real Toss only
    // notifies the *other* party -- **corrected 2026-08-23** by a real, user-supplied
    // Toss screenshot showing the SENDER's own device getting a push confirming their
    // own outgoing transfer. See notifyMoneySent below for the sender-side equivalent
    // this file now also sends.
    // Best-effort: a notification failure must never roll back or fail money that
    // already moved, same "auxiliary side-effect can't block real money movement"
    // discipline `MerchantService.collect`'s own cashback-award try/catch established.
    //
    // Real push wired in (2026-07-28) -- see `PushNotificationService`'s own doc comment
    // for the wider rollout this joins. Picked as the highest-priority remaining site of
    // the ~16 named there: an instant "money received" push is real Toss's own single
    // most iconic, signature notification -- of every notification type in this backend,
    // this is the one a user would notice missing first.
    fun notifyMoneyReceived(recipientUserId: String, senderUserId: String, amount: BigDecimal) {
        try {
            val sender = userRepository.findById(senderUserId).orElse(null)
            val senderName = sender?.let { "${it.firstName} ${it.lastName}" } ?: "Someone"
            val title = "Money received"
            val body = "$senderName sent you $amount RWF."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}",
                    userId = recipientUserId,
                    type = "MONEY_RECEIVED",
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = Instant.now(),
                    dataJson = "{\"amount\":\"$amount\",\"senderId\":\"$senderUserId\"}",
                ),
            )
            sendMoneyReceivedPushAfterCommit(recipientUserId, title, body, amount, senderUserId)
        } catch (e: Exception) {
            // Non-critical -- the real transfer already completed and succeeded.
            log.warn("Failed to notify user {} of money received", recipientUserId, e)
        }
    }

    private fun sendMoneyReceivedPushAfterCommit(
        recipientUserId: String,
        title: String,
        body: String,
        amount: BigDecimal,
        senderUserId: String,
    ) {
        val send = {
            pushNotificationService.sendToUser(
                recipientUserId,
                title,
                body,
                mapOf("amount" to amount.toString(), "senderId" to senderUserId),
                type = "MONEY_RECEIVED",
            )
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    // Real Toss-parity correction (2026-08-23, user-supplied real Toss push-notification
    // screenshot: "3,000원 출금" / "내 전북은행 통장 → 토스 김민석" -- a real Toss SENDER's
    // own device getting a push confirming their own outgoing transfer). The
    // notifyMoneyReceived doc comment above claims "Deliberately recipient-only... matching
    // real Toss's own behavior of notifying the *other* party" -- that claim was never
    // sourced (no citation, unlike the rest of this file's decisions), and this real
    // screenshot directly contradicts it, so it's corrected here rather than left standing
    // against real evidence. A sender-side confirmation also serves a real security purpose
    // already established elsewhere in this codebase: an unauthorized transfer from a
    // hijacked session is exactly the kind of thing the real account owner needs to see
    // immediately, the same reasoning LoansService.applyForLoan's own real
    // NEW_LOAN_DISBURSED Asset Protection Alert push already uses.
    //
    // Title says "sent," not Toss's own "출금" (withdrawal) -- itunda's transfer model
    // moves money directly between itunda accounts, not a withdrawal from an external
    // linked bank account the way Toss Pay's own real product works, so "sent" is the
    // honest term for what actually happened here, matching this file's own existing
    // "Money received" title language for the recipient side.
    fun notifyMoneySent(senderUserId: String, senderAccount: Account, recipientUserId: String, amount: BigDecimal) {
        try {
            val recipient = userRepository.findById(recipientUserId).orElse(null)
            val recipientName = recipient?.let { "${it.firstName} ${it.lastName}" } ?: "the recipient"
            val title = "$amount RWF sent"
            val body = "${senderAccount.accountName} → $recipientName"
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}",
                    userId = senderUserId,
                    type = "MONEY_SENT",
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = Instant.now(),
                    dataJson = "{\"amount\":\"$amount\",\"recipientId\":\"$recipientUserId\"}",
                ),
            )
            sendMoneySentPushAfterCommit(senderUserId, title, body, amount, recipientUserId)
        } catch (e: Exception) {
            // Non-critical -- the real transfer already completed and succeeded.
            log.warn("Failed to notify user {} of money sent", senderUserId, e)
        }
    }

    private fun sendMoneySentPushAfterCommit(senderUserId: String, title: String, body: String, amount: BigDecimal, recipientUserId: String) {
        val send = {
            pushNotificationService.sendToUser(
                senderUserId,
                title,
                body,
                mapOf("amount" to amount.toString(), "recipientId" to recipientUserId),
                type = "MONEY_SENT",
            )
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    // Real proactive-expiry notification (Bank product-completeness pass, cycle 2,
    // 2026-09-09) -- see P2pService.markExpired/P2pPaymentRequestExpiryScheduler's own
    // doc comments. Plain, everyday copy over jargon like "invalidated" or "voided"
    // (Toss's "Casual Concept" writing principle, same rewrite this session already
    // applied to the write-off notification copy).
    fun notifyRequestExpired(requesterUserId: String, amount: BigDecimal) {
        try {
            val title = "Request expired"
            val body = "Your request for $amount RWF expired unpaid. You can send a new one anytime."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}",
                    userId = requesterUserId,
                    type = "P2P_REQUEST_EXPIRED",
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = Instant.now(),
                    dataJson = "{\"amount\":\"$amount\"}",
                ),
            )
            pushNotificationService.sendToUser(requesterUserId, title, body, mapOf("amount" to amount.toString()), type = "P2P_REQUEST_EXPIRED")
        } catch (e: Exception) {
            // Non-critical -- the status flip already succeeded.
            log.warn("Failed to notify user {} of expired request", requesterUserId, e)
        }
    }
}
