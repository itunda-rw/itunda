package rw.itunda.feature.pay.impl

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

internal const val MIN_REQUEST_AMOUNT = 100L
internal const val MAX_REQUEST_AMOUNT = 5_000_000L
internal const val MAX_INPUT_AMOUNT = 99_999_999L

@Stable
class RequestMoneyState(
    initialAmount: Long = 5_000L,
    initialNote: String = "",
) {
    var amount by mutableStateOf(initialAmount)
    var note by mutableStateOf(initialNote)
}

internal val defaultRequestMoneyState = RequestMoneyState()

internal data class RequestMoneyStrings(
    val title: String,
    val amount: String,
    val amountAccessibility: String,
    val amountHint: String,
    val minimumError: String,
    val maximumError: String,
    val note: String,
    val noteAccessibility: String,
    val notePlaceholder: String,
    val requestCode: String,
    val validFor: String,
    val enterAmount: String,
    val copy: String,
    val share: String,
    val copied: String,
)

internal fun requestMoneyStrings(language: String) = when (language) {
    "rw" -> RequestMoneyStrings(
        title = "Saba amafaranga",
        amount = "Amafaranga",
        amountAccessibility = "Amafaranga muri RWF",
        amountHint = "Uwishyura iyi code aguha aya mafaranga.",
        minimumError = "Ntoya ni 100 RWF.",
        maximumError = "Ntushobora gusaba ibirenze 5,000,000 RWF.",
        note = "Icyitonderwa (si ngombwa)",
        noteAccessibility = "Icyitonderwa",
        notePlaceholder = "Urugero: ifunguro rya sasita",
        requestCode = "Code y’ubusabe",
        validFor = "ifite agaciro amasaha 24 · code y’icyitegererezo",
        enterAmount = "Andika amafaranga ubone code",
        copy = "Kopora",
        share = "Sangiza ubusabe",
        copied = "Ubusabe bwakopowe",
    )
    "fr" -> RequestMoneyStrings(
        title = "Demander de l'argent",
        amount = "Montant",
        amountAccessibility = "Montant en RWF",
        amountHint = "Qui paie ce code vous envoie ce montant.",
        minimumError = "Minimum 100 RWF.",
        maximumError = "Maximum 5,000,000 RWF par demande.",
        note = "Note (facultatif)",
        noteAccessibility = "Note",
        notePlaceholder = "Par exemple, déjeuner",
        requestCode = "Code de demande",
        validFor = "valable 24 h · code d’exemple",
        enterAmount = "Saisissez un montant pour obtenir un code",
        copy = "Copier",
        share = "Partager la demande",
        copied = "Demande copiée",
    )
    else -> RequestMoneyStrings(
        title = "Request money",
        amount = "Amount",
        amountAccessibility = "Amount in RWF",
        amountHint = "Whoever pays the code sends you this amount.",
        minimumError = "The least you can request is 100 RWF.",
        maximumError = "The most you can request is 5,000,000 RWF.",
        note = "Note (optional)",
        noteAccessibility = "Note",
        notePlaceholder = "For example, lunch",
        requestCode = "Request code",
        validFor = "valid 24 hours · sample code",
        enterAmount = "Enter an amount to get a code",
        copy = "Copy",
        share = "Share request",
        copied = "Request copied",
    )
}

internal fun requestMoneyCode(amount: Long): String {
    val number = BigDecimal.valueOf(amount)
        .divide(BigDecimal.valueOf(100))
        .add(BigDecimal.valueOf(4_210))
        .stripTrailingZeros()
        .toPlainString()
        .padStart(4, '0')
    return "ITD-$number-90"
}
