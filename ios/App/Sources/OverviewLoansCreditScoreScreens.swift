import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss-style unified account overview, multi-lender loan marketplace, credit
// score, digital certificate, personal KYC identity submission, and customer support
// tickets (2026-07-22 port) -- every one of these was found fully built on the
// backend during a full backend-vs-app audit, with zero client UI on Android/bank-mfe
// either until the same-day ports that preceded this one. See
// docs/TOSS_PARITY_MATRIX.md's own per-row notes for the full account of each gap.

private let linkProviders = ["MTN Mobile Money", "Airtel Money", "Bank of Kigali", "Equity Bank Rwanda"]

struct OverviewScreenView: View {
    var onBack: () -> Void = {}
    @State private var overview: OverviewResponse?
    @State private var linkedAccounts: [LinkedAccountDto] = []
    @State private var error: String?
    @State private var busy = false
    @State private var showLinkForm = false
    @State private var provider = ""
    @State private var accountNumber = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("My assets").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let overview {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Net worth").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(Int(overview.netWorth)) RWF").font(.title).bold()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Accounts").bold()
                            ForEach(overview.accounts) { a in
                                HStack { Text("\(a.name) (\(a.type))"); Spacer(); Text("\(a.currency) \(Int(a.balance))") }.font(.subheadline)
                            }
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Savings: \(Int(overview.savings.totalSaved)) RWF across \(overview.savings.goalCount) goal(s)").font(.subheadline)
                            Text("Loans: \(Int(overview.loans.totalOutstanding)) RWF outstanding, \(overview.loans.activeCount) active").font(.subheadline)
                            Text("Investments: \(Int(overview.investments.totalCostBasis)) RWF cost basis, \(overview.investments.holdingCount) holding(s)").font(.subheadline)
                            Text("Insurance: \(overview.insurance.activePolicyCount) active plan(s), \(Int(overview.insurance.totalMonthlyPremium)) RWF/month").font(.subheadline)
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Linked accounts").bold()
                            ForEach(linkedAccounts) { account in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(account.provider).bold()
                                    Text("\(account.externalAccountNumberMasked) · \(account.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    if let demoBalance = account.demoBalance {
                                        Text("Demo balance: \(account.demoBalanceCurrency ?? "") \(Int(demoBalance))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    if account.status == "LINKED" {
                                        Button(action: { Task { await unlink(account.id) } }) {
                                            Text("Unlink").font(.caption).bold()
                                        }
                                        .disabled(busy)
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                            if !showLinkForm {
                                Button(action: { showLinkForm = true }) {
                                    Text("Link a bank or mobile money account").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                                }
                            } else {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        ForEach(linkProviders, id: \.self) { p in
                                            Button(p) { provider = p }.font(.caption).bold()
                                        }
                                    }
                                    TextField("Provider name", text: $provider).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                    TextField("Account / phone number", text: $accountNumber).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                    Button(action: { Task { await link() } }) {
                                        Text(busy ? "Linking…" : "Link account").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busy || provider.isEmpty || accountNumber.isEmpty)
                                }
                            }
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
    }

    private func refresh() async {
        do {
            overview = try await NetworkClient.shared.getOverview()
            linkedAccounts = try await NetworkClient.shared.getLinkedAccounts().linkedAccounts
            error = nil
        } catch { self.error = "Could not load your overview." }
    }

    private func link() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.linkAccount(provider: provider, externalAccountNumber: accountNumber)
            provider = ""; accountNumber = ""; showLinkForm = false
            await refresh()
        } catch { self.error = "Could not link that account." }
    }

    private func unlink(_ accountId: String) async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.unlinkAccount(accountId: accountId)
            await refresh()
        } catch { self.error = "Could not unlink this account." }
    }
}

private enum LoansMode: String, CaseIterable { case offers = "Offers", myLoans = "My loans", overdraft = "Overdraft" }

