package rw.itunda.feature.banking.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Prototype v1038 hub sections below "Savings & deposits": Together, Loans, Services, Good to know.
// Every row opens something real or a dummy screen; nothing here is a dead control.

private fun row(title: String, art: String, onClick: () -> Unit, action: String = ">", caption: String = "", badge: String? = null) =
    ShellRow(title, "", action, Icons.Outlined.AccountBalanceWallet, onClick = onClick, caption = caption, art = art, badge = badge)

@Composable
internal fun BankTogetherSection(onGroups: () -> Unit, onYouth: () -> Unit, onIkimina: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ShellSection("Together", listOf(row("Group accounts", "group-accounts", onGroups), row("Youth account", "youth", onYouth), row("Ikimina", "ikimina", onIkimina)))
    }
}

@Composable
internal fun BankLoansSection(onLoan: () -> Unit, onHarvest: () -> Unit, onStudent: () -> Unit, onVup: () -> Unit, onMoto: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ShellSection(
            "Loans",
            listOf(
                row("Get a loan", "loan", onLoan), row("Harvest advance", "harvest-advance", onHarvest),
                row("Student loan", "student-loan", onStudent, action = "11–12%", badge = "New"),
                row("VUP Financial Services", "vup", onVup), row("Moto-Taxi Ownership", "moto", onMoto, badge = "New"),
            ),
        )
    }
}

@Composable
internal fun BankServicesSection(onOpen: (BankSub) -> Unit, onSpending: () -> Unit, onDetails: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ShellSection(
            "Services",
            listOf(
                row("Move to itunda Pay", "move-pay", { onOpen(BankSub.MOVE) }), row("Send to a bank account", "send-bank", { onOpen(BankSub.SEND) }),
                row("Payees", "payees", { onOpen(BankSub.PAYEES) }), row("Scheduled transfers", "scheduled", { onOpen(BankSub.SCHEDULED) }),
                row("Statements", "statements", { onOpen(BankSub.STATEMENT) }), row("Spending", "spending", onSpending),
                row("Budget", "budget", { onOpen(BankSub.BUDGET) }), row("Limits & alerts", "limits", { onOpen(BankSub.LIMITS) }),
                row("Account details", "account", onDetails), row("Documents & letters", "documents", { onOpen(BankSub.DOCS) }),
                row("Report a problem", "report", { onOpen(BankSub.CASES) }),
            ),
        )
    }
}

@Composable
internal fun BankGoodToKnowSection(onCreditScore: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ShellSection("Good to know", listOf(row("My credit score", "credit-score", onCreditScore, action = "View")))
    }
}
