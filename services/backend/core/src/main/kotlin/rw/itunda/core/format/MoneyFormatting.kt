package rw.itunda.core.format

import java.math.BigDecimal
import java.util.Locale

/**
 * The one real backend-side money-display formatter -- "10,346" not "10346", no
 * decimal places, matching the exact convention `LedgerFormatting.kt`'s
 * `"%,.0f $currency".format(amount)` already established on Android (see
 * `project_itunda_money_formatting_sweep` memory for the original client-side sweep
 * this mirrors). Found live 2026-09-05: `PriceOfferService`/`PropertyPriceOfferService`
 * sent real buyer/seller chat messages ("Offered 150000 RWF") with no thousands
 * separator at all, while `GiftService`/`GiftVoucherService`/`SplitBillService` each
 * separately reimplemented a correct-but-duplicated comma-grouping helper -- five
 * copies of a one-line concern, one of them wrong. Every caller should use this
 * instead of writing its own.
 *
 * Real gap found 2026-09-06, same class as the web/Android/iOS client-side locale
 * bug fixed the same session: this is server-rendered text persisted into real chat
 * messages/USSD responses (every caller sends the result straight into a stored
 * message or a live USSD session) -- `"%,.0f".format(...)` with no `Locale` resolves
 * to the JVM PROCESS's own default locale (`Locale.getDefault()`), which depends on
 * the container/base-image environment, not any itunda-controlled setting (this
 * repo's own `Dockerfile` sets no `LANG`/`LC_ALL`). Unlike the client-side bugs (only
 * some users' devices are affected), a wrong JVM default locale here would
 * mis-format money for EVERY user's stored message identically. Pinned `Locale.US`
 * explicitly so this can never depend on the runtime environment's own locale
 * configuration, regardless of what that configuration happens to be today.
 */
fun formatAmount(amount: BigDecimal): String = String.format(Locale.US, "%,.0f", amount)