struct LoansScreenView: View {
    var onBack: () -> Void = {}
    @State private var mode: LoansMode = .offers
    @State private var offers: [LoanOfferDto]?
    @State private var myLoans: [LoanAccountDto]?
    @State private var lenders: [LenderDto]?
    @State private var lenderId: String?
    @State private var error: String?
    @State private var busyId: String?
    @State private var repayAmounts: [String: String] = [:]
    @State private var refinanceResult: RefinanceResult?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Loans").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $mode) {
                ForEach(LoansMode.allCases, id: \.self) { m in Text(m.rawValue).tag(m) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let refinanceResult {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Refinanced into \(refinanceResult.newLoanName)").font(.subheadline).bold()
                            Text("\(refinanceResult.oldInterestRate, specifier: "%.1f")% → \(refinanceResult.newInterestRate, specifier: "%.1f")%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                    if mode == .offers {
                        if let lenders {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    lenderChip(title: "All lenders", selected: lenderId == nil) { Task { await selectLender(nil) } }
                                    ForEach(lenders) { lender in
                                        lenderChip(title: lender.name, selected: lenderId == lender.id) { Task { await selectLender(lender.id) } }
                                    }
                                }
                            }
                        }
                        if let offers {
                            if offers.isEmpty { Text("No offers from this lender right now.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                            ForEach(offers) { offer in LoanOfferCard(offer: offer, busy: busyId == offer.id, onApply: { amount in Task { await apply(offer, amount) } }) }
                        } else { ProgressView() }
                    } else if mode == .myLoans {
                        if let myLoans {
                            if myLoans.isEmpty { Text("You have no loans yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                            ForEach(myLoans) { loan in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("\(Int(loan.principal)) RWF loan").bold()
                                    Text("Outstanding: \(Int(loan.outstanding)) RWF").font(.subheadline)
                                    Text("Status: \(loan.status) · \(loan.interestRate)%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    if loan.status == "ACTIVE" {
                                        TextField("Repay amount (RWF)", text: Binding(
                                            get: { repayAmounts[loan.id] ?? "" },
                                            set: { repayAmounts[loan.id] = $0 }
                                        )).keyboardType(.numberPad).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
                                        Button(action: { Task { await repay(loan) } }) {
                                            Text(busyId == loan.id ? "Repaying…" : "Repay").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                                        }
                                        .disabled(busyId != nil)
                                        Button(action: { Task { await refinance(loan) } }) {
                                            Text(busyId == loan.id ? "Checking…" : "Refinance to a lower rate").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                        }
                                        .disabled(busyId != nil)
                                    }
                                }
                                .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                            }
                        } else { ProgressView() }
                    } else {
                        OverdraftPanel()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
    }

    private func refresh() async {
        do {
            offers = try await NetworkClient.shared.getLoanOffers().offers
            myLoans = try await NetworkClient.shared.getMyLoans().loans
            lenders = try await NetworkClient.shared.getLenders().lenders
            error = nil
        } catch { self.error = "Could not load loans." }
    }

    // Real "browse by lender" filter (2026-07-29 iOS port) -- see the bank-mfe port's own
    // comment: `getLenders`/`lenderId`-filtered `getLoanOffers` were both real backend
    // endpoints with zero client anywhere before this.
    private func selectLender(_ id: String?) async {
        lenderId = id
        error = nil
        do {
            offers = try await NetworkClient.shared.getLoanOffers(lenderId: id).offers
        } catch { self.error = "Could not load offers." }
    }

    private func lenderChip(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.caption).bold()
                .foregroundColor(selected ? IDS.Colors.brand : IDS.Colors.textPrimary)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(IDS.Colors.chipBackground)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected ? IDS.Colors.brand : .clear, lineWidth: 1))
                .cornerRadius(14)
        }
    }

    private func apply(_ offer: LoanOfferDto, _ amount: Double) async {
        busyId = offer.id; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.applyForLoan(loanId: offer.id, amount: amount)
            mode = .myLoans
            await refresh()
        } catch { self.error = "That loan application could not be completed." }
    }

    private func repay(_ loan: LoanAccountDto) async {
        guard let amount = Double(repayAmounts[loan.id] ?? "") else { return }
        busyId = loan.id; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.repayLoan(loanId: loan.id, amount: amount)
            repayAmounts[loan.id] = nil
            await refresh()
        } catch { self.error = "That repayment could not be completed." }
    }

    // Real 대환대출 (loan refinancing, 2026-07-29 iOS port) -- see NetworkClient's own
    // RefinanceLoanRequest comment; bank-mfe-only since 2026-07-26, ported to Android
    // the same session as this iOS port.
    private func refinance(_ loan: LoanAccountDto) async {
        busyId = loan.id; error = nil
        defer { busyId = nil }
        do {
            refinanceResult = try await NetworkClient.shared.refinanceLoan(loanId: loan.id)
            await refresh()
        } catch { self.error = "No better rate is available for this loan right now." }
    }
}

