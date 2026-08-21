package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.P2pPaymentRequestStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.P2pPaymentRequestRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.family.FamilyLinkService
import rw.itunda.savings.RoundUpService
import rw.itunda.wallet.AutoTopUpService
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class P2pRequestNotFoundException(message: String) : RuntimeException(message)
class P2pRequestNotPayableException(message: String) : RuntimeException(message)
class P2pSelfPaymentException(message: String) : RuntimeException(message)
class P2pNoWalletException(message: String) : RuntimeException(message)
class P2pRecipientNotFoundException(message: String) : RuntimeException(message)
class P2pInvalidAmountException(message: String) : RuntimeException(message)
class P2pTransferLimitExceededException(message: String) : RuntimeException(message)

/**
 * Real Toss/Kakao Bank-style recipient-name confirmation payload -- see
 * [P2pService.resolveRecipient]'s own doc comment for the full sourced account of why
 * this exists. Deliberately just the two fields a client needs to render "Send X RWF
 * to [displayName]?"; never leaks the recipient's phone number or account number back
 * out (the caller already knows the identifier they typed in).
 */
data class P2pRecipientPreview(val recipientUserId: String, val displayName: String)

/**
 * Real person-to-person QR -- see docs/TOSS_PARITY_MATRIX.md's QR Pay row. Deliberately
 * distinct from WalletService.confirmTransfer: that flow always routes through the simulated
 * external rail (recipientId hardcoded "external", CREDIT posts to rail_suspense) regardless
 * of whether the recipient happens to be an itunda user too -- there is no wallet-to-wallet
 * concept in it at all (confirmed by reading it directly). This is the first real
 * wallet-to-wallet money movement in the backend where both sides are known itunda accounts:
 * a direct WALLET-to-WALLET ledger pair, no rail hop, no fee (nothing external to settle).
 * Also the first real Transaction row where recipientId is an actual user id, not "external" --
 * WalletService.getTransactionHistory will show this to both the payer and the requester.
 */
