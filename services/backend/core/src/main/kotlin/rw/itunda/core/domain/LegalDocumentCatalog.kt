package rw.itunda.core.domain

/**
 * Real itunda-branded legal document bodies -- closes a real, live gap found
 * 2026-08-30 during a market-readiness audit: Android's own SettingsScreen.kt has
 * carried a "Legal Documents" card (Credit data policy / Privacy policy / Terms &
 * Consent) since 2026-08-12, but every row was deliberately built INERT (plain
 * labels, no `onClick`, no chevron) because "itunda has no Credit-data-usage/
 * Privacy-policy/Terms document screens to link to yet" -- that file's own honest
 * comment. iOS and bank-mfe never even had the section. This is the real content
 * those rows link to now.
 *
 * Deliberately itunda's own document, under itunda's own name and visual identity --
 * not a template of BNR's or any other regulator's actual license/registration
 * paperwork, and not a replica of any government-issued identity document. Section 1
 * of the Terms of Service says plainly what itunda actually is: a research and
 * product-demonstration platform built to explore what a Rwanda-market super-app
 * would look like, run against real infrastructure itunda itself operates (see
 * docs/TOSS_PARITY_MATRIX.md for the maintained real/simulated boundary on every
 * feature) but NOT a BNR-licensed deposit-taking bank. That honesty is the whole
 * point of these documents existing at all -- a real bank's terms would describe a
 * real license; itunda's terms describe what itunda actually, honestly is.
 *
 * Structured to match the real, standard shape of Korean fintech terms (Toss/Kakao
 * Bank's own published documents follow this same numbered-section convention) and
 * Rwanda's own real Law N° 058/2021 of 13/10/2021 Relating to the Protection of
 * Personal Data and Privacy (Rwanda's actual, currently-in-force data protection
 * law, publicly published by the Ministry of ICT and Innovation) for the Privacy
 * Policy's data-subject-rights section specifically -- real legal citations, not
 * invented ones, describing itunda's own real practices against that real law's
 * real requirements.
 *
 * Distinct from `TermsCatalog` (`terms_of_service`/`privacy_policy`/
 * `marketing_communications`), which drives the REQUIRED registration-consent gate
 * (`AuthService.register`) -- kept deliberately separate so this addition never
 * touches the registration flow fixed the same day (native `acceptedTermsIds`
 * wiring). This catalog is read-only reference material, reached from Settings, not
 * a consent gate; `bodyMarkdown` is intentionally the fuller version of the same two
 * documents `TermsCatalog` already summarizes for the signup checklist, plus one
 * document (`credit_data_policy`) that has no registration-time consent step at all
 * (Rwanda has no equivalent-to-Korea's 신용정보법 mandatory upfront credit-bureau
 * consent requirement, so this is honestly informational, not a checkbox).
 */
data class LegalDocument(val id: String, val title: String, val version: String, val bodyMarkdown: String)

object LegalDocumentCatalog {
    val documents = listOf(
        LegalDocument(
            id = "terms_of_service",
            title = "Terms of Service",
            version = "2026-08-30",
            bodyMarkdown = TERMS_OF_SERVICE,
        ),
        LegalDocument(
            id = "privacy_policy",
            title = "Privacy Policy",
            version = "2026-08-30",
            bodyMarkdown = PRIVACY_POLICY,
        ),
        LegalDocument(
            id = "credit_data_policy",
            title = "Credit Data Policy",
            version = "2026-08-30",
            bodyMarkdown = CREDIT_DATA_POLICY,
        ),
    )

    fun findById(id: String): LegalDocument? = documents.find { it.id == id }
}

