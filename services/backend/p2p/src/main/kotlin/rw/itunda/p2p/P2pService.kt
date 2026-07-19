package rw.itunda.p2p

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
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
import rw.itunda.core.repository.P2pPaymentRequestRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
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
) {

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
        if (request.requesterUserId == payerUserId) {
            throw P2pSelfPaymentException("Cannot pay your own payment request")
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

        // Re-fetch, same as WalletService.confirmTransfer -- postLedgerTransaction doesn't
        // mutate the Wallet instance already held in memory, only the underlying row.
        val updatedPayerWallet = walletRepository.findById(payerWallet.id).orElseThrow { P2pNoWalletException("No wallet found for this account") }
        return transaction to updatedPayerWallet.balance
    }

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

        val senderWallet = walletRepository.findByUserIdAndType(senderUserId, WalletType.MAIN)
            ?: throw P2pNoWalletException("No wallet found for this account")

        val recipientUser = userRepository.findByPhoneNumber(trimmedIdentifier)
        val recipientWallet = (
            if (recipientUser != null) walletRepository.findByUserIdAndType(recipientUser.id, WalletType.MAIN) else null
            ) ?: walletRepository.findByAccountNumber(trimmedIdentifier)
            ?: throw P2pRecipientNotFoundException("No itunda account found for this phone number or account number")

        if (recipientWallet.userId == senderUserId) {
            throw P2pSelfPaymentException("Cannot send money to your own account")
        }
        if (senderWallet.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance for this transfer")
        }

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

        val updatedSenderWallet = walletRepository.findById(senderWallet.id).orElseThrow { P2pNoWalletException("No wallet found for this account") }
        return transaction to updatedSenderWallet.balance
    }
}
