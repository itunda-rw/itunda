package rw.itunda.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rw.itunda.app.network.Wallet
import rw.itunda.app.network.SavingsGoal
import rw.itunda.app.network.InterestJar
import rw.itunda.app.network.DiscoverItem
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.QuoteTransferRequest
import rw.itunda.app.network.ConfirmTransferRequest
import rw.itunda.app.network.DepositRequest
import rw.itunda.app.network.BatchActionRequest
import rw.itunda.app.network.BatchRequest
import rw.itunda.app.network.ConnectivityObserver
import rw.itunda.app.network.OfflineActionQueue
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

/** Shared outcome type for the real money-moving calls below (transfer/deposit/
 * claim) -- distinct from AuthResult (SessionManager.kt) since these carry a
 * user-facing amount/balance, not a session. Queued (2026-07-13) is distinct from
 * Success: the action wasn't actually executed yet, only durably saved locally for
 * replay once connectivity returns -- see OfflineActionQueue.kt. */
sealed interface MoneyActionResult {
    data class Success(val message: String) : MoneyActionResult
    data class Queued(val message: String) : MoneyActionResult
    data class Failure(val message: String) : MoneyActionResult
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // Real offline queue + connectivity signal (2026-07-13) -- see
    // docs/TOSS_PARITY_MATRIX.md's Offline row. AndroidViewModel (not plain ViewModel)
    // specifically so this has a real Context to construct these from, without a
    // separate application-level singleton-holder just for that.
    private val offlineQueue = OfflineActionQueue(application)
    private val connectivityObserver = ConnectivityObserver(application)

    private val _pendingActionCount = MutableStateFlow(offlineQueue.peekAll().size)
    val pendingActionCount: StateFlow<Int> = _pendingActionCount
    private val _primaryWallet = MutableStateFlow<Wallet?>(null)
    val primaryWallet: StateFlow<Wallet?> = _primaryWallet

    private val _savingsGoals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val savingsGoals: StateFlow<List<SavingsGoal>> = _savingsGoals

    private val _interestJar = MutableStateFlow<InterestJar?>(null)
    val interestJar: StateFlow<InterestJar?> = _interestJar

    private val _discoverItems = MutableStateFlow<List<DiscoverItem>>(emptyList())
    val discoverItems: StateFlow<List<DiscoverItem>> = _discoverItems

    private val _transactions = MutableStateFlow<List<rw.itunda.app.network.TransactionDto>>(emptyList())
    val transactions: StateFlow<List<rw.itunda.app.network.TransactionDto>> = _transactions

    private val _profile = MutableStateFlow<rw.itunda.app.network.PublicUser?>(null)
    val profile: StateFlow<rw.itunda.app.network.PublicUser?> = _profile

    private val _notifications = MutableStateFlow<List<rw.itunda.app.network.NotificationDto>>(emptyList())
    val notifications: StateFlow<List<rw.itunda.app.network.NotificationDto>> = _notifications

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount

    // Distinguishes "showing real data" from "backend unreachable, showing offline
    // placeholder" -- the UI should be honest about which one it's rendering rather
    // than silently presenting fallback numbers as if they were real (2026-07-11 fix,
    // see this file's own previous version: it caught *every* exception, including
    // real 401s from an expired session, and masked them as "offline demo mode").
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline

    init {
        fetchData()
        // Real replay-on-reconnect (2026-07-13): the moment a validated network
        // becomes available, flush anything queued while offline -- not just on the
        // next manual pull-to-refresh. viewModelScope.launch here (not a raw
        // coroutine) so this is cancelled automatically in onCleared() along with
        // everything else this ViewModel owns.
        connectivityObserver.start {
            viewModelScope.launch { replayPendingActions() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityObserver.stop()
    }

    private fun fetchData() {
        viewModelScope.launch {
            try {
                val walletRes = NetworkClient.apiService.getWallets()
                if (walletRes.success) {
                    _primaryWallet.value = walletRes.wallets.firstOrNull { it.type == "MAIN" } ?: walletRes.wallets.firstOrNull()
                }

                val savingsRes = NetworkClient.apiService.getSavingsGoals()
                if (savingsRes.success) {
                    _savingsGoals.value = savingsRes.goals
                }

                // Real bug found live (2026-07-13): an account that has never made an
                // interest-jar-eligible deposit gets a real 404 INTEREST_JAR_NOT_FOUND
                // from the backend (confirmed directly against services/backend's
                // SavingsController) -- a legitimate state for any new account, not a
                // backend failure. This whole function's outer catch only handles
                // IOException on purpose (see below), so an uncaught HttpException here
                // was crashing the entire app on first launch for any new user, not
                // "surfacing a real error" as intended -- a crash isn't a surfaced
                // error, it's the absence of one. Scoped narrowly to 404 specifically;
                // any other HTTP status still propagates to the outer catch as before.
                try {
                    val jarRes = NetworkClient.apiService.getInterestJar()
                    if (jarRes.success) {
                        _interestJar.value = jarRes.jar
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() != 404) throw e
                    _interestJar.value = null
                }

                val discoverRes = NetworkClient.apiService.getDiscoverItems()
                if (discoverRes.success) {
                    _discoverItems.value = discoverRes.items
                }

                val transactionsRes = NetworkClient.apiService.getTransactionHistory()
                if (transactionsRes.success) {
                    _transactions.value = transactionsRes.transactions
                }
                _isOffline.value = false
            } catch (e: IOException) {
                // Genuinely can't reach the backend at all (no connectivity, wrong
                // host) -- this is the only case that should show placeholder data,
                // and it's now labeled as such via isOffline rather than presented as
                // real. A 401/403/5xx (retrofit2.HttpException) is NOT caught here --
                // those are real backend responses and should surface as real errors,
                // not get silently swallowed into fake numbers.
                _isOffline.value = true
                _primaryWallet.value = Wallet(
                    id = "w_offline_placeholder",
                    userId = "",
                    accountNumber = "----",
                    accountName = "Offline",
                    type = "MAIN",
                    balance = 0.0,
                    availableBalance = 0.0,
                    currency = "RWF",
                    isActive = false,
                )
                _discoverItems.value = listOf(
                    DiscoverItem("d_1", "government", "Irembo Services", "Pay government fees instantly", "Access 100+ services", "#0066FF", false, null),
                    DiscoverItem("d_3", "rewards", "itunda Points", "Earn on every transaction", "Earn 1 point per 100 RWF spent", "#FFB300", false, "1,240 pts"),
                    DiscoverItem("d_5", "lifestyle", "Yego Vouchers", "Exclusive partner deals", "Discounts at partners", "#E91E63", true, "Hot")
                )
            }
        }
    }

    fun retry() = fetchData()

    /** Real account settings screen data (2026-07-12) -- fetched on demand when the
     * Settings screen actually opens, not on every Home tab load. */
    fun loadSettingsData() {
        viewModelScope.launch {
            try {
                val profileRes = NetworkClient.authApi.getProfile()
                if (profileRes.success) _profile.value = profileRes.user

                val notificationsRes = NetworkClient.apiService.getNotifications()
                if (notificationsRes.success) {
                    _notifications.value = notificationsRes.notifications
                    _unreadNotificationCount.value = notificationsRes.unreadCount
                }
            } catch (_: Exception) {
                // Settings screen just shows whatever it already had (or nothing) --
                // not a money-moving action, no need for the offline-placeholder
                // treatment fetchData() uses for the Home tab.
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            try {
                NetworkClient.apiService.markNotificationRead(id)
                loadSettingsData()
            } catch (_: Exception) {
            }
        }
    }

    fun markAllNotificationsRead() = markNotificationRead("all")

    /**
     * Real quote-then-confirm transfer (2026-07-12) -- previously TransferFlow.kt's
     * screens were UI-only ("Backed by local state only, not TransferService", see
     * that file's header); now that a real session exists (SessionManager.kt), this
     * calls the actual services/backend wallet endpoints. A fresh Idempotency-Key
     * per attempt means a retried tap after a timeout replays the same result
     * instead of double-sending money -- same contract the backend's own
     * IdempotencyService enforces.
     */
    suspend fun sendTransfer(recipientAccountNumber: String, amountRwf: Long): MoneyActionResult {
        return try {
            val quoteRes = NetworkClient.apiService.quoteTransfer(
                QuoteTransferRequest(amount = BigDecimal(amountRwf), recipient = recipientAccountNumber)
            )
            val confirmRes = NetworkClient.apiService.confirmTransfer(
                idempotencyKey = UUID.randomUUID().toString(),
                request = ConfirmTransferRequest(quoteId = quoteRes.quote.id)
            )
            fetchData()
            MoneyActionResult.Success(confirmRes.message)
        } catch (e: retrofit2.HttpException) {
            MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    /**
     * Real offline queueing (2026-07-13): on IOException (no connectivity at all --
     * an HttpException, a real backend response, is never queued, only ever
     * surfaced as a real Failure) the deposit intent is durably saved locally via
     * OfflineActionQueue rather than dropped, and replayed automatically the moment
     * ConnectivityObserver reports a real network again. Deliberately scoped to
     * SAVINGS_DEPOSIT only, matching the backend batch endpoint's own supported
     * action types (BILL_PAY, BUY_AIRTIME, SAVINGS_DEPOSIT) -- sendTransfer above is
     * intentionally never queued, for the same 60-second-quote-expiry reason the
     * backend's ActionsBatchController doesn't support a transfer action type at all.
     */
    suspend fun depositToSavingsGoal(goalId: String, amountRwf: Long): MoneyActionResult {
        return try {
            val res = NetworkClient.apiService.depositToGoal(
                idempotencyKey = UUID.randomUUID().toString(),
                request = DepositRequest(goalId = goalId, amount = BigDecimal(amountRwf))
            )
            fetchData()
            MoneyActionResult.Success(res.message)
        } catch (e: retrofit2.HttpException) {
            MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            offlineQueue.enqueue(
                type = "SAVINGS_DEPOSIT",
                body = mapOf("goalId" to goalId, "amount" to amountRwf),
            )
            _pendingActionCount.value = offlineQueue.peekAll().size
            MoneyActionResult.Queued("Saved offline -- this deposit will go through automatically once you're back online.")
        }
    }

    /**
     * Real replay of everything OfflineActionQueue has saved, via the same
     * POST /api/v1/actions/batch endpoint a mobile client is meant to call --
     * see services/backend/offline/.../ActionsBatchController.kt. Each action
     * carries its own already-generated idempotencyKey, so replaying the same
     * queue twice (e.g. two connectivity blips in a row before this finishes) never
     * double-executes an already-completed deposit -- the backend's own
     * IdempotencyService.replayOrExecute guarantees that server-side. Only actions
     * the backend actually accepted or definitively rejected (2xx or a real 4xx/5xx
     * business response) are removed from the local queue; anything that couldn't
     * even reach the backend this attempt (a transient IOException on the batch
     * call itself) is left in place for the next reconnect.
     */
    suspend fun replayPendingActions() {
        val pending = offlineQueue.peekAll()
        if (pending.isEmpty()) return

        try {
            val response = NetworkClient.apiService.submitActionBatch(
                BatchRequest(
                    actions = pending.map {
                        BatchActionRequest(
                            clientActionId = it.clientActionId,
                            type = it.type,
                            idempotencyKey = it.idempotencyKey,
                            body = it.body,
                        )
                    },
                ),
            )
            val handledIds = response.results.map { it.clientActionId }.toSet()
            offlineQueue.removeByClientActionIds(handledIds)
            _pendingActionCount.value = offlineQueue.peekAll().size
            if (handledIds.isNotEmpty()) fetchData()
        } catch (_: IOException) {
            // Still offline (or the reconnect was too brief) -- leave the queue
            // intact, the next real connectivity callback will try again.
        }
    }

    suspend fun claimInterest(): MoneyActionResult {
        return try {
            val res = NetworkClient.apiService.claimInterest(idempotencyKey = UUID.randomUUID().toString())
            fetchData()
            MoneyActionResult.Success(res.message)
        } catch (e: retrofit2.HttpException) {
            MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    private fun backendErrorMessage(e: retrofit2.HttpException): String = when (e.code()) {
        422 -> "Insufficient funds for this amount."
        404 -> "That account or goal couldn't be found."
        409 -> "This request is already being processed."
        502 -> "The payment provider declined this transaction."
        else -> "Something went wrong. Please try again."
    }
}