private val TERMS_OF_SERVICE = """
1. About itunda

itunda is a research and product-demonstration platform. It is built and operated by itunda to explore, in working software rather than slides, what a Rwanda-market "super app" -- covering everyday banking, payments, shopping, food delivery, rides, and community features in one place -- could look like if it were built with the same engineering discipline as real products like Toss, Kakao Bank, Naver, and Coupang.

itunda is not a bank, microfinance institution, or payment service provider licensed by the National Bank of Rwanda (BNR), and nothing in these Terms or in the app should be read as claiming otherwise. Some features run against real infrastructure that itunda itself builds and operates -- real account ledgers, real cryptographic signing, real KYC review workflows -- while others simulate an external counterparty (a bank rail, a mobile money network, a government identity registry) that itunda has no license or commercial relationship to connect to for real. itunda maintains a public, continuously updated record of exactly which is which; ask itunda's support channel (Section 12) if a specific feature's status isn't clear to you.

By creating an itunda account, you acknowledge and accept this: you are using a research platform, not a licensed financial institution, and you should not treat balances, transfers, or other activity inside itunda as equivalent to holding funds at a BNR-licensed bank.

2. Eligibility

You must be at least 18 years old, or the age of majority in your jurisdiction if higher, and able to form a binding contract to create an itunda account. You must provide accurate registration information and keep it up to date. One person may hold one personal itunda account; using another person's identity, or creating multiple accounts to circumvent a limit or restriction, is not allowed.

3. Your Account

You are responsible for the security of your PIN, your device, and any biometric credential you register with itunda. itunda will never ask you to share your PIN, a one-time code, or your password with anyone, including someone claiming to be from itunda support. Tell itunda immediately, through Settings or support, if you believe your account or device has been compromised, or if you lose access to your registered phone number.

itunda may ask you to verify your identity (Section 6 and itunda's separate Credit Data Policy cover how identity and credit information are used) before enabling certain features, consistent with standard know-your-customer practice for financial-adjacent products.

4. Using itunda's Services

itunda's features -- Bank, Pay, Shopping, Eats, Rides, Hood (neighborhood/community), and others -- are itunda's own products, built and operated by itunda. Where a feature connects to a real third party (for example, a real licensed lender listed in itunda's loan marketplace, or a real delivery partner), itunda identifies that third party honestly rather than implying itunda itself holds their license or role.

You agree to use itunda's services only for lawful purposes, and not to use them to launder money, finance illegal activity, defraud another user or merchant, or circumvent any limit, verification step, or security control itunda puts in place.

5. Fees

Where itunda charges a fee for a service (for example, a transfer fee, a marketplace commission, or a subscription), the fee is disclosed to you before you confirm the action it applies to. itunda does not change a disclosed fee after you've confirmed it for that specific transaction.

6. Identity Verification (KYC)

itunda operates a real identity-verification workflow: you submit a document (a National ID number or passport number), an automated structural check runs against the real, publicly documented format for that document type, and a human reviewer makes the final decision. itunda does not claim to verify your identity against Rwanda's National Identification Agency's actual government database -- that automated pre-check is honestly a structural/format validation plus a simulated registry lookup, not a live government lookup, and itunda tells you exactly that inside the app. Certain features (for example, issuing an itunda Certificate, or higher transaction limits) require this verification to be complete and approved.

7. Your Responsibilities

You agree to: give itunda accurate information; keep your credentials secure; use itunda's services in a way that doesn't harm itunda, other users, or third parties; and tell itunda promptly if something in the app is clearly wrong (a balance that looks incorrect, a transaction you didn't make, a security issue) rather than exploiting it.

8. itunda's Responsibilities

itunda will operate its real infrastructure (account ledgers, transfers between itunda accounts, identity review, certificate issuance, and the rest) in good faith and disclose honestly, inside the app and in its public documentation, which features are real end-to-end and which simulate an external party itunda has no license to connect to for real. itunda will tell you about a material change to these Terms before it takes effect (Section 10).

9. Suspension and Termination

itunda may suspend or close an account that violates these Terms, that itunda reasonably believes is being used fraudulently, or where itunda is required to act by law. You may close your account at any time through Settings or by contacting support; itunda will handle any remaining balance according to the process described in the app at the time.

10. Changes to These Terms

itunda may update these Terms as the platform evolves. If a change is material, itunda will tell you before it takes effect -- inside the app, or through the contact details on file for your account -- and, where required by law, ask for your renewed consent.

11. Governing Law and Disputes

These Terms are governed by the laws of the Republic of Rwanda. Any dispute that cannot be resolved directly with itunda's support team is subject to the jurisdiction of the competent courts of Rwanda, seated in Kigali, unless mandatory local consumer-protection law gives you a different venue.

12. Contact

Questions about these Terms, or about what "real" versus "simulated" means for a specific feature, can be sent through the in-app support flow or to itunda's listed contact address in Settings.
""".trimIndent()

