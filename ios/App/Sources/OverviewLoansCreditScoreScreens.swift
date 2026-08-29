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
// Real friction point found live via Toss Simplicity21 research (2026-08-08, session 2-1
// "신은 디테일에 있다" -- eliminating friction from a real bank-linking flow): for a MoMo
// provider, the "account number" IS the caller's own real phone number -- the same number
// they're already logged in with. Making them retype it is unnecessary friction with a
// real, already-known answer, the exact shape that session's own title names.
private let momoLinkProviders: Set<String> = ["MTN Mobile Money", "Airtel Money"]

// Real second iOS screen localized (2026-08-08), following web/Android's own identical
// "phase content outward from login into account overview" step (docs/DESIGN_REFERENCES.md
// Section 19). Reuses AppLocale/loadStoredLocale from LoginScreen.swift (same target,
// promoted to internal there for exactly this reuse) rather than duplicating locale
// detection a second time. Includes overview.verificationFailed from the start -- web's own
// first pass over this exact screen missed that key entirely (only caught while porting to
// Android), so it's added here up front rather than repeating that omission a third time.
// Real "My assets" tab-by-tab redesign (2026-08-27, direct user reference: 3 real
// Toss "총자산" screenshots, "this is how my asset screen should look like"). One
// key per new tab label / real-card sentence / teaser message+CTA -- see
// OverviewService.kt's own doc comments for exactly how each new category is sourced.
private let overviewStrings: [AppLocale: [String: String]] = [
    .en: [
        "title": "My assets",
        "loadError": "Could not load your overview.",
        "netWorth": "Net worth",
        "accounts": "Accounts",
        "savings": "Savings: %d RWF across %d goal(s)",
        "loans": "Loans: %d RWF outstanding, %d active",
        "investments": "Investments: %d RWF cost basis, %d holding(s)",
        "insurance": "Insurance: %d active plan(s), %d RWF/month",
        "linkedAccounts": "Linked accounts",
        "demoBalance": "Demo balance: %@ %d",
        "unlink": "Unlink",
        "linkPrompt": "Link a bank or mobile money account",
        "providerNamePlaceholder": "Provider name",
        "accountPhonePlaceholder": "Account / phone number",
        "linking": "Linking…",
        "linkAccount": "Link account",
        "linkError": "Could not link that account.",
        "unlinkError": "Could not unlink this account.",
        "verificationFailed": "Could not verify that %@ account. It wasn't linked.",
        "tabCards": "Cards", "tabLoans": "Loans", "tabInvestment": "Investment", "tabInsurance": "Insurance",
        "tabRealEstate": "Real estate", "tabCar": "Car", "tabTax": "Tax", "tabPoints": "Points",
        "manage": "Manage",
        "cardNumber": "Card •••• %@", "cardActive": "Active", "cardFrozen": "Frozen",
        "carSummary": "%d vehicle(s), %d RWF purchase price",
        "taxSummary": "%d tax payment(s), %d RWF paid",
        "pointsSummary": "%d RWF rewards earned",
        "payMoneyBalance": "Pay Money balance: %d RWF",
        "teaserCards": "No card yet", "teaserCardsCta": "Get a card",
        "teaserLoans": "No loans yet", "teaserLoansCta": "Browse loan offers",
        "teaserInvestment": "No investments yet", "teaserInvestmentCta": "Start investing",
        "teaserInsurance": "No insurance yet", "teaserInsuranceCta": "Browse plans",
        "teaserRealEstate": "No real estate linked", "teaserRealEstateCta": "Explore real estate",
        "teaserCar": "No vehicle yet", "teaserCarCta": "Add a vehicle",
    ],
    .rw: [
        "title": "Umutungo wanjye",
        "loadError": "Ntibishoboka gushaka amakuru y'umutungo wawe.",
        "netWorth": "Umutungo wose",
        "accounts": "Konti",
        "savings": "Ubwizigame: RWF %d mu migambi %d",
        "loans": "Inguzanyo: RWF %d zisigaye, %d zikoreshwa",
        "investments": "Ishoramari: RWF %d yatanzwe, ibintu %d",
        "insurance": "Ubwishingizi: gahunda %d zikora, RWF %d ku kwezi",
        "linkedAccounts": "Konti zihujwe",
        "demoBalance": "Amafaranga y'ikitegererezo: %@ %d",
        "unlink": "Kuraho ihuza",
        "linkPrompt": "Huza konti ya banki cyangwa Mobile Money",
        "providerNamePlaceholder": "Izina ry'ikigo",
        "accountPhonePlaceholder": "Numero ya konti / telefoni",
        "linking": "Guhuza…",
        "linkAccount": "Huza konti",
        "linkError": "Ntibishoboka guhuza iyo konti.",
        "unlinkError": "Ntibishoboka kuraho iyo konti.",
        "verificationFailed": "Ntibishoboka kwemeza iyo konti ya %@. Ntiyahujwe.",
        "tabCards": "Amakarita", "tabLoans": "Inguzanyo", "tabInvestment": "Ishoramari", "tabInsurance": "Ubwishingizi",
        "tabRealEstate": "Imitungo itimukanwa", "tabCar": "Imodoka", "tabTax": "Imisoro", "tabPoints": "Amanota",
        "manage": "Gucunga",
        "cardNumber": "Ikarita •••• %@", "cardActive": "Irakora", "cardFrozen": "Yahagaritswe",
        "carSummary": "Imodoka %d, RWF %d y'igiciro cyo kugura",
        "taxSummary": "Kwishyura umusoro %d, RWF %d yishyuwe",
        "pointsSummary": "RWF %d y'ibihembo byabonetse",
        "payMoneyBalance": "Amafaranga ya Pay Money: RWF %d",
        "teaserCards": "Nta karita ufite", "teaserCardsCta": "Bona ikarita",
        "teaserLoans": "Nta nguzanyo ufite", "teaserLoansCta": "Reba inguzanyo zihari",
        "teaserInvestment": "Nta shoramari ufite", "teaserInvestmentCta": "Tangira gushora imari",
        "teaserInsurance": "Nta bwishingizi ufite", "teaserInsuranceCta": "Reba gahunda zihari",
        "teaserRealEstate": "Nta mutungo utimukanwa uhujwe", "teaserRealEstateCta": "Reba imitungo itimukanwa",
        "teaserCar": "Nta modoka ufite", "teaserCarCta": "Ongeraho imodoka",
    ],
    // Real French added 2026-08-15, same session as LoginScreen.swift's AppLocale
    // widening to .fr -- kept together, not left English-only a second time (the
    // exact mistake this file's own doc comment above already names web's first pass
    // over this same screen making with `verification failed`, now avoided here too).
    .fr: [
        "title": "Mon patrimoine",
        "loadError": "Impossible de charger votre aperçu.",
        "netWorth": "Valeur nette",
        "accounts": "Comptes",
        "savings": "Épargne : RWF %d sur %d objectif(s)",
        "loans": "Prêts : RWF %d restant, %d actif(s)",
        "investments": "Investissements : RWF %d de valeur d'acquisition, %d position(s)",
        "insurance": "Assurance : %d plan(s) actif(s), RWF %d/mois",
        "linkedAccounts": "Comptes liés",
        "demoBalance": "Solde de démonstration : %@ %d",
        "unlink": "Dissocier",
        "linkPrompt": "Lier un compte bancaire ou mobile money",
        "providerNamePlaceholder": "Nom du fournisseur",
        "accountPhonePlaceholder": "Compte / numéro de téléphone",
        "linking": "Liaison en cours…",
        "linkAccount": "Lier le compte",
        "linkError": "Impossible de lier ce compte.",
        "unlinkError": "Impossible de dissocier ce compte.",
        "verificationFailed": "Impossible de vérifier ce compte %@. Il n'a pas été lié.",
        "tabCards": "Cartes", "tabLoans": "Prêts", "tabInvestment": "Placements", "tabInsurance": "Assurance",
        "tabRealEstate": "Immobilier", "tabCar": "Voiture", "tabTax": "Impôts", "tabPoints": "Points",
        "manage": "Gérer",
        "cardNumber": "Carte •••• %@", "cardActive": "Active", "cardFrozen": "Bloquée",
        "carSummary": "%d véhicule(s), %d RWF de prix d'achat",
        "taxSummary": "%d paiement(s) d'impôt, %d RWF payés",
        "pointsSummary": "%d RWF de récompenses gagnées",
        "payMoneyBalance": "Solde Pay Money : %d RWF",
        "teaserCards": "Pas encore de carte", "teaserCardsCta": "Obtenir une carte",
        "teaserLoans": "Pas encore de prêt", "teaserLoansCta": "Voir les offres de prêt",
        "teaserInvestment": "Pas encore de placement", "teaserInvestmentCta": "Commencer à investir",
        "teaserInsurance": "Pas encore d'assurance", "teaserInsuranceCta": "Voir les formules",
        "teaserRealEstate": "Aucun bien immobilier lié", "teaserRealEstateCta": "Explorer l'immobilier",
        "teaserCar": "Pas encore de véhicule", "teaserCarCta": "Ajouter un véhicule",
    ],
]

