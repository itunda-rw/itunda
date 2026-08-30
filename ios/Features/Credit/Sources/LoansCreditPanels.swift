import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention never reached this file, which predates that sweep's file list).
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

// Real fix (2026-08-26): split out of OverviewLoansCreditScoreScreens.swift once
// that file grew past its file-size-lint baseline. Overdraft and postpaid-credit
// are both real, self-contained credit-line panels only rendered inside
// LoansScreenView's own mode switch -- same split already done for Android's
// LoansScreen.kt. Moved into FeatureCredit (2026-08-30) alongside LoansScreenView.

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// backend OverdraftAccount.kt's own doc comment. Found 2026-07-29 via a full-backend-
// endpoint sweep: zero client anywhere on any of the 3 platforms before this.
struct OverdraftPanel: View {
    @State private var account: OverdraftAccountDto?
    @State private var loaded = false
    @State private var requestedLimit = "100000"
    @State private var drawAmount = ""
    @State private var repayAmount = ""
    @State private var busy = false
    @State private var error: String?
    @State private var notice: String?

    var body: some View {
        Group {
            if !loaded {
                ProgressView()
            } else if let account {
                let availableCredit = account.creditLimit - account.drawnBalance
                VStack(alignment: .leading, spacing: 6) {
                    Text("Overdraft line").bold()
                    Text("Drawn: \(formatAmount(Int(account.drawnBalance))) RWF of \(formatAmount(Int(account.creditLimit))) RWF").font(.subheadline)
                    Text("Available to draw: \(formatAmount(Int(availableCredit))) RWF · \(account.interestRate, specifier: "%.1f")% annual, interest only on what's drawn")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let notice { Text(notice).font(.caption).foregroundColor(IDS.Colors.brand) }
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    IdsTextField("Draw amount (RWF)", text: $drawAmount, keyboardType: .numberPad)
                    Button(action: { Task { await draw() } }) {
                        Text(busy ? "Drawing…" : "Draw").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                    IdsTextField("Repay amount (RWF)", text: $repayAmount, keyboardType: .numberPad)
                    Button(action: { Task { await repay() } }) {
                        Text(busy ? "Repaying…" : "Repay").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                    .disabled(busy || account.drawnBalance <= 0)
                }
                .padding(.vertical, 10)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Open an overdraft line").bold()
                    Text("A pre-approved credit limit you can draw from anytime -- pay interest only on what you actually use, up to 500,000 RWF.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    IdsTextField("Requested limit (RWF)", text: $requestedLimit, keyboardType: .numberPad)
                    Button(action: { Task { await open() } }) {
                        Text(busy ? "Opening…" : "Open overdraft").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
                .padding(.vertical, 10)
            }
        }
        .task {
            do { account = try await NetworkClient.shared.getMyOverdraft().account } catch { self.error = "Could not load your overdraft account." }
            loaded = true
        }
    }

    private func open() async {
        guard let limit = Double(requestedLimit), limit > 0 else { error = "Enter a valid credit limit."; return }
        busy = true; error = nil
        defer { busy = false }
        do {
            account = try await NetworkClient.shared.openOverdraft(requestedLimit: limit).account
        } catch {
            self.error = "Could not open an overdraft account."
        }
    }

    private func draw() async {
        guard let amount = Double(drawAmount), amount > 0 else { error = "Enter a valid amount to draw."; return }
        busy = true; error = nil; notice = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.drawOverdraft(amount: amount)
            if var current = account { current = OverdraftAccountDto(id: current.id, userId: current.userId, accountId: current.accountId, creditLimit: current.creditLimit, drawnBalance: res.drawnBalance, interestRate: current.interestRate, status: current.status); account = current }
            drawAmount = ""
            notice = "Drew \(formatAmount(Int(res.amount))) RWF — \(formatAmount(Int(res.availableCredit))) RWF still available."
        } catch {
            self.error = "Could not draw from your overdraft."
        }
    }

    private func repay() async {
        guard let amount = Double(repayAmount), amount > 0 else { error = "Enter a valid repayment amount."; return }
        busy = true; error = nil; notice = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.repayOverdraft(amount: amount)
            if var current = account { current = OverdraftAccountDto(id: current.id, userId: current.userId, accountId: current.accountId, creditLimit: current.creditLimit, drawnBalance: res.drawnBalance, interestRate: current.interestRate, status: current.status); account = current }
            repayAmount = ""
            notice = "Repaid \(formatAmount(Int(res.amount))) RWF — \(formatAmount(Int(res.availableCredit))) RWF now available."
        } catch {
            self.error = "Could not repay your overdraft."
        }
    }
}

// Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, real since
// 2026-07-31) -- first iOS client for this feature, mirroring bank-mfe's
// PostpaidCreditView.tsx and this screen's own OverdraftPanel shape exactly. Genuinely
// distinct from overdraft above: no requested-limit input (auto-computed from the
// caller's own real credit score), no interest shown for spending (only a real late fee
// if a cycle goes unpaid).
struct PostpaidCreditPanel: View {
    @State private var line: PostpaidCreditLineDto?
    @State private var loaded = false
    @State private var spendAmount = ""
    @State private var repayAmount = ""
    @State private var busy = false
    @State private var error: String?
    @State private var notice: String?

    var body: some View {
        Group {
            if !loaded {
                ProgressView()
            } else if let line {
                let availableCredit = line.creditLimit - line.currentBalance
                let suspended = line.status == "SUSPENDED"
                VStack(alignment: .leading, spacing: 6) {
                    Text("Postpaid credit").bold()
                    Text("Owed: \(formatAmount(Int(line.currentBalance))) RWF of \(formatAmount(Int(line.creditLimit))) RWF").font(.subheadline)
                    Text("Available: \(formatAmount(Int(availableCredit))) RWF · interest-free if repaid within 30 days")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if suspended {
                        Text("Suspended — repay your overdue balance to keep spending.").font(.caption).foregroundColor(.red)
                    }
                    if let notice { Text(notice).font(.caption).foregroundColor(IDS.Colors.brand) }
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    IdsTextField("Spend amount (RWF)", text: $spendAmount, keyboardType: .numberPad).disabled(suspended)
                    Button(action: { Task { await spend() } }) {
                        Text(busy ? "Adding…" : "Add to account").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy || suspended)
                    IdsTextField("Repay amount (RWF)", text: $repayAmount, keyboardType: .numberPad)
                    Button(action: { Task { await repay() } }) {
                        Text(busy ? "Repaying…" : "Repay").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                    .disabled(busy || line.currentBalance <= 0)
                }
                .padding(.vertical, 10)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Get postpaid credit").bold()
                    Text("A small credit line for real purchases, interest-free if you pay within 30 days — your limit is set automatically from your credit score, up to 300,000 RWF.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    Button(action: { Task { await apply() } }) {
                        Text(busy ? "Applying…" : "Get postpaid credit").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
                .padding(.vertical, 10)
            }
        }
        .task {
            do { line = try await NetworkClient.shared.getMyPostpaidCredit().line } catch { self.error = "Could not load your postpaid credit line." }
            loaded = true
        }
    }

    private func apply() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            line = try await NetworkClient.shared.applyForPostpaidCredit().line
        } catch {
            self.error = "Could not open a postpaid credit line."
        }
    }

    private func spend() async {
        guard let amount = Double(spendAmount), amount > 0 else { error = "Enter a valid amount to spend."; return }
        busy = true; error = nil; notice = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.spendPostpaidCredit(amount: amount)
            if var current = line { current = PostpaidCreditLineDto(id: current.id, userId: current.userId, accountId: current.accountId, creditLimit: current.creditLimit, currentBalance: res.currentBalance, status: current.status, cycleDueAt: current.cycleDueAt, lastLateFeeAccrualAt: current.lastLateFeeAccrualAt, createdAt: current.createdAt, updatedAt: current.updatedAt); line = current }
            spendAmount = ""
            notice = "Added \(formatAmount(Int(res.amount))) RWF to your account — \(formatAmount(Int(res.availableCredit))) RWF still available."
        } catch {
            self.error = "Could not spend from your postpaid credit line."
        }
    }

    private func repay() async {
        guard let amount = Double(repayAmount), amount > 0 else { error = "Enter a valid repayment amount."; return }
        busy = true; error = nil; notice = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.repayPostpaidCredit(amount: amount)
            if var current = line {
                let newStatus = res.currentBalance <= 0 ? "ACTIVE" : current.status
                current = PostpaidCreditLineDto(id: current.id, userId: current.userId, accountId: current.accountId, creditLimit: current.creditLimit, currentBalance: res.currentBalance, status: newStatus, cycleDueAt: current.cycleDueAt, lastLateFeeAccrualAt: current.lastLateFeeAccrualAt, createdAt: current.createdAt, updatedAt: current.updatedAt)
                line = current
            }
            repayAmount = ""
            notice = "Repaid \(formatAmount(Int(res.amount))) RWF — \(formatAmount(Int(res.availableCredit))) RWF now available."
        } catch {
            self.error = "Could not repay your postpaid credit line."
        }
    }
}