private val PRIVACY_POLICY = """
1. Who We Are

This Privacy Policy is issued by itunda ("itunda", "we", "us") and explains how itunda collects, uses, shares, and protects your personal data when you use itunda's apps and services. itunda is the data controller for the personal data described in this Policy.

2. The Law This Policy Follows

This Policy is written to meet Rwanda's Law N° 058/2021 of 13/10/2021 Relating to the Protection of Personal Data and Privacy, and describes itunda's real, current data practices against that law's real requirements -- not a generic template.

3. What We Collect

Identity information: your name, date of birth, phone number, and (if you complete identity verification) your National ID number or passport number and the document type you submitted.

Account and financial information: your itunda account balance and transaction history, transfers you make and receive, loan applications, savings and investment activity, and merchant orders you place.

Location information: a coarse, reverse-geocoded neighborhood derived from a location you share (for hyperlocal features like Hood and nearby merchants) -- itunda stores the resulting neighborhood name, not a continuous location trail.

Device and security information: your device identifier, device name, a public key if you enable passwordless biometric login, and app-lock/security-setting state, used to secure your account and detect suspicious activity.

Communications: messages you send through itunda Talk, support conversations, and community posts/reviews you choose to publish.

4. Why We Collect It, and Our Legal Basis

To provide the service you asked for (contract performance) -- creating your account, processing a transfer, fulfilling an order.

To meet a legal obligation -- identity verification and transaction record-keeping consistent with standard know-your-customer and anti-money-laundering practice.

With your consent -- optional items such as marketing communications, which you can withdraw at any time in Settings without affecting your ability to use itunda's core services.

For itunda's legitimate interests -- fraud prevention, security monitoring, and improving the reliability of itunda's own services, balanced against your right to privacy.

5. Who We Share It With

itunda does not sell your personal data. itunda shares data only: with a real named third party you've asked itunda to interact with (for example, a real licensed lender in itunda's loan marketplace, shown to you by name before you apply); with a service provider itunda uses to operate its own infrastructure, bound by confidentiality obligations; or when required by a valid legal order from a competent Rwandan authority.

6. Data Retention

itunda keeps your account and transaction data for as long as your account is active and for a reasonable period after closure to meet legal record-keeping obligations and resolve any outstanding dispute. Identity-verification documents are kept only as long as needed for the purpose they were collected for, consistent with Section 3 above.

7. Your Rights

Under Law N° 058/2021, you have the right to: access the personal data itunda holds about you; ask itunda to correct inaccurate data; ask itunda to delete your data where the law allows; object to or restrict certain processing; and withdraw consent for anything itunda processes on the basis of your consent (for example, marketing communications), at any time, through Settings or by contacting support. itunda will respond to a request within a reasonable time and consistent with the law's requirements.

8. Security Measures

itunda stores your password/PIN as a real one-way hash, never in plain text; supports real hardware-backed biometric login on devices that offer it; issues its own real cryptographically signed identity certificate (see itunda's Certificate feature) rather than storing a biometric template; and applies device-verification step-up checks to sensitive account actions.

9. Children's Privacy

itunda's services are for people who meet the eligibility age in Section 2 of the Terms of Service. itunda does not knowingly collect personal data from children below that age.

10. Changes to This Policy

If itunda materially changes how it collects or uses your data, itunda will tell you before the change takes effect and, where the law requires it, ask for your renewed consent.

11. Contact

To exercise any right in Section 7, or to ask a question about this Policy, contact itunda through the in-app support flow or the contact details listed in Settings.
""".trimIndent()

private val CREDIT_DATA_POLICY = """
1. What This Policy Covers

This Policy explains how itunda uses your financial activity inside itunda to compute an itunda credit score, and how that score is used within itunda's own products (for example, itunda's loan-offer eligibility). This is separate from, and does not affect, the required Terms of Service and Privacy Policy consent you gave when you registered.

2. What Feeds Into Your itunda Score

Your itunda credit score is computed from your real, observable activity inside itunda: account tenure, transaction and repayment history on itunda's own products (loans, overdraft, bills), and savings activity. It does not use, and itunda has no access to, any external credit bureau record.

3. What Your itunda Score Is Not

itunda's credit score is itunda's own simple underwriting model, built for itunda's own loan marketplace -- it is not a bureau-grade credit score, is not shared with or recognized by any external lender or credit bureau outside itunda, and has no legal standing outside itunda's own products.

4. How It's Used

Your itunda score determines which of itunda's own loan offers and partner-lender offers (see itunda's Terms of Service, Section 4, on how itunda identifies real third parties) you're eligible to see, and the concurrent-loan and amount limits described in-app at the time you apply. A lower score does not close your itunda account or affect any other itunda feature.

5. Your Rights

You can see your current itunda score and a summary of the activity behind it inside the app at any time. If you believe your score reflects an error (for example, a transaction that didn't actually happen), contact support so itunda can review it -- itunda does not use fully automated scoring decisions without a way for you to ask a human to look at the result.

6. Data Sharing

Your itunda score itself is not shared outside itunda except with a real partner lender you've actively chosen to apply to through itunda's marketplace, and only for the purpose of that application.
""".trimIndent()
