package rw.itunda.core.designsystem.components

// Real bridge between "My payment code" (MyPaymentCodeCard, ItundaAppScreen.kt) and
// TransitHceService (2026-08-27, direct user follow-up: "for simplification we need
// nfc"). A HostApduService is instantiated by the OS on its own, outside Compose's
// object graph -- there is no way to hand it the currently-revealed code directly, so
// this in-process singleton is the real bridge. Safe: it's the exact same short-lived
// (2-minute), single-use, non-secret-until-consumed bearer token already rendered
// on-screen as a QR/barcode for anyone nearby to photograph -- holding it in memory a
// moment longer for NFC broadcast doesn't weaken it.
object TransitPresentmentStore {
    @Volatile
    private var code: String? = null

    @Volatile
    private var expiresAtMillis: Long = 0L

    fun set(code: String, expiresAtMillis: Long) {
        this.code = code
        this.expiresAtMillis = expiresAtMillis
    }

    fun clear() {
        code = null
        expiresAtMillis = 0L
    }

    /** Null once the code has actually expired, even if `clear()` was never called --
     * a stale value must never be broadcast just because the screen wasn't closed. */
    fun currentCode(): String? {
        val c = code
        return if (c != null && System.currentTimeMillis() < expiresAtMillis) c else null
    }
}