struct OverviewScreenView: View {
    var onBack: () -> Void = {}
    var onOpenCard: () -> Void = {}
    var onOpenLoans: () -> Void = {}
    var onOpenInvest: () -> Void = {}
    var onOpenProperty: () -> Void = {}
    var onOpenVehicleValuation: () -> Void = {}
    var onOpenInsurance: () -> Void = {}
    var onOpenBills: () -> Void = {}
    var onOpenRewards: () -> Void = {}
    @State private var locale: AppLocale = loadStoredLocale()
    @State private var overview: OverviewResponse?
    @State private var linkedAccounts: [LinkedAccountDto] = []
    @State private var error: String?
    @State private var busy = false
    @State private var showLinkForm = false
    @State private var provider = ""
    @State private var accountNumber = ""
    @State private var myPhoneNumber = ""
    @State private var activeTab: AssetTab = .accounts

    private func t(_ key: String) -> String {
        overviewStrings[locale]?[key] ?? overviewStrings[.en]?[key] ?? key
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text(t("title")).font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let overview {
                        // Real "My assets" tab-by-tab redesign (2026-08-27, direct user
                        // reference: 3 real Toss "총자산" screenshots) -- deliberately
                        // reverses the 2026-08-24 flat-design sweep's Card removal for
                        // THIS screen only, since the user's own literal pixel reference
                        // is card-based (confirmed with them before starting).
                        VStack(alignment: .leading, spacing: 4) {
                            Text(t("netWorth")).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(Int(overview.netWorth)) RWF").font(.title).bold()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 20) {
                                ForEach(AssetTab.allCases, id: \.self) { tab in
                                    AssetTabLabel(title: assetTabTitle(tab), selected: activeTab == tab) { activeTab = tab }
                                }
                            }
                        }

                        assetTabContent(overview)

                        Divider().overlay(IDS.Colors.divider)

                        VStack(alignment: .leading, spacing: 8) {
                            Text(t("linkedAccounts")).bold()
                            ForEach(linkedAccounts) { account in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(account.provider).bold()
                                    Text("\(account.externalAccountNumberMasked) · \(account.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    if let demoBalance = account.demoBalance {
                                        Text(String(format: t("demoBalance"), account.demoBalanceCurrency ?? "", Int(demoBalance))).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    if account.status == "LINKED" {
                                        Button(action: { Task { await unlink(account.id) } }) {
                                            Text(t("unlink")).font(.caption).bold()
                                        }
                                        .disabled(busy)
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                            if !showLinkForm {
                                Button(action: { showLinkForm = true }) {
                                    Text(t("linkPrompt")).bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                                }
                            } else {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        ForEach(linkProviders, id: \.self) { p in
                                            Button(p) {
                                                provider = p
                                                // Real friction fix (2026-08-10) -- pre-fill with the
                                                // caller's own already-known phone number for a MoMo
                                                // provider, still editable in case they want to link a
                                                // different number. Left blank for a real bank, where
                                                // the account number is genuinely a different value.
                                                if momoLinkProviders.contains(p) && accountNumber.isEmpty {
                                                    accountNumber = myPhoneNumber
                                                }
                                            }.font(.caption).bold()
                                        }
                                    }
                                    IdsTextField(t("providerNamePlaceholder"), text: $provider)
                                    IdsTextField(t("accountPhonePlaceholder"), text: $accountNumber)
                                    Button(action: { Task { await link() } }) {
                                        Text(busy ? t("linking") : t("linkAccount")).bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busy || provider.isEmpty || accountNumber.isEmpty)
                                }
                            }
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
        .task {
            // Non-critical -- the pre-fill just won't happen; typing it manually still works.
            myPhoneNumber = (try? await NetworkClient.shared.getProfile().user.phoneNumber) ?? ""
        }
    }

    private func refresh() async {
        do {
            overview = try await NetworkClient.shared.getOverview()
            linkedAccounts = try await NetworkClient.shared.getLinkedAccounts().linkedAccounts
            error = nil
        } catch { self.error = t("loadError") }
    }

    private func link() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            let linked = try await NetworkClient.shared.linkAccount(provider: provider, externalAccountNumber: accountNumber).linkedAccount
            let submittedProvider = provider
            provider = ""; accountNumber = ""; showLinkForm = false
            await refresh()
            // Real gap found via Toss Simplicity21 research (2026-08-08): a declined
            // provider verification is still a 200 response (the account is saved as
            // VERIFICATION_FAILED so it shows up in the list above) -- without this check
            // the form just closed as if the link had worked.
            if linked.status == "VERIFICATION_FAILED" {
                self.error = linked.failureReason ?? String(format: t("verificationFailed"), submittedProvider)
            }
        } catch { self.error = t("linkError") }
    }