private struct LoanOfferCard: View {
    let offer: LoanOfferDto
    let busy: Bool
    let onApply: (Double) -> Void
    @State private var amountText: String

    init(offer: LoanOfferDto, busy: Bool, onApply: @escaping (Double) -> Void) {
        self.offer = offer; self.busy = busy; self.onApply = onApply
        _amountText = State(initialValue: String(Int(offer.maxAmount)))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(offer.name).bold()
            Text(offer.lenderName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            Text("Up to \(Int(offer.maxAmount)) RWF · \(offer.interestRate)% · \(offer.term)").font(.subheadline)
            Text(offer.requirements).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            TextField("Amount (RWF)", text: $amountText).keyboardType(.numberPad).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
            Button(action: { if let n = Double(amountText), n > 0 { onApply(n) } }) {
                Text(busy ? "Applying…" : "Apply").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
            }
            .disabled(busy)
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// backend OverdraftAccount.kt's own doc comment. Found 2026-07-29 via a full-backend-
// endpoint sweep: zero client anywhere on any of the 3 platforms before this.
private struct OverdraftPanel: View {
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
                    Text("Drawn: \(Int(account.drawnBalance)) RWF of \(Int(account.creditLimit)) RWF").font(.subheadline)
                    Text("Available to draw: \(Int(availableCredit)) RWF · \(account.interestRate, specifier: "%.1f")% annual, interest only on what's drawn")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let notice { Text(notice).font(.caption).foregroundColor(IDS.Colors.brand) }
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    TextField("Draw amount (RWF)", text: $drawAmount).keyboardType(.numberPad).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
                    Button(action: { Task { await draw() } }) {
                        Text(busy ? "Drawing…" : "Draw").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                    TextField("Repay amount (RWF)", text: $repayAmount).keyboardType(.numberPad).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
                    Button(action: { Task { await repay() } }) {
                        Text(busy ? "Repaying…" : "Repay").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                    .disabled(busy || account.drawnBalance <= 0)
                }
                .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Open an overdraft line").bold()
                    Text("A pre-approved credit limit you can draw from anytime -- pay interest only on what you actually use, up to 500,000 RWF.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    TextField("Requested limit (RWF)", text: $requestedLimit).keyboardType(.numberPad).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
                    Button(action: { Task { await open() } }) {
                        Text(busy ? "Opening…" : "Open overdraft").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
                .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
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
            if var current = account { current = OverdraftAccountDto(id: current.id, userId: current.userId, walletId: current.walletId, creditLimit: current.creditLimit, drawnBalance: res.drawnBalance, interestRate: current.interestRate, status: current.status); account = current }
            drawAmount = ""
            notice = "Drew \(Int(res.amount)) RWF — \(Int(res.availableCredit)) RWF still available."
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
            if var current = account { current = OverdraftAccountDto(id: current.id, userId: current.userId, walletId: current.walletId, creditLimit: current.creditLimit, drawnBalance: res.drawnBalance, interestRate: current.interestRate, status: current.status); account = current }
            repayAmount = ""
            notice = "Repaid \(Int(res.amount)) RWF — \(Int(res.availableCredit)) RWF now available."
        } catch {
            self.error = "Could not repay your overdraft."
        }
    }
}

// CreditScoreScreenView moved to Features/Credit/Sources/CreditScoreScreenView.swift
// (2026-07-23) -- the first real screen in that Feature module, see
// docs/MULTI_AGENT_ISOLATION.md.

// Real digital identity/signing certificate (2026-07-22 port) -- this feature was
// already real and live-verified on bank-mfe (web) since 2026-07-17, and ported to
// Android the same day as this iOS port; both used it as the reference for a
// platform-parity gap, not a never-built feature.
struct CertificateScreenView: View {
    var onBack: () -> Void = {}
    @State private var certificate: CertificateDto?
    @State private var loaded = false
    @State private var issuedPrivateKey: String?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Itunda Certificate").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !loaded {
                        ProgressView()
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("A digital certificate you can use to sign agreements in Itunda. You'll need a verified identity first.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if let certificate, certificate.status == "ACTIVE" {
                                Text("Active").bold().foregroundColor(.green)
                                Text("Serial \(certificate.serialNumber)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Text("Expires \(certificate.expiresAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Button(action: { Task { await revoke() } }) {
                                    Text(busy ? "Revoking…" : "Revoke certificate").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                }
                                .disabled(busy)
                            } else {
                                if let certificate {
                                    Text("Your previous certificate was \(certificate.status.lowercased()).").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Button(action: { Task { await issue() } }) {
                                    Text(busy ? "Issuing…" : "Issue a certificate").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy)
                            }
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)

                        if let issuedPrivateKey {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Save this private key now — you won't be able to see it again.").font(.subheadline).bold().foregroundColor(.orange)
                                Text(issuedPrivateKey).font(.system(.caption, design: .monospaced))
                            }
                            .padding(16).background(Color.orange.opacity(0.1)).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                        if let error { Text(error).font(.caption).foregroundColor(.red) }
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do { certificate = try await NetworkClient.shared.getMyCertificate().certificate } catch { self.error = "Could not load your certificate." }
            loaded = true
        }
    }

    private func issue() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            let result = try await NetworkClient.shared.issueCertificate()
            certificate = result.certificate
            issuedPrivateKey = result.privateKey
        } catch NetworkError.httpError(let code) where code == 403 {
            error = "You need a verified identity before you can issue a certificate."
        } catch {
            self.error = "Could not issue a certificate."
        }
    }

    private func revoke() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            certificate = try await NetworkClient.shared.revokeCertificate().certificate
            issuedPrivateKey = nil
        } catch { self.error = "Could not revoke your certificate." }
    }
}

