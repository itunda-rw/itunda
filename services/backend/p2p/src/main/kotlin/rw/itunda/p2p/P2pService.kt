package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.P2pPaymentRequestStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.P2pPaymentRequestRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.family.FamilyLinkService
import rw.itunda.savings.RoundUpService
import rw.itunda.account.AutoTopUpService
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Real person-to-person QR -- see docs/TOSS_PARITY_MATRIX.md's QR Pay row. Deliberately
 * distinct from AccountService.confirmTransfer: that flow always routes through the simulated
 * external rail (recipientId hardcoded "external", CREDIT posts to rail_suspense) regardless
 * of whether the recipient happens to be an itunda user too -- there is no account-to-account
 * concept in it at all (confirmed by reading it directly). This is the first real
 * account-to-account money movement in the backend where both sides are known itunda accounts:
 * a direct ACCOUNT-to-ACCOUNT ledger pair, no rail hop, no fee (nothing external to settle).
 * Also the first real Transaction row where recipientId is an actual user id, not "external" --
 * AccountService.getTransactionHistory will show this to both the payer and the requester.
 */
@Service
class P2pService(
    private val p2pPaymentRequestRepository: P2pPaymentRequestRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val fraudRuleEngine: FraudRuleEngine,
    private val rateLimiter: RateLimiter,
    private val roundUpService: RoundUpService,
    private val familyLinkService: FamilyLinkService,
    private val autoTopUpService: AutoTopUpService,
    private val p2pTransferLimitService: P2pTransferLimitService,
    private val p2pNotificationService: P2pNotificationService,
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

        val payerAccount = accountRepository.findByUserIdAndType(payerUserId, AccountType.MAIN)
            ?: throw P2pNoAccountException("No account found for this account")
        val requesterAccount = accountRepository.findByUserIdAndType(request.requesterUserId, AccountType.MAIN)
            ?: throw P2pNoAccountException("Requester has no account to receive this payment")
        if (payerAccount.availableBalance < request.amount) {
            throw InsufficientFundsException("Insufficient available balance for this payment")
        }

        val result = ledgerService.postLedgerTransaction(
            payerAccount.currency,
            listOf(
                LedgerLeg(payerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, request.amount, "QR payment - ${request.description}"),
                LedgerLeg(requesterAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, request.amount, "QR payment received - ${request.description}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "P2PQR${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = payerUserId,
            recipientId = request.requesterUserId,
            fromAccountId = payerAccount.id,
            toAccountId = requesterAccount.id,
            amount = request.amount,
            fee = BigDecimal.ZERO,
            currency = payerAccount.currency,
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
        p2pNotificationService.notifyMoneyReceived(request.requesterUserId, payerUserId, request.amount)
        p2pNotificationService.notifyMoneySent(payerUserId, payerAccount, request.requesterUserId, request.amount)

        // Re-fetch, same as AccountService.confirmTransfer -- postLedgerTransaction doesn't
        // mutate the Account instance already held in memory, only the underlying row.
        val updatedPayerAccount = accountRepository.findById(payerAccount.id).orElseThrow { P2pNoAccountException("No account found for this account") }
        return transaction to updatedPayerAccount.balance
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
     * reuses [resolveRecipientAccount], the exact same resolution [sendDirect] itself
     * uses, so what's previewed here is guaranteed to be who actually receives the
     * money if the sender goes on to confirm. Deliberately does NOT create any
     * durable/expiring quote object the way [rw.itunda.account.AccountService
     * .quoteTransfer] does for the external-rail flow: unlike that flow (where the fee
     * and rail routing genuinely need to be locked in between quote and confirm),
     * nothing about a account-to-account resolution can drift between this call and
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

        val recipientAccount = resolveRecipientAccount(trimmedIdentifier)
        // Same real self-payment guard sendDirect enforces -- previewing "send to
        // yourself" would be a confusing, pointless result to show, not an honest one.
        if (recipientAccount.userId == callerUserId) {
            throw P2pSelfPaymentException("You can't send money to yourself -- check the recipient and try again")
        }
        val recipientUser = userRepository.findById(recipientAccount.userId).orElse(null)
        val displayName = recipientUser?.let { "${it.firstName} ${it.lastName}" } ?: "itunda user"
        return P2pRecipientPreview(recipientUserId = recipientAccount.userId, displayName = displayName)
    }

    /**
     * Real recipient resolution shared by [resolveRecipient] and [sendDirect] -- a
     * phone number (`UserRepository.findByPhoneNumber`, matching how a user actually
     * thinks of a contact) or, if that misses, an account number (`AccountRepository.
     * findByAccountNumber`, globally unique). Extracted so the preview a sender sees
     * and the recipient money actually moves to can never diverge -- one real
     * resolution path, not two independently-maintained copies of the same lookup.
     */
    private fun resolveRecipientAccount(trimmedIdentifier: String) =
        (
            userRepository.findByPhoneNumber(trimmedIdentifier)?.let { accountRepository.findByUserIdAndType(it.id, AccountType.MAIN) }
                ?: accountRepository.findByAccountNumber(trimmedIdentifier)
            ) ?: throw P2pRecipientNotFoundException("No itunda account found for this phone number or account number")

    /**
     * Real direct itunda-to-itunda push-transfer (2026-07-20) -- a genuine gap surfaced
     * while wiring bank-mfe's own home-screen "Transfer" button: that button, matching
     * Android/iOS's own `sendTransfer`, calls `AccountService.confirmTransfer`, which by
     * pre-existing design (see this class's own doc comment above) always routes through
     * the simulated external rail and never actually credits another itunda user's
     * account, even when the typed-in recipient is a real itunda account. Until now the
     * only real internal account-to-account movement was `payRequest` above, which requires
     * the *recipient* to first generate a request -- there was no way to just type in
     * someone's phone number or account number and send them money immediately, the
     * single most basic real Toss "Transfer" action. This closes that gap by reusing
     * `payRequest`'s exact real ledger-movement shape (direct ACCOUNT-to-ACCOUNT pair, no
     * fee -- nothing external to settle) with a real recipient resolved by phone number
     * (`UserRepository.findByPhoneNumber`, matching how a user actually thinks of a
     * contact) or, if that misses, by account number (`AccountRepository.
     * findByAccountNumber`, globally unique) -- never a fabricated match; an identifier
     * that resolves to neither is a real, honest 404, not a silent no-op.
     */
    // Real gap found live (2026-08-31, Toss security research thread): matches
    // Toss's own real "Fraud Suspicion Siren" (사기의심 사이렌, toss.im/tossfeed/article/
    // toss-fraud-detection) -- a real, informational warning shown to the sender when a
    // transfer trips a fraud heuristic, never a silent block. FraudRuleEngine.evaluate's
    // own result was computed here since the feature shipped but simply discarded at
    // every call site; nothing ever told the sender their transfer looked unusual, even
    // when NEW_RECIPIENT + HIGH_VALUE both fired on the same transfer. Returned here
    // (not surfaced by the fraud engine itself, which stays review-only/non-blocking per
    // its own doc comment) so P2pController can turn it into a real, honest,
    // non-technical warning string for the sender -- the flag itself, and the money
    // movement, are both already final by the time this returns; this is purely
    // informational, matching the post-hoc timing FraudRuleEngine.evaluate already has
    // (see this function's own "evaluated before save" comment above -- fraud detection
    // here has never been able to block a transfer before it lands, only flag it after).
    @Transactional
    fun sendDirect(senderUserId: String, recipientIdentifier: String, amount: BigDecimal, description: String, fromAccountId: String? = null): Triple<Transaction, BigDecimal, List<FraudFlag>> {
        if (amount <= BigDecimal.ZERO) throw P2pInvalidAmountException("Amount must be greater than zero")
        val trimmedIdentifier = recipientIdentifier.trim()
        if (trimmedIdentifier.isEmpty()) throw P2pRecipientNotFoundException("Recipient is required")

        // Real anti-spam limit, same 30/hour convention payRequest already established
        // for a real mutating money-movement endpoint.
        rateLimiter.checkLimit("p2p:send:$senderUserId", limit = 30, window = Duration.ofHours(1))

        // Real gap found live (2026-08-31, direct user reference of their own Toss app
        // showing a "which account should the money come from" picker on every send):
        // this always resolved the sender's MAIN account and nothing else, even though
        // `Transaction.fromAccountId` already models an arbitrary source account and a
        // real itunda user can genuinely hold more than one debit-capable Account row
        // (FOREIGN_CURRENCY, BUSINESS, MINI, GROW31_SAVINGS). `fromAccountId` is optional
        // and defaults to the existing MAIN lookup, so every pre-existing caller (USSD,
        // AutoTransfer, ScheduledTransfer) is unaffected.
        var senderAccount = if (fromAccountId != null) {
            accountRepository.findById(fromAccountId).filter { it.userId == senderUserId }.orElseThrow { P2pNoAccountException("No account found for this account") }
        } else {
            accountRepository.findByUserIdAndType(senderUserId, AccountType.MAIN)
                ?: throw P2pNoAccountException("No account found for this account")
        }

        val recipientAccount = resolveRecipientAccount(trimmedIdentifier)

        if (recipientAccount.userId == senderUserId) {
            throw P2pSelfPaymentException("You can't send money to yourself -- check the recipient and try again")
        }
        if (senderAccount.availableBalance < amount) {
            // Real Naver Pay Money "결제 시 부족분 자동 충전" (auto-charge the shortfall at
            // payment time) -- see AutoTopUpService.topUpShortfall's own doc comment for
            // the full sourced account and why this is safe to call synchronously here
            // (before this transfer's own ledger legs are posted, so before any row lock
            // on the sender's account is taken -- not the post-commit-hook pattern
            // RoundUpService needed for its own, structurally different, AFTER-the-fact
            // auxiliary action). A no-op (falls through to the same real
            // InsufficientFundsException) for the overwhelming common case of a sender
            // with no auto top-up configured or enabled.
            val shortfall = amount.subtract(senderAccount.availableBalance)
            val topUpResult = autoTopUpService.topUpShortfall(senderUserId, senderAccount.id, shortfall)
            if (topUpResult.triggered) {
                senderAccount = accountRepository.findById(senderAccount.id).orElseThrow { P2pNoAccountException("No account found for this account") }
            }
            if (senderAccount.availableBalance < amount) {
                throw InsufficientFundsException("Insufficient available balance for this transfer")
            }
        }
        // Real FamilyLink daily spend-limit enforcement (2026-07-27) -- see
        // FamilyLinkService.enforceSpendLimit's own doc comment. A real gate before
        // money moves, same discipline AccountFrozenException/minOrderAmount already
        // established; a no-op for the overwhelming common case of a sender who isn't a
        // linked child with a real limit set.
        familyLinkService.enforceSpendLimit(senderUserId, senderAccount.id, amount)
        // Real Korean "이체한도" (transfer limit) enforcement (Section 186) -- see
        // P2pTransferLimitService's own doc comment for the full sourced account. A
        // real, flat per-transfer and daily-cumulative cap on itunda's real
        // account-to-account transfer rail, independent of (and stacked on top of) any
        // account-specific FamilyLink limit above.
        p2pTransferLimitService.enforce(senderUserId, senderAccount.id, amount)

        val trimmedDescription = description.trim().ifEmpty { "Transfer" }
        val result = ledgerService.postLedgerTransaction(
            senderAccount.currency,
            listOf(
                LedgerLeg(senderAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transfer - $trimmedDescription"),
                LedgerLeg(recipientAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Transfer received - $trimmedDescription"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "P2PTXN${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = senderUserId,
            recipientId = recipientAccount.userId,
            fromAccountId = senderAccount.id,
            toAccountId = recipientAccount.id,
            amount = amount,
            fee = BigDecimal.ZERO,
            currency = senderAccount.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Transfer - $trimmedDescription",
            completedAt = Instant.now(),
        )
        // Evaluated before save, same ordering reasoning as payRequest's own inline
        // comment: evaluating after would let this transaction match itself as prior
        // history and permanently mask NEW_RECIPIENT.
        val fraudFlags = fraudRuleEngine.evaluate(senderUserId, recipientAccount.userId, amount, transaction.id)
        transactionRepository.save(transaction)
        p2pNotificationService.notifyMoneyReceived(recipientAccount.userId, senderUserId, amount)
        p2pNotificationService.notifyMoneySent(senderUserId, senderAccount, recipientAccount.userId, amount)
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
        //     `SELECT account FOR UPDATE` (inside fundInvestmentAccount/depositToGoal)
        //     blocks on the exact same sender account row THIS transaction already locked
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

        val updatedSenderAccount = accountRepository.findById(senderAccount.id).orElseThrow { P2pNoAccountException("No account found for this account") }
        return Triple(transaction, updatedSenderAccount.balance, fraudFlags)
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
    fun sendToFamilyMember(guardianUserId: String, childUserId: String, amount: BigDecimal, description: String): Triple<Transaction, BigDecimal, List<FraudFlag>> {
        if (!familyLinkService.isActiveGuardianOf(guardianUserId, childUserId)) {
            throw P2pRecipientNotFoundException("No active family link with this account")
        }
        val childAccount = accountRepository.findByUserIdAndType(childUserId, AccountType.MAIN)
            ?: throw P2pRecipientNotFoundException("No itunda account found for this family member")
        return sendDirect(guardianUserId, childAccount.accountNumber, amount, description)
    }
}