    private func unlink(_ accountId: String) async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.unlinkAccount(accountId: accountId)
            await refresh()
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            await refresh()
        } catch { self.error = t("unlinkError") }
    }

    private func assetTabTitle(_ tab: AssetTab) -> String {
        switch tab {
        case .accounts: return t("accounts")
        case .cards: return t("tabCards")
        case .loans: return t("tabLoans")
        case .investment: return t("tabInvestment")
        case .insurance: return t("tabInsurance")
        case .realEstate: return t("tabRealEstate")
        case .car: return t("tabCar")
        case .tax: return t("tabTax")
        case .points: return t("tabPoints")
        }
    }

    @ViewBuilder
    private func assetTabContent(_ overview: OverviewResponse) -> some View {
        switch activeTab {
        case .accounts:
            VStack(alignment: .leading, spacing: 6) {
                ForEach(overview.accounts) { a in
                    HStack { Text("\(a.name) (\(a.type))"); Spacer(); Text("\(a.currency) \(Int(a.balance))") }.font(.subheadline)
                }
            }
        case .cards:
            if overview.cards.hasCard {
                AssetSummaryCard(
                    title: String(format: t("cardNumber"), overview.cards.last4 ?? ""),
                    value: (overview.cards.frozen ?? false) ? t("cardFrozen") : t("cardActive"),
                    ctaLabel: t("manage"), onCta: onOpenCard,
                )
            } else {
                AssetTeaserCard(maskedValue: "••••", message: t("teaserCards"), ctaLabel: t("teaserCardsCta"), onCta: onOpenCard)
            }
        case .loans:
            if overview.loans.activeCount > 0 {
                AssetSummaryCard(
                    title: t("tabLoans"),
                    value: String(format: t("loans"), Int(overview.loans.totalOutstanding), overview.loans.activeCount),
                    ctaLabel: t("manage"), onCta: onOpenLoans,
                )
            } else {
                AssetTeaserCard(maskedValue: "₩???", message: t("teaserLoans"), ctaLabel: t("teaserLoansCta"), onCta: onOpenLoans)
            }
        case .investment:
            if overview.investments.holdingCount > 0 {
                AssetSummaryCard(
                    title: t("tabInvestment"),
                    value: String(format: t("investments"), Int(overview.investments.totalCostBasis), overview.investments.holdingCount),
                    ctaLabel: t("manage"), onCta: onOpenInvest,
                )
            } else {
                AssetTeaserCard(maskedValue: "??%", message: t("teaserInvestment"), ctaLabel: t("teaserInvestmentCta"), onCta: onOpenInvest)
            }
        case .insurance:
            if overview.insurance.activePolicyCount > 0 {
                AssetSummaryCard(
                    title: t("tabInsurance"),
                    value: String(format: t("insurance"), overview.insurance.activePolicyCount, Int(overview.insurance.totalMonthlyPremium)),
                    ctaLabel: t("manage"), onCta: onOpenInsurance,
                )
            } else {
                AssetTeaserCard(maskedValue: "???", message: t("teaserInsurance"), ctaLabel: t("teaserInsuranceCta"), onCta: onOpenInsurance)
            }
        case .realEstate:
            // Real estate has no home-valuation/ownership-tracking backend feature at
            // all (the realestate module is a marketplace listing flow, not a "track
            // your own home" asset feature) -- always a teaser, matches Toss's own
            // screenshot showing this tab in teaser state too.
            AssetTeaserCard(maskedValue: "₩???", message: t("teaserRealEstate"), ctaLabel: t("teaserRealEstateCta"), onCta: onOpenProperty)
        case .car:
            if overview.vehicles.vehicleCount > 0 {
                AssetSummaryCard(
                    title: t("tabCar"),
                    value: String(format: t("carSummary"), overview.vehicles.vehicleCount, Int(overview.vehicles.totalPurchasePrice)),
                    ctaLabel: t("manage"), onCta: onOpenVehicleValuation,
                )
            } else {
                AssetTeaserCard(maskedValue: "₩???", message: t("teaserCar"), ctaLabel: t("teaserCarCta"), onCta: onOpenVehicleValuation)
            }
        case .tax:
            // Always a real card, even at zero payments -- matches Toss's own
            // always-populated Tax tab (no "link a tax account" step exists; paying a
            // real RRA bill through Bills IS the real activity this reflects).
            AssetSummaryCard(
                title: t("tabTax"),
                value: String(format: t("taxSummary"), overview.tax.paymentCount, Int(overview.tax.totalPaid)),
                ctaLabel: t("manage"), onCta: onOpenBills,
            )
        case .points:
            VStack(alignment: .leading, spacing: 6) {
                Text(String(format: t("pointsSummary"), Int(overview.points.rewardsTotal))).font(.subheadline)
                Text(String(format: t("payMoneyBalance"), Int(overview.points.payMoneyBalance))).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                Button(action: onOpenRewards) { Text(t("manage")).font(.caption).bold() }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(IDS.Colors.card)
            .cornerRadius(12)
            .idsCardBorder(cornerRadius: 12)
        }
    }
}