// Real personal KYC identity submission (2026-07-22) -- found fully built on the
// backend (rw.itunda.identity) with zero client UI anywhere; merchant-mfe already has
// KYB submission and ops-mfe the review queue, but this ordinary personal
// NATIONAL_ID/PASSPORT submission had zero UI on any client.
private let identityDocumentTypes = ["NATIONAL_ID", "PASSPORT"]

struct IdentityScreenView: View {
    var onBack: () -> Void = {}
    @State private var submissions: [KycSubmissionDto]?
    @State private var error: String?
    @State private var busy = false
    @State private var documentType = identityDocumentTypes[0]
    @State private var documentNumber = ""
    @State private var documentReference = ""

    private var hasPending: Bool { submissions?.contains { $0.status == "PENDING" } ?? false }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Verify your identity").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if hasPending {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Submission pending review").bold()
                            Text("We'll update your status once it's reviewed.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                ForEach(identityDocumentTypes, id: \.self) { t in
                                    Button(t) { documentType = t }.font(.caption).bold()
                                        .foregroundColor(documentType == t ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(documentType == t ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(999)
                                }
                            }
                            TextField("Document number", text: $documentNumber).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            TextField("Document reference (scan/photo reference)", text: $documentReference).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit for review").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || documentNumber.isEmpty || documentReference.isEmpty)
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }

                    Text("Your submissions").bold()
                    if let submissions {
                        if submissions.isEmpty { Text("You have no submissions yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                        ForEach(submissions) { s in
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(s.documentType) · \(s.documentNumber)").bold()
                                Text("Status: \(s.status)").font(.subheadline)
                                if let reason = s.decisionReason { Text(reason).font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                Text("Filed: \(s.submittedAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
    }

    private func refresh() async {
        do { submissions = try await NetworkClient.shared.getIdentityStatus().submissions; error = nil }
        catch { self.error = "Could not load your identity status." }
    }

    private func submit() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.submitIdentity(documentType: documentType, documentNumber: documentNumber, documentReference: documentReference)
            documentNumber = ""; documentReference = ""
            await refresh()
        } catch { self.error = "That submission could not be completed." }
    }
}

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere. A ticket is always tied to a
// specific transaction (see SupportTicket.kt's own doc comment for why), so this
// screen has the user pick one from their real transaction history rather than
// filing a free-floating complaint.
private let supportCategories = ["GENERAL", "PAYMENT_DISPUTE", "ACCOUNT_TAKEOVER"]

struct SupportScreenView: View {
    var onBack: () -> Void = {}
    @State private var tickets: [SupportTicketDto]?
    @State private var transactions: [TransactionDto] = []
    @State private var error: String?
    @State private var busy = false
    @State private var showNewForm = false
    @State private var selectedTransactionId: String?
    @State private var category = supportCategories[0]
    @State private var descriptionText = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Support").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if !showNewForm {
                        Button(action: { showNewForm = true }) {
                            Text("Report an issue with a transaction").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Which transaction?").font(.caption).bold()
                            ForEach(transactions.prefix(10), id: \.id) { tx in
                                HStack {
                                    Text("\(tx.description) · \(tx.currency) \(Int(tx.amount))").font(.subheadline)
                                    Spacer()
                                    Image(systemName: selectedTransactionId == tx.id ? "largecircle.fill.circle" : "circle")
                                }
                                .onTapGesture { selectedTransactionId = tx.id }
                            }
                            Text("Category").font(.caption).bold()
                            HStack {
                                ForEach(supportCategories, id: \.self) { c in
                                    Button(c) { category = c }.font(.caption).bold()
                                        .foregroundColor(category == c ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 8).padding(.vertical, 6)
                                        .background(category == c ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(8)
                                }
                            }
                            TextField("Describe the issue", text: $descriptionText).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit ticket").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || selectedTransactionId == nil || descriptionText.isEmpty)
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }

                    Text("Your tickets").bold()
                    if let tickets {
                        if tickets.isEmpty { Text("You have no support tickets.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                        ForEach(tickets) { t in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(t.category).bold()
                                Text(t.description).font(.subheadline)
                                Text("Status: \(t.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                if let resolution = t.resolution { Text("Resolution: \(resolution)").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                Text("Filed: \(t.createdAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
    }

    private func refresh() async {
        do {
            tickets = try await NetworkClient.shared.getSupportTickets().tickets
            transactions = try await NetworkClient.shared.getTransactionHistory().transactions
            error = nil
        } catch { self.error = "Could not load support tickets." }
    }

    private func submit() async {
        guard let transactionId = selectedTransactionId else { return }
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.createSupportTicket(transactionId: transactionId, category: category, description: descriptionText)
            showNewForm = false; selectedTransactionId = nil; descriptionText = ""
            await refresh()
        } catch { self.error = "That ticket could not be submitted." }
    }
}
