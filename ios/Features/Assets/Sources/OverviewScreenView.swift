import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss-style unified account overview (2026-07-22 port, see
// docs/TOSS_PARITY_MATRIX.md's own per-row notes) -- extracted out of App/Sources'
// OverviewLoansCreditScoreScreens.swift (2026-08-30) into FeatureAssets, mirroring
// CreditScoreScreenView's own move into FeatureCredit. See
// project_itunda_feature_isolation's own memory for the extraction order/rationale.

private let linkProviders = ["MTN Mobile Money", "Airtel Money", "Bank of Kigali", "Equity Bank Rwanda"]
// Real friction point found live via Toss Simplicity21 research (2026-08-08, session 2-1
// "신은 디테일에 있다" -- eliminating friction from a real bank-linking flow): for a MoMo
// provider, the "account number" IS the caller's own real phone number -- the same number
// they're already logged in with. Making them retype it is unnecessary friction with a
// real, already-known answer, the exact shape that session's own title names.
private let momoLinkProviders: Set<String> = ["MTN Mobile Money", "Airtel Money"]

// Real second iOS screen localized (2026-08-08), following web/Android's own identical
// "phase content outward from login into account overview" step (docs/DESIGN_REFERENCES.md
// Section 19). Reuses AppLocale/loadStoredLocale, promoted into CoreDesignSystem's own
// AppLocale.swift (2026-08-30) specifically so this Feature module could reuse it too.
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

public struct OverviewScreenView: View {
    public var onBack: () -> Void
    public var onOpenCard: () -> Void
    public var onOpenLoans: () -> Void
    public var onOpenInvest: () -> Void
    public var onOpenProperty: () -> Void
    public var onOpenVehicleValuation: () -> Void
    public var onOpenInsurance: () -> Void
    public var onOpenBills: () -> Void
    public var onOpenRewards: () -> Void

    public init(
        onBack: @escaping () -> Void = {},
        onOpenCard: @escaping () -> Void = {},
        onOpenLoans: @escaping () -> Void = {},
        onOpenInvest: @escaping () -> Void = {},
        onOpenProperty: @escaping () -> Void = {},
        onOpenVehicleValuation: @escaping () -> Void = {},
        onOpenInsurance: @escaping () -> Void = {},
        onOpenBills: @escaping () -> Void = {},
        onOpenRewards: @escaping () -> Void = {}
    ) {
        self.onBack = onBack
        self.onOpenCard = onOpenCard
        self.onOpenLoans = onOpenLoans
        self.onOpenInvest = onOpenInvest
        self.onOpenProperty = onOpenProperty
        self.onOpenVehicleValuation = onOpenVehicleValuation
        self.onOpenInsurance = onOpenInsurance
        self.onOpenBills = onOpenBills
        self.onOpenRewards = onOpenRewards
    }

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

    public var body: some View {
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