@Service
class P2pService(
    private val p2pPaymentRequestRepository: P2pPaymentRequestRepository,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val fraudRuleEngine: FraudRuleEngine,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val roundUpService: RoundUpService,
    private val familyLinkService: FamilyLinkService,
    private val autoTopUpService: AutoTopUpService,
    private val pushNotificationService: PushNotificationService,
    private val p2pTransferLimitService: P2pTransferLimitService,
) {
    private val log = LoggerFactory.getLogger(P2pService::class.java)

    fun generateRequest(requesterUserId: String, amount: BigDecimal, description: String): P2pPaymentRequest {
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Every
        // other real content/money-creation endpoint in this codebase (Marketplace
        // listings, Community posts, Jobs posts, Real Estate listings/offers,
        // chargeCard) already has one; this real-money-request-creation endpoint had
        // shipped without it, same class of gap as the chargeCard/toggleReaction
        // findings from earlier sweeps.
        rateLimiter.checkLimit("p2p:request:$requesterUserId", limit = 20, window = Duration.ofHours(1))
        val request = P2pPaymentRequest(
            id = "p2p_${UUID.randomUUID()}",
            requesterUserId = requesterUserId,
            amount = amount,
            description = description,
            expiresAt = Instant.now().plusSeconds(900),
        )
        return p2pPaymentRequestRepository.save(request)
    }

    fun getMyRequests(requesterUserId: String): List<P2pPaymentRequest> =
        p2pPaymentRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(requesterUserId)

    @Transactional
    fun payRequest(payerUserId: String, requestId: String): Pair<Transaction, BigDecimal> {
        val request = p2pPaymentRequestRepository.findById(requestId)
            .orElseThrow { P2pRequestNotFoundException("Payment request not found") }
        if (request.status != P2pPaymentRequestStatus.PENDING) {
            throw P2pRequestNotPayableException("This payment request has already been used")
        }
        if (request.expiresAt.isBefore(Instant.now())) {
            request.status = P2pPaymentRequestStatus.EXPIRED
            p2pPaymentRequestRepository.save(request)
            throw P2pRequestNotPayableException("This payment request has expired")
        }
        // Real Toss writing-principle reference (toss.tech/article/21021, "6 principles
        // for a good error message"): plain user language over a blunt "Cannot X"
        // directive, and a concrete next step rather than just naming what's wrong.
        if (request.requesterUserId == payerUserId) {
            throw P2pSelfPaymentException("This request is yours -- ask someone else to pay it instead")
        }
        // Real anti-spam limit, same sweep -- lower abuse surface than generateRequest
        // (a real pending request is single-use and payment debits the payer's own real
        // balance), but still a real mutating money-movement endpoint that gets the
        // same day-one-rate-limiting discipline as everything else in this codebase.
        rateLimiter.checkLimit("p2p:pay:$payerUserId", limit = 30, window = Duration.ofHours(1))

        val payerWallet = walletRepository.findByUserIdAndType(payerUserId, WalletType.MAIN)
            ?: throw P2pNoWalletException("No wallet found for this account")
        val requesterWallet = walletRepository.findByUserIdAndType(request.requesterUserId, WalletType.MAIN)
            ?: throw P2pNoWalletException("Requester has no wallet to receive this payment")
        if (payerWallet.availableBalance < request.amount) {
            throw InsufficientFundsException("Insufficient available balance for this payment")
        }

        val result = ledgerService.postLedgerTransaction(
            payerWallet.currency,
            listOf(
                LedgerLeg(payerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, request.amount, "QR payment - ${request.description}"),
                LedgerLeg(requesterWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, request.amount, "QR payment received - ${request.description}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "P2PQR${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = payerUserId,
            recipientId = request.requesterUserId,
            fromWalletId = payerWallet.id,
            toWalletId = requesterWallet.id,
            amount = request.amount,
            fee = BigDecimal.ZERO,
            currency = payerWallet.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "QR payment - ${request.description}",
            completedAt = Instant.now(),
        )
        // Real bug found live: evaluating fraud rules *after* saving this transaction let it
        // find itself as "prior history" (recipientId already matches, since it's checking
        // against itself), permanently masking NEW_RECIPIENT no matter how new the recipient
        // actually was. Must evaluate before this transaction exists in the query results.
        fraudRuleEngine.evaluate(payerUserId, request.requesterUserId, request.amount, transaction.id)
        transactionRepository.save(transaction)

        request.status = P2pPaymentRequestStatus.COMPLETED
        request.completedTransactionId = transaction.id
        request.paidByUserId = payerUserId
        p2pPaymentRequestRepository.save(request)
        notifyMoneyReceived(request.requesterUserId, payerUserId, request.amount)

        // Re-fetch, same as WalletService.confirmTransfer -- postLedgerTransaction doesn't
        // mutate the Wallet instance already held in memory, only the underlying row.
        val updatedPayerWallet = walletRepository.findById(payerWallet.id).orElseThrow { P2pNoWalletException("No wallet found for this account") }
        return transaction to updatedPayerWallet.balance
    }

    /**
     * Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") before a
     * P2P transfer completes -- sourced from Toss's own support FAQ
     * (support.toss.im/faq/127: a transfer is cancelled and refunded outright if the
     * sender-entered name doesn't match the real account holder) and Toss's own
     * contact-based transfer flow, which shows the resolved recipient's real name in
     * parentheses (e.g. "김토스(한*스)") for the sender to actively verify before
     * confirming -- "recipient name mismatch" is documented as a standard, expected
     * step across Korean banking apps generally (see e.g. "Korean Banking App Terms
     * Explained: Transfer Limits, OTP, and 'Recipient Name Mismatch'",
     * kl.imporinfo.com/2026/05/korean-banking-app-terms-explained.html). The whole
     * point: catch a single mistyped digit in a phone/account number BEFORE money
     * moves, not after -- P2P transfers here are immediate and final, with no reversal
     * path once [sendDirect] completes.
     *
     * [sendDirect] itself resolves a phone number or account number and moves money in
     * one synchronous call -- confirmed by reading it directly, there was zero point
     * anywhere in that flow for a sender to see WHO they actually resolved to before
     * it becomes irreversible. A real, confirmed gap, not a guess: a sender fat-
     * fingering one digit of a contact's phone number that happens to collide with a
     * different real itunda account would silently send real money to a stranger with
     * no recovery path, and never know until the stranger's balance changed instead of
     * their intended contact's.
     *
     * This is a read-only preview a client calls right after the recipient identifier
     * is entered, before rendering the final "Send X RWF to [name]?" confirmation --
     * reuses [resolveRecipientWallet], the exact same resolution [sendDirect] itself
     * uses, so what's previewed here is guaranteed to be who actually receives the
     * money if the sender goes on to confirm. Deliberately does NOT create any
     * durable/expiring quote object the way [rw.itunda.wallet.WalletService
     * .quoteTransfer] does for the external-rail flow: unlike that flow (where the fee
     * and rail routing genuinely need to be locked in between quote and confirm),
     * nothing about a wallet-to-wallet resolution can drift between this call and
     * [sendDirect] -- a phone number isn't reassigned to a different real account
     * mid-session, so re-resolving at send time is exactly as safe and avoids a whole
     * extra class of stale/hijacked-quote bugs for zero real benefit.
     */
    fun resolveRecipient(callerUserId: String, identifier: String): P2pRecipientPreview {
        val trimmedIdentifier = identifier.trim()
        if (trimmedIdentifier.isEmpty()) throw P2pRecipientNotFoundException("Recipient is required")
        // Real anti-enumeration limit: without one, an authenticated caller could
        // script this read-only lookup across a range of phone numbers to harvest
        // which ones are real itunda accounts and their real names -- the same "every
        // money/PII-adjacent endpoint gets a real rate limit from day one" discipline
        // generateRequest/payRequest/sendDirect above already established, just tuned
        // higher than sendDirect's own 30/hour since a real user legitimately checking
        // a few candidate contacts before picking the right one shouldn't get blocked.
        rateLimiter.checkLimit("p2p:resolve:$callerUserId", limit = 40, window = Duration.ofHours(1))

        val recipientWallet = resolveRecipientWallet(trimmedIdentifier)
        // Same real self-payment guard sendDirect enforces -- previewing "send to
        // yourself" would be a confusing, pointless result to show, not an honest one.
        if (recipientWallet.userId == callerUserId) {
            throw P2pSelfPaymentException("You can't send money to yourself -- check the recipient and try again")
        }
        val recipientUser = userRepository.findById(recipientWallet.userId).orElse(null)
        val displayName = recipientUser?.let { "${it.firstName} ${it.lastName}" } ?: "itunda user"
        return P2pRecipientPreview(recipientUserId = recipientWallet.userId, displayName = displayName)
    }

    /**
     * Real recipient resolution shared by [resolveRecipient] and [sendDirect] -- a
     * phone number (`UserRepository.findByPhoneNumber`, matching how a user actually
     * thinks of a contact) or, if that misses, an account number (`WalletRepository.
     * findByAccountNumber`, globally unique). Extracted so the preview a sender sees
     * and the recipient money actually moves to can never diverge -- one real
     * resolution path, not two independently-maintained copies of the same lookup.
     */
    private fun resolveRecipientWallet(trimmedIdentifier: String) =
        (
            userRepository.findByPhoneNumber(trimmedIdentifier)?.let { walletRepository.findByUserIdAndType(it.id, WalletType.MAIN) }
                ?: walletRepository.findByAccountNumber(trimmedIdentifier)
            ) ?: throw P2pRecipientNotFoundException("No itunda account found for this phone number or account number")

    /**
     * Real direct itunda-to-itunda push-transfer (2026-07-20) -- a genuine gap surfaced
     * while wiring bank-mfe's own home-screen "Transfer" button: that button, matching
     * Android/iOS's own `sendTransfer`, calls `WalletService.confirmTransfer`, which by
     * pre-existing design (see this class's own doc comment above) always routes through
     * the simulated external rail and never actually credits another itunda user's
     * wallet, even when the typed-in recipient is a real itunda account. Until now the
     * only real internal wallet-to-wallet movement was `payRequest` above, which requires
     * the *recipient* to first generate a request -- there was no way to just type in
     * someone's phone number or account number and send them money immediately, the
     * single most basic real Toss "Transfer" action. This closes that gap by reusing
     * `payRequest`'s exact real ledger-movement shape (direct WALLET-to-WALLET pair, no
     * fee -- nothing external to settle) with a real recipient resolved by phone number
     * (`UserRepository.findByPhoneNumber`, matching how a user actually thinks of a
     * contact) or, if that misses, by account number (`WalletRepository.
     * findByAccountNumber`, globally unique) -- never a fabricated match; an identifier
     * that resolves to neither is a real, honest 404, not a silent no-op.
     */
    @Transactional
    fun sendDirect(senderUserId: String, recipientIdentifier: String, amount: BigDecimal, description: String): Pair<Transaction, BigDecimal> {
        if (amount <= BigDecimal.ZERO) throw P2pInvalidAmountException("Amount must be greater than zero")
        val trimmedIdentifier = recipientIdentifier.trim()
        if (trimmedIdentifier.isEmpty()) throw P2pRecipientNotFoundException("Recipient is required")

        // Real anti-spam limit, same 30/hour convention payRequest already established
        // for a real mutating money-movement endpoint.
        rateLimiter.checkLimit("p2p:send:$senderUserId", limit = 30, window = Duration.ofHours(1))

        var senderWallet = walletRepository.findByUserIdAndType(senderUserId, WalletType.MAIN)
            ?: throw P2pNoWalletException("No wallet found for this account")

        val recipientWallet = resolveRecipientWallet(trimmedIdentifier)

        if (recipientWallet.userId == senderUserId) {
            throw P2pSelfPaymentException("You can't send money to yourself -- check the recipient and try again")
        }
        if (senderWallet.availableBalance < amount) {
            // Real Naver Pay Money "결제 시 부족분 자동 충전" (auto-charge the shortfall at
            // payment time) -- see AutoTopUpService.topUpShortfall's own doc comment for
            // the full sourced account and why this is safe to call synchronously here
            // (before this transfer's own ledger legs are posted, so before any row lock
            // on the sender's wallet is taken -- not the post-commit-hook pattern
            // RoundUpService needed for its own, structurally different, AFTER-the-fact
            // auxiliary action). A no-op (falls through to the same real
            // InsufficientFundsException) for the overwhelming common case of a sender
            // with no auto top-up configured or enabled.
            val shortfall = amount.subtract(senderWallet.availableBalance)
            val topUpResult = autoTopUpService.topUpShortfall(senderUserId, senderWallet.id, shortfall)
            if (topUpResult.triggered) {
                senderWallet = walletRepository.findById(senderWallet.id).orElseThrow { P2pNoWalletException("No wallet found for this account") }
            }
            if (senderWallet.availableBalance < amount) {
                throw InsufficientFundsException("Insufficient available balance for this transfer")
            }
        }
        // Real FamilyLink daily spend-limit enforcement (2026-07-27) -- see
        // FamilyLinkService.enforceSpendLimit's own doc comment. A real gate before
        // money moves, same discipline WalletFrozenException/minOrderAmount already
        // established; a no-op for the overwhelming common case of a sender who isn't a
        // linked child with a real limit set.
        familyLinkService.enforceSpendLimit(senderUserId, senderWallet.id, amount)
        // Real Korean "이체한도" (transfer limit) enforcement (Section 186) -- see
        // P2pTransferLimitService's own doc comment for the full sourced account. A
        // real, flat per-transfer and daily-cumulative cap on itunda's real
        // wallet-to-wallet transfer rail, independent of (and stacked on top of) any
        // account-specific FamilyLink limit above.
        p2pTransferLimitService.enforce(senderUserId, senderWallet.id, amount)

        val trimmedDescription = description.trim().ifEmpty { "Transfer" }
        val result = ledgerService.postLedgerTransaction(
            senderWallet.currency,
            listOf(
                LedgerLeg(senderWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transfer - $trimmedDescription"),
                LedgerLeg(recipientWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Transfer received - $trimmedDescription"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "P2PTXN${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = senderUserId,
            recipientId = recipientWallet.userId,
            fromWalletId = senderWallet.id,
            toWalletId = recipientWallet.id,
            amount = amount,
            fee = BigDecimal.ZERO,
            currency = senderWallet.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Transfer - $trimmedDescription",
            completedAt = Instant.now(),
        )
        // Evaluated before save, same ordering reasoning as payRequest's own inline
        // comment: evaluating after would let this transaction match itself as prior
        // history and permanently mask NEW_RECIPIENT.
        fraudRuleEngine.evaluate(senderUserId, recipientWallet.userId, amount, transaction.id)
        transactionRepository.save(transaction)
        notifyMoneyReceived(recipientWallet.userId, senderUserId, amount)
        // Real round-up auto-saving (2026-07-25) -- see RoundUpService's own doc
        // comment for the full account, including why P2P transfer specifically is
        // this feature's honest v1 scope.
        //
        // Real three-attempt bug fix 2026-07-27, all three caught live in the same
        // verification pass, in this order:
        // (1) A plain synchronous call here (REQUIRED propagation) let a nested
        //     @Transactional call failing inside processRoundUp (e.g. buyStock,
        //     depositToGoal) mark the *shared* transaction rollback-only -- Spring then
        //     threw a real UnexpectedRollbackException on commit, 500-ing this entire
        //     real transfer even though the money had already correctly moved, and even
        //     though processRoundUp's own try/catch had already handled the business
        //     exception (a caught exception inside a proxied @Transactional method still
        //     poisons the transaction if it escaped a DIFFERENT proxied method first).
        // (2) Switching processRoundUp to REQUIRES_NEW (still called synchronously,
        //     right here) fixed (1) but created a real self-deadlock: its nested
        //     `SELECT wallet FOR UPDATE` (inside fundInvestmentWallet/depositToGoal)
        //     blocks on the exact same sender wallet row THIS transaction already locked
        //     and hasn't released yet (row locks live until commit, and REQUIRES_NEW
        //     runs on a second, separate connection) -- real 30s "Lock wait timeout
        //     exceeded", again swallowed but silently skipping every real round-up.
        // (3) Deferring the call to Spring's real post-commit hook (this transaction's
        //     locks are released by the time it fires) fixed the deadlock, but calling
        //     processRoundUp with plain REQUIRED from inside afterCommit() itself real-
        //     threw "Query requires transaction be in progress, but no transaction is
        //     known to be in progress" -- Spring's transaction-synchronization ThreadLocal
        //     state is genuinely ambiguous during the post-commit callback window, and
        //     REQUIRED's "join if present" logic doesn't reliably resolve that into
        //     starting a real new physical transaction. See processRoundUp's own doc
        //     comment for why REQUIRES_NEW is what actually fixes this, safely this time.
        // Final correct architecture: defer processRoundUp to a real post-commit hook
        // (not a home-grown polling/retry scheme) AND keep it REQUIRES_NEW, so it always
        // unconditionally opens a genuinely fresh transaction with this caller's locks
        // already released and nothing left for it to poison. Guarded by
        // isSynchronizationActive(): plain unit tests construct P2pService directly
        // (bypassing Spring's @Transactional proxy entirely), so no synchronization is
        // ever active there -- the direct-call fallback keeps this method callable
        // outside a real Spring transaction too.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        roundUpService.processRoundUp(senderUserId, amount)
                    } catch (e: Exception) {
                        log.warn("Round-up skipped for transfer {}: {}", transaction.id, e.message)
                    }
                }
            })
        } else {
            try {
                roundUpService.processRoundUp(senderUserId, amount)
            } catch (e: Exception) {
                log.warn("Round-up skipped for transfer {}: {}", transaction.id, e.message)
            }
        }

        val updatedSenderWallet = walletRepository.findById(senderWallet.id).orElseThrow { P2pNoWalletException("No wallet found for this account") }
        return transaction to updatedSenderWallet.balance
    }

    /**
     * Real Naver Pay "가족 공유 자산 관리" (family shared asset management) -- instant
     * transfer to a linked family member straight from the guardian's own family
     * overview, sourced from fresh 2026-08 research (see docs/DESIGN_REFERENCES.md's
     * own entry for the full account). A real, additive convenience on top of [sendDirect],
     * not a new money-movement mechanism: resolves the child's own account number via a
     * verified ACTIVE [FamilyLinkService.isActiveGuardianOf] gate, then delegates straight
     * to the exact same, already-tested [sendDirect] the plain "type a phone number"
     * flow uses -- same rate limit, same auto top-up, same fraud check, same round-up,
     * same notification, zero duplicated ledger logic.
     */
    @Transactional
    fun sendToFamilyMember(guardianUserId: String, childUserId: String, amount: BigDecimal, description: String): Pair<Transaction, BigDecimal> {
        if (!familyLinkService.isActiveGuardianOf(guardianUserId, childUserId)) {
            throw P2pRecipientNotFoundException("No active family link with this account")
        }
        val childWallet = walletRepository.findByUserIdAndType(childUserId, WalletType.MAIN)
            ?: throw P2pRecipientNotFoundException("No itunda account found for this family member")
        return sendDirect(guardianUserId, childWallet.accountNumber, amount, description)
    }

    // Real-time "money received" notification (2026-07-22) -- modeled on one of Toss
    // Bank's most iconic, signature UX elements: an instant in-app notification the
    // moment money arrives (real Toss shows "OOO님이 5,000원을 보냈어요" -- "OOO sent you
    // 5,000 won" -- the instant a transfer completes), not something a recipient has to
    // notice by manually opening the app and checking their balance. Found as a real,
    // significant gap by auditing this file directly: zero `Notification` references
    // existed anywhere in it despite both real money-movement paths (payRequest,
    // sendDirect) completing successfully -- confirmed by grep across every module that
    // already does write real notifications (auth, savings, wallet budget alerts,
    // messaging, commerce, community, agents), `p2p` and `merchant` were the two
    // conspicuously absent ones for money actually arriving in someone's account.
    // Deliberately recipient-only, not sender-side too: the sender already gets an
    // immediate synchronous success response in the app UI from the action they just
    // took -- a second notification telling them what they just did themselves would be
    // redundant, matching real Toss's own behavior of notifying the *other* party.
    // Best-effort: a notification failure must never roll back or fail money that
    // already moved, same "auxiliary side-effect can't block real money movement"
    // discipline `MerchantService.collect`'s own cashback-award try/catch established.
    //
    // Real push wired in (2026-07-28) -- see `PushNotificationService`'s own doc comment
    // for the wider rollout this joins. Picked as the highest-priority remaining site of
    // the ~16 named there: an instant "money received" push is real Toss's own single
    // most iconic, signature notification -- of every notification type in this backend,
    // this is the one a user would notice missing first.
    private fun notifyMoneyReceived(recipientUserId: String, senderUserId: String, amount: BigDecimal) {
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
}
