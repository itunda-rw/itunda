package rw.itunda.core.format

import java.math.BigDecimal

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
 */
fun formatAmount(amount: BigDecimal): String = "%,.0f".format(amount)
