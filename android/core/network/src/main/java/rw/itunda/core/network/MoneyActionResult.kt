package rw.itunda.core.network

/** Shared outcome type for real money-moving calls (transfer/deposit/claim) --
 * distinct from AuthResult (SessionManager.kt) since these carry a user-facing
 * amount/balance, not a session. Queued (2026-07-13) is distinct from Success: the
 * action wasn't actually executed yet, only durably saved locally for replay once
 * connectivity returns -- see OfflineActionQueue.kt.
 *
 * Promoted here from :app's MainViewModel.kt (2026-09-02, Banking Feature-module
 * decomposition slice 2) so both :app (MainViewModel itself) and any Feature module
 * whose UI takes a money-moving callback (starting with :features:banking:impl's
 * BankHubScreen/NewSavingsGoalDialog) can reference the same real type without
 * :features depending back on :app. */
sealed interface MoneyActionResult {
    // fraudWarnings added 2026-09-02 (Toss security research thread) -- real,
    // friendly post-send fraud-heuristic warnings, so far only ever populated by
    // sendDirect's own response. Defaults to empty so every other Success(...)
    // caller (savings deposits/claims, which never carry a fraud warning) is
    // completely unaffected.
    // goalCompleted added 2026-09-05 (see feedback_idempotency_key_sweep-adjacent
    // UX-writing audit, project_itunda_product_feel memory) -- carries whether
    // this specific deposit's response reported the goal as SavingsGoalStatus
    // .completed, so depositToSavingsGoal's caller can tell a genuine
    // active->completed transition (the ItundaAppScreen.kt deposit-success
    // handler still needs to compare against the goal's own pre-call status,
    // since this alone doesn't distinguish "just completed" from "already was
    // completed" -- see SavingsFlowStep.Deposit's own wasAlreadyCompleted field).
    // Defaults to false so every other Success(...) caller is unaffected.
    data class Success(val message: String, val fraudWarnings: List<String> = emptyList(), val goalCompleted: Boolean = false) : MoneyActionResult
    data class Queued(val message: String) : MoneyActionResult
    data class Failure(val message: String) : MoneyActionResult
    // Real device binding (2026-07-21 port) -- a real 403 DEVICE_NOT_VERIFIED (this
    // device hasn't been step-up-verified yet) gets its own case, not a generic
    // Failure, since the caller has a real, actionable next step (re-enter password,
    // then retry). Mirrors bank-mfe's needsDeviceVerification handling exactly.
    data object DeviceNotVerified : MoneyActionResult
}
