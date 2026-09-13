package rw.itunda.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rw.itunda.core.network.Account
import rw.itunda.core.network.SavingsGoal
import rw.itunda.core.network.InterestJar
import rw.itunda.core.network.RoundUpSettingsDto
import rw.itunda.core.network.SetRoundUpSettingsRequest
import rw.itunda.core.network.DiscoverItem
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SendDirectP2pRequest
import rw.itunda.core.network.SendGiftRequest
import rw.itunda.core.network.DepositRequest
import rw.itunda.core.network.WithdrawRequest
import rw.itunda.core.network.CreateSavingsGoalRequest
import rw.itunda.core.network.BatchActionRequest
import rw.itunda.core.network.BatchRequest
import rw.itunda.core.network.ConnectivityObserver
import rw.itunda.core.network.OfflineActionQueue
import rw.itunda.core.network.MoneyActionResult
import rw.itunda.core.network.SessionManager
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

// MoneyActionResult moved to :core:network (2026-09-02, Banking Feature-module
// decomposition slice 2) so Feature modules can share it without depending on :app.

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // Real offline queue + connectivity signal (2026-07-13) -- see
    // docs/TOSS_PARITY_MATRIX.md's Offline row. AndroidViewModel (not plain ViewModel)
    // specifically so this has a real Context to construct these from, without a
    // separate application-level singleton-holder just for that.
    private val offlineQueue = OfflineActionQueue(application)
    private val connectivityObserver = ConnectivityObserver(application)

    private val _pendingActionCount = MutableStateFlow(offlineQueue.peekAll().size)
    val pendingActionCount: StateFlow<Int> = _pendingActionCount
    private val _primaryAccount = MutableStateFlow<Account?>(null)
    val primaryAccount: StateFlow<Account?> = _primaryAccount

    private val _savingsGoals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val savingsGoals: StateFlow<List<SavingsGoal>> = _savingsGoals

    private val _interestJar = MutableStateFlow<InterestJar?>(null)
    val interestJar: StateFlow<InterestJar?> = _interestJar

    private val _roundUpSettings = MutableStateFlow<RoundUpSettingsDto?>(null)
    val roundUpSettings: StateFlow<RoundUpSettingsDto?> = _roundUpSettings

    private val _discoverItems = MutableStateFlow<List<DiscoverItem>>(emptyList())
    val discoverItems: StateFlow<List<DiscoverItem>> = _discoverItems

    private val _transactions = MutableStateFlow<List<rw.itunda.core.network.TransactionDto>>(emptyList())
    val transactions: StateFlow<List<rw.itunda.core.network.TransactionDto>> = _transactions

    // Real Toss-style home-screen spending insight (2026-08-03) -- GET
    // /api/v1/account/spending, backed by AccountService.getSpendingInsight, has been
    // real since 2026-07-13 and already had its own dedicated SpendingScreen.kt, but
    // was never fetched here for the Home tab, which instead showed a hardcoded
    // "RWF 463,022 / Spent in July" placeholder explicitly commented as illustrative.
    private val _spendingInsight = MutableStateFlow<rw.itunda.core.network.SpendingInsightResponse?>(null)
    val spendingInsight: StateFlow<rw.itunda.core.network.SpendingInsightResponse?> = _spendingInsight

    private val _profile = MutableStateFlow<rw.itunda.core.network.PublicUser?>(null)
    val profile: StateFlow<rw.itunda.core.network.PublicUser?> = _profile

    private val _notifications = MutableStateFlow<List<rw.itunda.core.network.NotificationDto>>(emptyList())
    val notifications: StateFlow<List<rw.itunda.core.network.NotificationDto>> = _notifications

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount

    // Real cross-platform-parity gap found live (2026-09-13) -- web already shows a
    // real numeric Messages-tab badge (BankDashboard.tsx) using this exact unbounded
    // backend aggregate; Android had neither the endpoint nor any bottom-nav badge.
    private val _messagesUnreadCount = MutableStateFlow(0L)
    val messagesUnreadCount: StateFlow<Long> = _messagesUnreadCount

    // Real Partner SDK catalog (2026-07-17) -- approved third-party mini-apps a user can
    // actually tap into, closing the mobile half of docs/TOSS_PARITY_MATRIX.md's Partner
    // SDK row. See miniapps/PartnerMiniAppLoader.kt for the download/render mechanism.
    // Real Mini-Apps hub pass (2026-09-11) -- this StateFlow now backs only MenuScreen's
    // small capped teaser (first 3, no filters); the dedicated MiniAppsHubScreen ("See
    // all") owns its own category-filtered fetch, matching bank-mfe's identical
    // MiniAppsHubScreen.tsx split. Pagination state removed accordingly.
    private val _partnerMiniApps = MutableStateFlow<List<rw.itunda.core.network.PartnerMiniAppDto>>(emptyList())
    val partnerMiniApps: StateFlow<List<rw.itunda.core.network.PartnerMiniAppDto>> = _partnerMiniApps

    // Distinguishes "showing real data" from "backend unreachable, showing offline
    // placeholder" -- the UI should be honest about which one it's rendering rather
    // than silently presenting fallback numbers as if they were real (2026-07-11 fix,
    // see this file's own previous version: it caught *every* exception, including
    // real 401s from an expired session, and masked them as "offline demo mode").
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline

    // Real Toss/Kakao pull-to-refresh support (2026-08-11 research pass) -- Home had
    // no way to manually refresh at all beyond navigating away and back; this backs
    // the gesture itself. True for the whole duration of fetchData(), not just the
    // gesture's own release animation, so the indicator stays visible until real data
    // has actually landed rather than hiding early and looking like a no-op.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Same real Toss/Kakao pull-to-refresh support, a separate flag from
    // _isRefreshing above since Settings loads its own distinct data
    // (profile/notifications/devices) via loadSettingsData(), not fetchData() --
    // the Home tab's own pull gesture must never appear to finish early just because
    // an unrelated Settings fetch happened to complete around the same time.
    private val _isLoadingSettings = MutableStateFlow(false)
    val isLoadingSettings: StateFlow<Boolean> = _isLoadingSettings

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
            _isRefreshing.value = true
            try {
                val accountRes = NetworkClient.apiService.getAccounts()
                if (accountRes.success) {
                    _primaryAccount.value = accountRes.accounts.firstOrNull { it.type == "MAIN" } ?: accountRes.accounts.firstOrNull()
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

                // Real round-up auto-save settings (2026-07-25) -- a brand-new account has
                // no row yet, same 404-safe discipline as getInterestJar's fetch above.
                try {
                    val roundUpRes = NetworkClient.apiService.getRoundUpSettings()
                    if (roundUpRes.success) {
                        _roundUpSettings.value = roundUpRes.settings
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() != 404) throw e
                    _roundUpSettings.value = null
                }

                val discoverRes = NetworkClient.apiService.getDiscoverItems()
                if (discoverRes.success) {
                    _discoverItems.value = discoverRes.items
                }

                val transactionsRes = NetworkClient.apiService.getTransactionHistory()
                if (transactionsRes.success) {
                    _transactions.value = transactionsRes.transactions
                }

                val spendingRes = NetworkClient.apiService.getSpendingInsight()
                if (spendingRes.success) {
                    _spendingInsight.value = spendingRes
                }

                // Real Partner SDK catalog fetch (2026-07-17). Scoped in its own try/catch,
                // same discipline as getInterestJar's 404 handling above: a partner-catalog
                // hiccup (e.g. this environment's partners module not deployed) must never
                // block the rest of Home from loading real data.
                try {
                    val catalogRes = NetworkClient.apiService.getMiniAppCatalog()
                    if (catalogRes.success) {
                        _partnerMiniApps.value = catalogRes.miniApps
                    }
                } catch (e: retrofit2.HttpException) {
                    _partnerMiniApps.value = emptyList()
                }

                // Real bug found live (2026-09-07): getNotifications() used to only ever
                // be called from loadSettingsData(), itself only triggered by opening the
                // Settings screen -- so the Home tab's own bell badge, and the feed opened
                // directly from that same bell, both read a StateFlow that stayed at its
                // default (0/empty) until the user had separately visited Settings at
                // least once. Scoped in its own try/catch, same discipline as
                // getMiniAppCatalog's fetch above: a notifications hiccup must never block
                // the rest of Home from loading real data.
                try {
                    val notificationsRes = NetworkClient.apiService.getNotifications()
                    if (notificationsRes.success) {
                        _notifications.value = notificationsRes.notifications
                        _unreadNotificationCount.value = notificationsRes.unreadCount
                    }
                } catch (e: retrofit2.HttpException) {
                    // Leave whatever Home already had rather than clobber it with 0/empty.
                }

                // Real cross-platform-parity gap found live (2026-09-13) -- see
                // _messagesUnreadCount's own doc comment above. Same non-blocking,
                // leave-stale-on-failure discipline as the notifications fetch above.
                try {
                    val unreadRes = NetworkClient.talkApi.getUnreadCount()
                    if (unreadRes.success) {
                        _messagesUnreadCount.value = unreadRes.total
                    }
                } catch (e: retrofit2.HttpException) {
                    // Leave whatever Home already had rather than clobber it with 0.
                }
                _isOffline.value = false
            } catch (e: retrofit2.HttpException) {
                // Real stale-session crash (found 2026-07-22): a cached access token
                // that's no longer valid against the backend (e.g. after a DB reset or
                // redeploy) makes this function's first call, getAccounts() above, come
                // back with a genuine 401 -- the correct response is a real logout
                // back to the login screen.
                //
                // Real crash found live (2026-08-10): every OTHER status used to
                // `throw e` uncaught inside viewModelScope.launch, crashing the whole
                // app -- including on a real, genuinely possible 503 from the backend
                // having a bad moment (this is Home's own very first launch call).
                // "Surface a real error, don't silently fabricate data" was the right
                // intent, but a crash isn't a surfaced error, it's the absence of one
                // -- same reasoning getInterestJar's own 404 handling above already
                // applies. Routed into the same offline-placeholder path IOException
                // below uses: from the user's perspective, "the server replied but
                // something's wrong" and "couldn't reach it at all" are the same lived
                // experience -- see something honest, not a crash.
                if (e.code() == 401) {
                    SessionManager.logout()
                } else {
                    showOfflinePlaceholder()
                }
            } catch (e: IOException) {
                // Genuinely can't reach the backend at all (no connectivity, wrong
                // host) -- labeled via isOffline rather than presented as real.
                showOfflinePlaceholder()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun showOfflinePlaceholder() {
        _isOffline.value = true
        _primaryAccount.value = Account(
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
        // Real fix (2026-08-13, direct live-device catch): "itunda Points" carried a
        // hardcoded "1,240 pts" -- a stale copy of a fake literal the real Discover
        // feed itself already stopped showing (DiscoverService.kt's own doc comment,
        // 2026-08-11, replaced it with the caller's real reward total). This offline
        // placeholder never got the same fix, so a genuinely offline user saw a
        // fabricated specific number with no indication it wasn't real -- worse
        // still, isOffline (right above) was never actually surfaced anywhere in the
        // UI, so there was no honest signal this was placeholder data at all. Badge
        // removed (null, matching this same list's own "Irembo Services" row) rather
        // than inventing a fake-but-different number; see HomeTab's own new offline
        // banner for the other half of this fix.
        _discoverItems.value = listOf(
            DiscoverItem("d_1", "government", "Irembo Services", "Pay government fees instantly", "Access 100+ services", "#0066FF", false, null),
            DiscoverItem("d_3", "rewards", "itunda Points", "Earn on every transaction", "Earn 1 point per 100 RWF spent", "#FFB300", false, null),
            DiscoverItem("d_5", "lifestyle", "Yego Vouchers", "Exclusive partner deals", "Discounts at partners", "#E91E63", true, "Hot")
        )
    }

    fun retry() = fetchData()

    // Real Toss Bank reference (2026-09-12, "계좌 별명" -- account nickname) -- the
    // Manage screen's own PATCH call already updated the backend; this just reflects
    // that same real value into the already-loaded primaryAccount so the account
    // header can show it without a full re-fetch.
    fun updatePrimaryAccountNickname(nickname: String?) {
        _primaryAccount.value = _primaryAccount.value?.copy(nickname = nickname)
    }

    /** Real account settings screen data (2026-07-12) -- fetched on demand when the
     * Settings screen actually opens, not on every Home tab load. */
    fun loadSettingsData() {
        viewModelScope.launch {
            _isLoadingSettings.value = true
            try {
                val profileRes = NetworkClient.authApi.getProfile()
                if (profileRes.success) _profile.value = profileRes.user

                val notificationsRes = NetworkClient.apiService.getNotifications()
                if (notificationsRes.success) {
                    _notifications.value = notificationsRes.notifications
                    _unreadNotificationCount.value = notificationsRes.unreadCount
                }

                // Real device management (2026-07-21 port) -- see fetchDevices' own doc
                // comment.
                fetchDevices()
            } catch (_: Exception) {
                // Settings screen just shows whatever it already had (or nothing) --
                // not a money-moving action, no need for the offline-placeholder
                // treatment fetchData() uses for the Home tab.
            } finally {
                _isLoadingSettings.value = false
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
     * Real direct P2P push-transfer (2026-07-20) -- switched from the previous
     * quote-then-confirm `quoteTransfer`/`confirmTransfer` pair (built 2026-07-12) to
     * the new `sendDirect` endpoint: those older endpoints always route through a
     * simulated external rail and never actually credit another itunda user's account,
     * even when the recipient is a real itunda account (confirmed via a direct MySQL
     * check while building the real fix on the backend one day earlier -- see
     * SendDirectP2pRequest's own doc comment). No quote step needed here, since there's
     * no external rail decision to quote -- a fresh Idempotency-Key per attempt still
     * means a retried tap after a timeout replays the same result instead of
     * double-sending money, same contract the backend's own IdempotencyService enforces.
     */
    /**
     * Real savings-goal creation (2026-08-14). The backend endpoint and bank-mfe's own
     * createGoal have both existed for a long time, but Android only ever had the GET,
     * so "Save & grow" could never show a goal on this platform. Idempotency-Key added
     * 2026-08-15 -- a periodic backend coverage sweep found createGoal was missing the
     * same replayOrExecute protection its own sibling endpoints (deposit,
     * interest-jar/claim) already had, a real gap matching this codebase's established
     * risk signature (creates a brand-new row, no uniqueness constraint to catch a
     * retried duplicate). Fixed on the backend first, wired through here to match.
     */
    suspend fun createSavingsGoal(
        name: String,
        targetAmountRwf: Long,
        monthlyContributionRwf: Long?,
        targetDate: String?,
    ): MoneyActionResult {
        return try {
            NetworkClient.apiService.createSavingsGoal(
                idempotencyKey = UUID.randomUUID().toString(),
                request = CreateSavingsGoalRequest(
                    name = name,
                    targetAmount = BigDecimal(targetAmountRwf),
                    monthlyContribution = monthlyContributionRwf?.let { BigDecimal(it) },
                    targetDate = targetDate,
                ),
            )
            fetchData()
            MoneyActionResult.Success("Savings goal created.")
        } catch (e: retrofit2.HttpException) {
            MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    // Real gap found live (2026-08-31, direct user reference of their own Toss app's
    // "which account should the money come from" picker) -- fromAccountId is optional
    // and defaults to null (backend resolves the sender's MAIN account, same as
    // before) so every pre-existing call site is unaffected. See bank-mfe's identical
    // fix the same day (P2pService.sendDirect's own doc comment on the backend).
    suspend fun sendTransfer(recipientIdentifier: String, amountRwf: Long, memo: String = "", fromAccountId: String? = null): MoneyActionResult {
        return try {
            val res = NetworkClient.apiService.sendDirect(
                idempotencyKey = UUID.randomUUID().toString(),
                request = SendDirectP2pRequest(recipient = normalizeRecipientIdentifier(recipientIdentifier), amount = BigDecimal(amountRwf), description = memo, fromAccountId = fromAccountId),
            )
            fetchData()
            MoneyActionResult.Success(res.message, fraudWarnings = res.fraudWarnings)
        } catch (e: retrofit2.HttpException) {
            if (isDeviceNotVerified(e)) MoneyActionResult.DeviceNotVerified else MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    /**
     * Real standalone "send as a gift" (found via an uncalled-endpoint sweep 2026-08-16,
     * backend/bank-mfe/iOS docs Section 88) -- GiftService's own POST /api/v1/gifts,
     * money moves into escrow immediately and only reaches the recipient once they
     * claim it, unlike sendTransfer's instant push. Only resolves recipients by phone
     * number (unlike sendDirect's phone-or-account-number lookup), so the identifier
     * is normalized the same way sendTransfer's own recipient field is.
     */
    suspend fun sendGift(recipientPhoneNumber: String, amountRwf: Long, note: String?, theme: String?): MoneyActionResult {
        return try {
            NetworkClient.apiService.sendGift(
                idempotencyKey = UUID.randomUUID().toString(),
                request = SendGiftRequest(
                    recipientPhoneNumber = normalizeRecipientIdentifier(recipientPhoneNumber),
                    amount = BigDecimal(amountRwf).toDouble(),
                    note = note,
                    theme = theme,
                ),
            )
            fetchData()
            MoneyActionResult.Success("Gift sent! Held until they claim it -- auto-refunded after 7 days if unclaimed.")
        } catch (e: retrofit2.HttpException) {
            if (isDeviceNotVerified(e)) MoneyActionResult.DeviceNotVerified else MoneyActionResult.Failure(backendErrorMessage(e))
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
            MoneyActionResult.Success(res.message, goalCompleted = res.goal.status == "completed")
        } catch (e: retrofit2.HttpException) {
            if (isDeviceNotVerified(e)) MoneyActionResult.DeviceNotVerified else MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            offlineQueue.enqueue(
                type = "SAVINGS_DEPOSIT",
                body = mapOf("goalId" to goalId, "amount" to amountRwf),
            )
            _pendingActionCount.value = offlineQueue.peekAll().size
            MoneyActionResult.Queued("Saved offline -- this deposit will go through automatically once you're back online.")
        }
    }

    // Real gap found live (2026-08-31, direct user re-reference of the real Toss
    // "얼마나 꺼낼까요?" (withdraw) screenshot) -- see backend SavingsService
    // .withdrawFromGoal's own doc comment for the full account. Not offline-queued,
    // unlike depositToSavingsGoal above -- the backend batch endpoint
    // (ActionsBatchController) has no SAVINGS_WITHDRAW action type yet.
    suspend fun withdrawFromSavingsGoal(goalId: String, amountRwf: Long): MoneyActionResult {
        return try {
            val res = NetworkClient.apiService.withdrawFromGoal(
                idempotencyKey = UUID.randomUUID().toString(),
                request = WithdrawRequest(goalId = goalId, amount = BigDecimal(amountRwf))
            )
            fetchData()
            MoneyActionResult.Success(res.message)
        } catch (e: retrofit2.HttpException) {
            if (isDeviceNotVerified(e)) MoneyActionResult.DeviceNotVerified else MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    /**
     * Real round-up auto-save settings write (2026-07-25) -- see
     * RoundUpController.kt's own doc comment for why this is unqueued (a settings
     * change, not money movement) unlike depositToSavingsGoal above.
     */
    suspend fun setRoundUpSettings(enabled: Boolean, roundToNearest: Long, targetGoalId: String?, targetStockId: String? = null): MoneyActionResult {
        return try {
            val res = NetworkClient.apiService.setRoundUpSettings(
                SetRoundUpSettingsRequest(enabled = enabled, roundToNearest = BigDecimal(roundToNearest), targetGoalId = targetGoalId, targetStockId = targetStockId)
            )
            _roundUpSettings.value = res.settings
            MoneyActionResult.Success(if (enabled) "Round-up saving is on" else "Round-up saving is off")
        } catch (e: retrofit2.HttpException) {
            MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
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
            if (isDeviceNotVerified(e)) MoneyActionResult.DeviceNotVerified else MoneyActionResult.Failure(backendErrorMessage(e))
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    // Moved to network/ApiService.kt as `isDeviceNotVerifiedError` (2026-07-21) so
    // every money-moving call site can reuse it, not just this ViewModel's original
    // three (sendTransfer/depositToSavingsGoal/claimInterest).
    private fun isDeviceNotVerified(e: retrofit2.HttpException): Boolean =
        rw.itunda.core.network.isDeviceNotVerifiedError(e)

    // Real step-up re-verification (2026-07-21 port) -- re-proves password ownership
    // on THIS device (resolved server-side from the caller's own JWT deviceId claim)
    // and marks it trusted, matching bank-mfe's verifyDevice() exactly. The caller is
    // expected to retry whatever money-moving action returned DeviceNotVerified once
    // this returns true.
    suspend fun verifyDevice(password: String): MoneyActionResult {
        return try {
            NetworkClient.authApi.verifyDevice(rw.itunda.core.network.VerifyDeviceRequest(password))
            MoneyActionResult.Success("Device verified")
        } catch (e: retrofit2.HttpException) {
            // Real backend message pass-through first (2026-08-10), same fix as
            // backendErrorMessage/DeviceStepUpHost: this was discarding any specific
            // backend decline in favor of a hardcoded bucket.
            val message = rw.itunda.core.network.apiErrorMessage(e) ?: when (e.code()) {
                400 -> "Incorrect password."
                503, 504 -> "itunda is having a brief hiccup on our end -- not your password. Try again in a moment."
                else -> "Something went wrong. Please try again."
            }
            MoneyActionResult.Failure(message)
        } catch (e: IOException) {
            MoneyActionResult.Failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    private val _devices = MutableStateFlow<List<rw.itunda.core.network.TrustedDeviceDto>>(emptyList())
    val devices: StateFlow<List<rw.itunda.core.network.TrustedDeviceDto>> = _devices

    // Real gap found 2026-09-05 (matches iOS's identical same-day fix in
    // SettingsViewModel.swift/DeviceListScreen.swift, and web's HoodResumeBuilder.tsx
    // fix earlier this session) -- revokeDevice used to silently discard a failed
    // revoke. Unlike fetchDevices below, this IS a security-relevant action (removing
    // a device's ability to send money), so a failure needs real, visible feedback.
    private val _deviceError = MutableStateFlow<String?>(null)
    val deviceError: StateFlow<String?> = _deviceError

    // Real self-service device management (2026-07-21 port) -- backs a Devices list
    // in Settings, same real control Toss's own security settings page offers.
    suspend fun fetchDevices() {
        try {
            _devices.value = NetworkClient.authApi.getMyDevices().devices
        } catch (_: Exception) {
            // Best-effort -- Settings already renders fine with an empty list; this
            // isn't a money-moving action worth a dedicated error state for.
        }
    }

    suspend fun revokeDevice(deviceId: String) {
        try {
            NetworkClient.authApi.revokeDevice(deviceId)
            _deviceError.value = null
            fetchDevices()
        } catch (e: Exception) {
            _deviceError.value = "Couldn't remove this device. Try again."
        }
    }

    // Non-suspend wrapper for SettingsScreen's onClick, which has no coroutine scope
    // of its own to launch revokeDevice (a suspend fun) from.
    fun revokeDeviceFromSettings(deviceId: String) {
        viewModelScope.launch { revokeDevice(deviceId) }
    }

    // Real Keystore-signed-challenge device verification, client half (item 246) -- see
    // DeviceKeyManager's own doc comment. Same password bar as verifyDevice/revokeDevice
    // above (this is exactly as strong a trust decision, so it must cost exactly as
    // much): generates the hardware-backed key locally, then registers its public half
    // server-side in the same call that proves the password.
    fun registerDeviceKey(publicKeyBase64: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                NetworkClient.authApi.registerDeviceKey(
                    rw.itunda.core.network.RegisterDeviceKeyRequest(publicKeyBase64, password),
                )
                fetchDevices()
                onResult(true, null)
            } catch (e: retrofit2.HttpException) {
                // Real backend message pass-through first (2026-08-10), same fix as
                // backendErrorMessage/DeviceStepUpHost: this was discarding any specific
                // backend decline in favor of a hardcoded bucket.
                val message = rw.itunda.core.network.apiErrorMessage(e) ?: when (e.code()) {
                    400 -> "Incorrect password."
                    503, 504 -> "itunda is having a brief hiccup on our end -- not your password. Try again in a moment."
                    else -> "Something went wrong. Please try again."
                }
                onResult(false, message)
            } catch (_: IOException) {
                onResult(false, "Couldn't reach itunda. Check your connection and try again.")
            }
        }
    }

    // Real gap found 2026-08-08 (Toss Simplicity21 research): this used to switch on
    // HTTP status code alone, a 4-case map that fell through to a generic message for
    // every other real decline (self-payment, account-frozen, family spend limit, rate
    // limit) even though the backend already sends specific text for each -- bank-mfe's
    // ApiError already showed that real text, this didn't. Now prefers the real backend
    // message (rw.itunda.core.network.apiErrorMessage) and only falls back to a
    // per-status default when the body genuinely didn't parse.
    private fun backendErrorMessage(e: retrofit2.HttpException): String =
        rw.itunda.core.network.apiErrorMessage(e) ?: when (e.code()) {
            422 -> "Insufficient funds for this amount."
            404 -> "That account or goal couldn't be found."
            409 -> "This request is already being processed."
            502 -> "The payment provider declined this transaction."
            // Real gap found live (2026-08-10), same fix as superAppErrorMessage's own
            // -- distinct from 502 above, which is a real payment-PROVIDER decline
            // (MTN/Airtel's own API), not itunda's infrastructure. A 503/504 is
            // itunda's own side having a bad moment (e.g. a pod restarting) -- the
            // money itself was never at risk (no real transfer was attempted), so say
            // that plainly rather than leaving the user to wonder.
            503, 504 -> "itunda is having a brief hiccup on our end -- your money is safe, nothing was sent. Try again in a moment."
            else -> "Something went wrong. Please try again."
        }

    /**
     * Real phone-vs-account-number disambiguation for the numeric-keypad recipient
     * screen (2026-07-20) -- RecipientEntryScreen's real Toss-reference-matching design
     * (TransferFlow.kt, 2026-07-11) is a pure digit keypad with no "+" key, so a real
     * Rwandan mobile number typed there arrives as raw digits ("0788000001" or
     * "250788000001"), not the "+250XXXXXXXXX" form `User.phoneNumber` is actually
     * stored in (every real registration in this app uses that format). Same real,
     * sourced national-number shape `RailCatalog.resolveByPhoneNumber` already
     * recognizes on the backend (Rwanda's RURA numbering plan: a local number is
     * 0-prefixed + 9 digits, an international one is 250-prefixed + 9 digits) --
     * converged here to the canonical +250 form `sendDirect`'s exact-match phone lookup
     * needs, instead of stripped down to a bare national number. A real itunda account
     * number (always 10 digits starting 2024/2025, see AuthService.
     * generateAccountNumber) never matches either digit shape, so it passes through
     * unchanged and still resolves via `sendDirect`'s own account-number fallback.
     * Honestly scoped: a phone number stored in a genuinely non-standard format at
     * registration won't match this guess -- same accepted-limitation precedent
     * `RailCatalog`'s own doc comment already established ("deliberately
     * conservative... falls back rather than guessing").
     */
    private fun normalizeRecipientIdentifier(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("0") && digits.length == 10 -> "+250" + digits.substring(1)
            digits.startsWith("250") && digits.length == 12 -> "+$digits"
            else -> raw
        }
    }
}