private enum LoansMode: String, CaseIterable { case offers = "Offers", myLoans = "My loans", overdraft = "Overdraft", postpaidCredit = "Postpaid credit" }

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
    // Real Toss writing-principle adoption ("숨은 감정 찾기" -- find the hidden emotion):
    // toss.tech/article/8-writing-principles-of-toss names a fully-repaid loan as their
    // own example of a moment that deserves more than transactional silence. Paying off
    // a loan just refreshed the list silently before this, even though the repay
    // response already tells us `remaining` hit zero.
    @State private var payoffMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
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
                    if let payoffMessage { Text(payoffMessage).font(.subheadline).bold().foregroundColor(IDS.Colors.brand) }
                    if let refinanceResult {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Refinanced into \(refinanceResult.newLoanName)").font(.subheadline).bold()
                            Text("\(refinanceResult.oldInterestRate, specifier: "%.1f")% → \(refinanceResult.newInterestRate, specifier: "%.1f")%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                        // a lone info banner (docs/UI_UX_GUIDELINES.md §10).
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
                            EmptyStateView("No offers from this lender right now.")
                            ForEach(offers) { offer in LoanOfferCard(offer: offer, busy: busyId == offer.id, onApply: { amount in Task { await apply(offer, amount) } }) }
                        } else { ProgressView() }
                    } else if mode == .myLoans {
                        if let myLoans {
                            if myLoans.isEmpty { Text("You have no loans yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                            // a status/history log of active loans, kept the per-row Divider
                            // convention (docs/DESIGN_REFERENCES.md §274).
                            ForEach(myLoans) { loan in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("\(Int(loan.principal)) RWF loan").bold()
                                    Text("Outstanding: \(Int(loan.outstanding)) RWF").font(.subheadline)
                                    Text("Status: \(loan.status) · \(loan.interestRate)%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    if loan.status == "ACTIVE" {
                                        IdsTextField("Repay amount (RWF)", text: Binding(
                                            get: { repayAmounts[loan.id] ?? "" },
                                            set: { repayAmounts[loan.id] = $0 }
                                        ), keyboardType: .numberPad)
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
                                .padding(.vertical, 10)
                                Divider().overlay(IDS.Colors.divider)
                            }
                        } else { ProgressView() }
                    } else if mode == .overdraft {
                        OverdraftPanel()
                    } else {
                        PostpaidCreditPanel()
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
            let result = try await NetworkClient.shared.repayLoan(loanId: loan.id, amount: amount)
            repayAmounts[loan.id] = nil
            payoffMessage = result.remaining <= 0 ? "You paid off this loan in full — one less thing to carry." : nil
            await refresh()
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "LOAN_ALREADY_PAID" {
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

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a browsable
// catalog list of loan offers, matching Android's/web's identical no-divider
// treatment for entity/product lists (docs/UI_UX_GUIDELINES.md §10).
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
        if busy {
            LoanApplyProgress()
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Text(offer.name).bold()
                Text(offer.lenderName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                Text("Up to \(Int(offer.maxAmount)) RWF · \(offer.interestRate)% · \(offer.term)").font(.subheadline)
                Text(offer.requirements).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                IdsTextField("Amount (RWF)", text: $amountText, keyboardType: .numberPad)
                Button(action: { if let n = Double(amountText), n > 0 { onApply(n) } }) {
                    Text("Apply").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                }
            }
            .padding(.vertical, 10)
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
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
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
                        // Real fix (2026-08-24, flat-design sweep): dropped this Card wrapper
                        // and VerifyCertificateCard's own -- 2 real sections shown together on
                        // this screen, so a Divider marks the boundary between them instead
                        // (docs/UI_UX_GUIDELINES.md §10). The orange private-key warning box
                        // below is deliberately left as-is -- a distinct color treatment, not
                        // itunda-card, signaling a one-time, high-stakes notice.
                        .padding(.vertical, 10)

                        if let issuedPrivateKey {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Save this private key now — you won't be able to see it again.").font(.subheadline).bold().foregroundColor(.orange)
                                Text(issuedPrivateKey).font(.system(.caption, design: .monospaced))
                            }
                            .padding(16).background(Color.orange.opacity(0.1)).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                        if let error { Text(error).font(.caption).foregroundColor(.red) }

                        Divider().overlay(IDS.Colors.divider)
                        VerifyCertificateCard()
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

// Real public certificate status/verify (2026-08-04) -- see NetworkClient.swift's own
// doc comment on getCertificateStatus/verifyCertificateSignature: the two endpoints
// that answer "does this signed thing check out," found via a fresh backend-endpoint
// sweep with zero client anywhere. Deliberately separate from CertificateScreenView
// above -- that one manages the caller's own certificate; this one checks someone
// else's.
private struct VerifyCertificateCard: View {
    @State private var serialNumber = ""
    @State private var payload = ""
    @State private var signature = ""
    @State private var statusResult: CertificateDto?
    @State private var verifyResult: VerifyCertificateSignatureResponse?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Verify a certificate").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("Check whether a certificate serial number is still active, or verify a document someone signed with theirs.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)

            IdsTextField("Serial number", text: $serialNumber)
                .onChange(of: serialNumber) { _ in statusResult = nil; verifyResult = nil }
            Button(action: { Task { await checkStatus() } }) {
                Text(busy ? "Checking…" : "Check status").bold().frame(maxWidth: .infinity).padding(10)
                    .background(IDS.Colors.chipBackground).cornerRadius(8)
            }
            .disabled(busy || serialNumber.isEmpty)

            if let statusResult {
                Text("Status: \(statusResult.status) · Expires \(statusResult.expiresAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }

            Text("Verify a signature").font(.footnote).bold().foregroundColor(IDS.Colors.textPrimary).padding(.top, 6)
            IdsTextField("Payload (the exact text they signed)", text: $payload)
                .onChange(of: payload) { _ in verifyResult = nil }
            IdsTextField("Signature (base64)", text: $signature)
                .onChange(of: signature) { _ in verifyResult = nil }
            Button(action: { Task { await verify() } }) {
                Text(busy ? "Verifying…" : "Verify signature").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(busy || serialNumber.isEmpty || payload.isEmpty || signature.isEmpty)

            if let verifyResult {
                Text(verifyResult.signatureValid ? "✓ Signature is valid" : "✗ Signature does not match")
                    .bold().foregroundColor(verifyResult.signatureValid ? .green : .red)
                Text("Certificate status: \(verifyResult.certificateStatus)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
        }
        .padding(.vertical, 10)
    }

    private func checkStatus() async {
        busy = true; error = nil; verifyResult = nil
        defer { busy = false }
        do {
            statusResult = try await NetworkClient.shared.getCertificateStatus(serialNumber: serialNumber).certificate
        } catch {
            statusResult = nil
            self.error = "No certificate found with that serial number."
        }
    }

    private func verify() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            verifyResult = try await NetworkClient.shared.verifyCertificateSignature(serialNumber: serialNumber, payload: payload, signature: signature)
        } catch {
            verifyResult = nil
            self.error = "Could not verify this signature."
        }
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
    // Real, sourced Toss simplification (2026-08-24, toss.tech/article/signup: adding
    // WHY a personal-info request exists, not removing fields, is what measurably cut
    // Toss's own signup drop-off). bank-mfe's BankDashboard.tsx IdentityView already
    // got this exact fix (2026-08-19, a different sourced Toss article), Android got
    // it the same pass as this iOS fix -- reuses the same real live number both do
    // (CreditScoreService.KYC_VERIFIED_POINTS via the already-shipped
    // getCreditScoreSuggestions endpoint), not a hardcoded points value.
    @State private var kycPointsGain: Int?

    private var hasPending: Bool { submissions?.contains { $0.status == "PENDING" } ?? false }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
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
                        // Real fix (2026-08-24, flat-design sweep): dropped both Card wrappers
                        // in this if/else (mutually exclusive, no divider between them), and
                        // the per-row Card below (kept the per-row Divider -- a real status log
                        // of past submissions) (docs/UI_UX_GUIDELINES.md §10 /
                        // docs/DESIGN_REFERENCES.md §274).
                        .padding(.vertical, 10)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(kycPointsGain.map { "A quick, one-time check that confirms it's really you -- it protects your account from takeover, and raises your Credit Score by \($0) points once approved." }
                                ?? "A quick, one-time check that confirms it's really you -- it protects your account from takeover.")
                                .font(.caption)
                                .foregroundColor(IDS.Colors.textSecondary)
                            HStack {
                                ForEach(identityDocumentTypes, id: \.self) { t in
                                    Button(t) { documentType = t }.font(.caption).bold()
                                        .foregroundColor(documentType == t ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(documentType == t ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(999)
                                }
                            }
                            IdsTextField("Document number", text: $documentNumber)
                            IdsTextField("Document reference (scan/photo reference)", text: $documentReference)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit for review").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || documentNumber.isEmpty || documentReference.isEmpty)
                        }
                        .padding(.vertical, 10)
                    }

                    Divider().overlay(IDS.Colors.divider)
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
                            .padding(.vertical, 10)
                            Divider().overlay(IDS.Colors.divider)
                        }
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
        .task { await loadKycPointsGain() }
    }

    private func refresh() async {
        do { submissions = try await NetworkClient.shared.getIdentityStatus().submissions; error = nil }
        catch { self.error = "Could not load your identity status." }
    }

    private func loadKycPointsGain() async {
        // Non-critical -- the explanatory line just falls back to the generic wording
        // above when this call fails.
        kycPointsGain = try? await NetworkClient.shared.getCreditScoreSuggestions().suggestions
            .first { $0.action == "Verify your identity" }?.pointsGain
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
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
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
                            IdsTextField("Describe the issue", text: $descriptionText)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit ticket").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || selectedTransactionId == nil || descriptionText.isEmpty)
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped this Card and the
                        // per-row Card below (kept its per-row Divider -- a real status log of
                        // past tickets), same pattern as IdentityScreenView above
                        // (docs/UI_UX_GUIDELINES.md §10 / docs/DESIGN_REFERENCES.md §274).
                        .padding(.vertical, 10)
                    }

                    Divider().overlay(IDS.Colors.divider)
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
                            .padding(.vertical, 10)
                            Divider().overlay(IDS.Colors.divider)
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
