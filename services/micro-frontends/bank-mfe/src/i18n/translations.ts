// Real first slice of Kinyarwanda/English localization (2026-08-08) -- found missing
// entirely via Toss ecosystem research comparing itunda against Paytm/PhonePe's own
// real regional-language UX (>50% of new fintech users in a comparable market prefer
// their own language over English, per docs/DESIGN_REFERENCES.md Section 19). Before
// this, every string in every itunda client was hardcoded English -- a real gap given
// itunda's own explicit financial-inclusion mission in a market where English is
// Rwanda's third official language, not its most widely spoken one.
//
// Deliberately small and honest about its own limits, matching this codebase's
// established "real but honestly scoped" discipline (see e.g. DemoExternalBalanceService's
// own doc comment): this is infrastructure plus ONE real screen (login, itunda's own
// single highest-traffic screen per LoginPage.tsx's existing comment), not a claim that
// the whole app is translated. The `rw` strings below are a good-faith, careful
// translation -- NOT verified by a native Kinyarwanda speaker -- and should get real
// native-speaker review before being treated as production-final, the same honesty
// standard this codebase already applies to anything it can't fully verify itself.

export type Locale = 'en' | 'rw';

export const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Ikinyarwanda' },
];

export const DEFAULT_LOCALE: Locale = 'en';

export type TranslationKey =
  | 'login.tagline'
  | 'login.phoneNumber'
  | 'login.password'
  | 'login.showPassword'
  | 'login.hidePassword'
  | 'login.signingIn'
  | 'login.signIn'
  | 'login.createAccount'
  | 'login.connectionError'
  // Real second slice (2026-08-08): wallet overview, itunda's own second-highest-traffic
  // screen (the one every user lands on right after login) -- following the exact phased
  // rollout named in docs/DESIGN_REFERENCES.md Section 19's own "real next steps" list.
  // Several of these carry a dynamic amount/count, hence the `{{placeholder}}` support
  // added to useI18n's `t()` alongside this -- a real, common minimal-i18n pattern
  // (named placeholders a translator can reorder per-language), not string concatenation
  // that would lock every locale into English word order.
  | 'overview.netWorth'
  | 'overview.accounts'
  | 'overview.savings'
  | 'overview.loans'
  | 'overview.investments'
  | 'overview.insurance'
  | 'overview.linkedAccounts'
  | 'overview.demoBalance'
  | 'overview.unlink'
  | 'overview.linkAccountPrompt'
  | 'overview.providerNamePlaceholder'
  | 'overview.accountPhonePlaceholder'
  | 'overview.linking'
  | 'overview.linkAccount'
  | 'overview.loadError'
  | 'overview.linkError'
  | 'overview.unlinkError'
  // Real gap caught while porting this exact screen to Android (2026-08-08): this
  // specific message -- shown when the backend saves a link attempt as
  // VERIFICATION_FAILED, a real 200 response, not a thrown error (see
  // BankDashboard.tsx's own handleLink comment) -- was missed in this file's own first
  // pass over this screen. Fixed here retroactively, and on Android at the same time,
  // not left inconsistent between the two.
  | 'overview.verificationFailed'
  // Real third slice (2026-08-08): P2P transfer, itunda's own single highest-stakes
  // money-moving screen -- following the same phased rollout, one screen at a time,
  // across all 3 platforms before moving to the next.
  | 'transfer.title'
  | 'transfer.recipientPlaceholder'
  | 'transfer.amountPlaceholder'
  | 'transfer.contactsLabel'
  | 'transfer.addContact'
  | 'transfer.cancel'
  | 'transfer.namePlaceholder'
  | 'transfer.phonePlaceholder'
  | 'transfer.saveContact'
  | 'transfer.noContacts'
  | 'transfer.continue'
  | 'transfer.confirmTitle'
  | 'transfer.toRecipient'
  | 'transfer.amountLine'
  | 'transfer.scamWarningTitle'
  | 'transfer.scamWarningBody'
  | 'transfer.sending'
  | 'transfer.send'
  | 'transfer.newBalance'
  | 'transfer.done'
  | 'transfer.saveContactError'
  | 'transfer.sendError'
  // The real entry point INTO the transfer flow above -- a separate component
  // (AccountBalance, the wallet card), found while verifying the transfer screen live:
  // translating the flow itself but leaving its own trigger button in English would
  // have been the same "translated the destination, not the door" gap as the missing
  // overview.verificationFailed key. "Top up" included even though it's a real,
  // honestly-disabled non-feature (see its own title= tooltip) -- disabled doesn't mean
  // invisible, the label still renders and should still be localized.
  | 'dashboard.mainAccount'
  | 'dashboard.transfer'
  | 'dashboard.topUp'
  // Real 5th-localization-pass additions (2026-08-09) -- finishing HomeView (the
  // actual default landing tab, not OverviewView, which is a deeper tab) rather than
  // starting a whole new screen: found it was only partially localized (just
  // AccountBalance + TransferFlow) while its own load-error message, quick actions,
  // device step-up prompt, and the ReportScamLink component -- explicitly named as a
  // known gap two passes ago -- were all still English.
  | 'home.loadError'
  | 'quickActions.scanToPay'
  | 'quickActions.cards'
  | 'deviceStepUp.title'
  | 'deviceStepUp.body'
  | 'deviceStepUp.passwordPlaceholder'
  | 'deviceStepUp.showPassword'
  | 'deviceStepUp.hidePassword'
  | 'deviceStepUp.cancel'
  | 'deviceStepUp.verifying'
  | 'deviceStepUp.verify'
  | 'deviceStepUp.genericError'
  | 'scamReport.promptQuestion'
  | 'scamReport.thanks'
  | 'scamReport.reporting'
  | 'scamReport.reportLink'
  | 'home.recentActivity'
  | 'home.noTransactions'
  | 'home.unusuallyLarge'
  // Real 6th-localization-pass additions (2026-08-09) -- the remaining HomeView cards
  // named as open follow-ups two passes ago: DiscoverSection, MiniWalletCard,
  // ScheduledTransfersCard, AutoTransfersCard, RequestMoneyCard, AutoTopUpCard.
  | 'discover.title'
  | 'discover.new'
  | 'miniWallet.title'
  | 'miniWallet.cancel'
  | 'miniWallet.addMoney'
  | 'miniWallet.ageIneligible'
  | 'miniWallet.openError'
  | 'miniWallet.birthDateError'
  | 'miniWallet.depositError'
  | 'miniWallet.description'
  | 'miniWallet.opening'
  | 'miniWallet.open'
  | 'miniWallet.birthDatePrompt'
  | 'miniWallet.checking'
  | 'miniWallet.checkEligibility'
  | 'miniWallet.amountPlaceholder'
  | 'miniWallet.adding'
  | 'miniWallet.add'
  | 'scheduledTransfers.title'
  | 'scheduledTransfers.cancel'
  | 'scheduledTransfers.schedule'
  | 'scheduledTransfers.recipientPlaceholder'
  | 'scheduledTransfers.amountPlaceholder'
  | 'scheduledTransfers.descriptionPlaceholder'
  | 'scheduledTransfers.scheduling'
  | 'scheduledTransfers.scheduleButton'
  | 'scheduledTransfers.createError'
  | 'scheduledTransfers.cancelError'
  | 'scheduledTransfers.noTransfers'
  | 'scheduledTransfers.statusScheduled'
  | 'scheduledTransfers.statusSent'
  | 'scheduledTransfers.statusCancelled'
  | 'scheduledTransfers.statusFailed'
  | 'autoTransfers.title'
  | 'autoTransfers.cancel'
  | 'autoTransfers.setUp'
  | 'autoTransfers.recipientPlaceholder'
  | 'autoTransfers.amountPlaceholder'
  | 'autoTransfers.weekly'
  | 'autoTransfers.monthly'
  | 'autoTransfers.dayOfMonth'
  | 'autoTransfers.descriptionPlaceholder'
  | 'autoTransfers.settingUp'
  | 'autoTransfers.setUpButton'
  | 'autoTransfers.createError'
  | 'autoTransfers.toggleError'
  | 'autoTransfers.cancelError'
  | 'autoTransfers.noTransfers'
  | 'autoTransfers.weeklyLabel'
  | 'autoTransfers.monthlyLabel'
  | 'autoTransfers.statusActive'
  | 'autoTransfers.statusPaused'
  | 'autoTransfers.statusCancelled'
  | 'autoTransfers.pause'
  | 'autoTransfers.resume'
  | 'autoTransfers.cancelAction'
  | 'weekday.monday'
  | 'weekday.tuesday'
  | 'weekday.wednesday'
  | 'weekday.thursday'
  | 'weekday.friday'
  | 'weekday.saturday'
  | 'weekday.sunday'
  | 'requestMoney.title'
  | 'requestMoney.cancel'
  | 'requestMoney.newRequest'
  | 'requestMoney.amountPlaceholder'
  | 'requestMoney.whatsItFor'
  | 'requestMoney.creating'
  | 'requestMoney.createButton'
  | 'requestMoney.shareCode'
  | 'requestMoney.payCodePlaceholder'
  | 'requestMoney.paying'
  | 'requestMoney.pay'
  | 'requestMoney.createError'
  | 'requestMoney.payError'
  | 'requestMoney.myRequests'
  | 'requestMoney.statusPending'
  | 'requestMoney.statusPaid'
  | 'requestMoney.statusExpired'
  | 'autoTopUp.title'
  | 'autoTopUp.cancel'
  | 'autoTopUp.edit'
  | 'autoTopUp.setUp'
  | 'autoTopUp.linkFirst'
  | 'autoTopUp.selectAccount'
  | 'autoTopUp.thresholdPlaceholder'
  | 'autoTopUp.topUpPlaceholder'
  | 'autoTopUp.maxPerDay'
  | 'autoTopUp.saving'
  | 'autoTopUp.save'
  | 'autoTopUp.saveError'
  | 'autoTopUp.on'
  | 'autoTopUp.off'
  | 'autoTopUp.summaryLine'
  | 'autoTopUp.upToPerDay'
  | 'autoTopUp.turnOff'
  | 'autoTopUp.turnOn'
  | 'autoTopUp.checking'
  | 'autoTopUp.checkNow'
  | 'autoTopUp.checkError';

export const translations: Record<Locale, Record<TranslationKey, string>> = {
  en: {
    'login.tagline': 'Sign in to your Itunda account.',
    'login.phoneNumber': 'Phone number',
    'login.password': 'Password',
    'login.showPassword': 'Show password',
    'login.hidePassword': 'Hide password',
    'login.signingIn': 'Signing in…',
    'login.signIn': 'Sign in',
    'login.createAccount': 'New to itunda? Create an account',
    'login.connectionError': "Can't connect right now. Please try again in a moment.",
    'overview.netWorth': 'Net worth',
    'overview.accounts': 'Accounts',
    'overview.savings': 'Savings: {{amount}} RWF across {{count}} goal(s)',
    'overview.loans': 'Loans: {{amount}} RWF outstanding, {{count}} active',
    'overview.investments': 'Investments: {{amount}} RWF cost basis, {{count}} holding(s)',
    'overview.insurance': 'Insurance: {{count}} active plan(s), {{amount}} RWF/month',
    'overview.linkedAccounts': 'Linked accounts',
    'overview.demoBalance': 'Demo balance: {{currency}} {{amount}}',
    'overview.unlink': 'Unlink',
    'overview.linkAccountPrompt': 'Link a bank or mobile money account',
    'overview.providerNamePlaceholder': 'Provider name',
    'overview.accountPhonePlaceholder': 'Account / phone number',
    'overview.linking': 'Linking…',
    'overview.linkAccount': 'Link account',
    'overview.loadError': 'Could not load your overview.',
    'overview.linkError': 'Could not link that account.',
    'overview.unlinkError': 'Could not unlink this account.',
    'overview.verificationFailed': "Could not verify that {{provider}} account. It wasn't linked.",
    'transfer.title': 'Transfer',
    'transfer.recipientPlaceholder': 'Recipient phone or account number',
    'transfer.amountPlaceholder': 'Amount (RWF)',
    'transfer.contactsLabel': 'Contacts',
    'transfer.addContact': '+ Add',
    'transfer.cancel': 'Cancel',
    'transfer.namePlaceholder': 'Name',
    'transfer.phonePlaceholder': 'Phone number',
    'transfer.saveContact': 'Save contact',
    'transfer.noContacts': 'No saved contacts yet — add one to send money faster next time.',
    'transfer.continue': 'Continue',
    'transfer.confirmTitle': 'Confirm transfer',
    'transfer.toRecipient': 'To {{recipient}}',
    'transfer.amountLine': 'Amount: {{amount}} RWF',
    'transfer.scamWarningTitle': 'Caution needed before this transfer',
    'transfer.scamWarningBody': 'This recipient has been reported by {{count}} other itunda users. Double-check before sending.',
    'transfer.sending': 'Sending…',
    'transfer.send': 'Send {{amount}} RWF',
    'transfer.newBalance': 'New balance: {{amount}} RWF',
    'transfer.done': 'Done',
    'transfer.saveContactError': 'Could not save that contact.',
    'transfer.sendError': 'Could not complete this transfer.',
    'dashboard.mainAccount': 'Main Account',
    'dashboard.transfer': 'Transfer',
    'dashboard.topUp': 'Top up',
    'home.loadError': 'Could not load your account.',
    'quickActions.scanToPay': 'Scan to Pay',
    'quickActions.cards': 'Cards',
    'deviceStepUp.title': '🔒 Verify this device',
    'deviceStepUp.body': 'This is a new device for your account. Re-enter your password to allow it to send money, then try again.',
    'deviceStepUp.passwordPlaceholder': 'Password',
    'deviceStepUp.showPassword': 'Show password',
    'deviceStepUp.hidePassword': 'Hide password',
    'deviceStepUp.cancel': 'Cancel',
    'deviceStepUp.verifying': 'Verifying…',
    'deviceStepUp.verify': 'Verify device',
    'deviceStepUp.genericError': 'Could not verify this device.',
    'scamReport.promptQuestion': 'Why are you reporting {{identifier}}?',
    'scamReport.thanks': 'Thanks -- this number has been reported.',
    'scamReport.reporting': 'Reporting…',
    'scamReport.reportLink': 'Report this number as a scam',
    'home.recentActivity': 'Recent Activity',
    'home.noTransactions': "No transactions yet — once you send, receive, or spend, it'll all show up here.",
    'home.unusuallyLarge': 'Unusually large',
    'discover.title': 'Discover',
    'discover.new': 'NEW',
    'miniWallet.title': 'Mini account',
    'miniWallet.cancel': 'Cancel',
    'miniWallet.addMoney': '+ Add money',
    'miniWallet.ageIneligible': 'Mini accounts are only available for ages 7-18.',
    'miniWallet.openError': 'Could not open a Mini account.',
    'miniWallet.birthDateError': 'Could not save your birth date.',
    'miniWallet.depositError': 'Could not add money to your Mini account.',
    'miniWallet.description': 'A capped starter account for ages 7-18 -- a 500,000 RWF balance cap, 300,000 RWF daily and 2,000,000 RWF monthly deposit limits.',
    'miniWallet.opening': 'Opening…',
    'miniWallet.open': 'Open a Mini account',
    'miniWallet.birthDatePrompt': 'Enter your birth date to check eligibility.',
    'miniWallet.checking': 'Checking…',
    'miniWallet.checkEligibility': 'Check eligibility',
    'miniWallet.amountPlaceholder': 'Amount (RWF)',
    'miniWallet.adding': 'Adding…',
    'miniWallet.add': 'Add',
    'scheduledTransfers.title': 'Scheduled transfers',
    'scheduledTransfers.cancel': 'Cancel',
    'scheduledTransfers.schedule': '+ Schedule',
    'scheduledTransfers.recipientPlaceholder': 'Phone or account number',
    'scheduledTransfers.amountPlaceholder': 'Amount (RWF)',
    'scheduledTransfers.descriptionPlaceholder': 'Description (optional)',
    'scheduledTransfers.scheduling': 'Scheduling…',
    'scheduledTransfers.scheduleButton': 'Schedule transfer',
    'scheduledTransfers.createError': 'Could not schedule this transfer.',
    'scheduledTransfers.cancelError': 'Could not cancel this scheduled transfer.',
    'scheduledTransfers.noTransfers': 'No scheduled transfers yet — schedule one to send money on a future date.',
    'scheduledTransfers.statusScheduled': 'Scheduled',
    'scheduledTransfers.statusSent': 'Sent',
    'scheduledTransfers.statusCancelled': 'Cancelled',
    'scheduledTransfers.statusFailed': 'Failed',
    'autoTransfers.title': 'Auto-transfers',
    'autoTransfers.cancel': 'Cancel',
    'autoTransfers.setUp': '+ Set up',
    'autoTransfers.recipientPlaceholder': 'Phone or account number',
    'autoTransfers.amountPlaceholder': 'Amount (RWF)',
    'autoTransfers.weekly': 'Weekly',
    'autoTransfers.monthly': 'Monthly',
    'autoTransfers.dayOfMonth': 'Day {{day}} of the month',
    'autoTransfers.descriptionPlaceholder': 'Description (optional)',
    'autoTransfers.settingUp': 'Setting up…',
    'autoTransfers.setUpButton': 'Set up auto-transfer',
    'autoTransfers.createError': 'Could not set up this auto-transfer.',
    'autoTransfers.toggleError': 'Could not update this auto-transfer.',
    'autoTransfers.cancelError': 'Could not cancel this auto-transfer.',
    'autoTransfers.noTransfers': 'No auto-transfers set up yet — set one up to send money on a schedule automatically.',
    'autoTransfers.weeklyLabel': 'Weekly ({{day}})',
    'autoTransfers.monthlyLabel': 'Monthly (day {{day}})',
    'autoTransfers.statusActive': 'Active',
    'autoTransfers.statusPaused': 'Paused',
    'autoTransfers.statusCancelled': 'Cancelled',
    'autoTransfers.pause': 'Pause',
    'autoTransfers.resume': 'Resume',
    'autoTransfers.cancelAction': 'Cancel',
    'weekday.monday': 'Monday',
    'weekday.tuesday': 'Tuesday',
    'weekday.wednesday': 'Wednesday',
    'weekday.thursday': 'Thursday',
    'weekday.friday': 'Friday',
    'weekday.saturday': 'Saturday',
    'weekday.sunday': 'Sunday',
    'requestMoney.title': 'Request money',
    'requestMoney.cancel': 'Cancel',
    'requestMoney.newRequest': '+ New request',
    'requestMoney.amountPlaceholder': 'Amount (RWF)',
    'requestMoney.whatsItFor': "What's it for? (optional)",
    'requestMoney.creating': 'Creating…',
    'requestMoney.createButton': 'Create request',
    'requestMoney.shareCode': 'Share this code -- expires in 15 minutes',
    'requestMoney.payCodePlaceholder': 'Pay a request code',
    'requestMoney.paying': 'Paying…',
    'requestMoney.pay': 'Pay',
    'requestMoney.createError': 'Could not create this request.',
    'requestMoney.payError': 'Could not pay this request.',
    'requestMoney.myRequests': 'My requests',
    'requestMoney.statusPending': 'Pending',
    'requestMoney.statusPaid': 'Paid',
    'requestMoney.statusExpired': 'Expired',
    'autoTopUp.title': 'Auto top-up',
    'autoTopUp.cancel': 'Cancel',
    'autoTopUp.edit': 'Edit',
    'autoTopUp.setUp': '+ Set up',
    'autoTopUp.linkFirst': 'Link an external bank/mobile money account first to enable auto top-up.',
    'autoTopUp.selectAccount': 'Select linked account',
    'autoTopUp.thresholdPlaceholder': 'Top up when balance falls below (RWF)',
    'autoTopUp.topUpPlaceholder': 'Top-up amount (RWF)',
    'autoTopUp.maxPerDay': 'Max times per day',
    'autoTopUp.saving': 'Saving…',
    'autoTopUp.save': 'Save',
    'autoTopUp.saveError': 'Could not save this setting.',
    'autoTopUp.on': 'On',
    'autoTopUp.off': 'Off',
    'autoTopUp.summaryLine': '{{state}} — top up {{topUp}} RWF when balance falls below {{threshold}} RWF',
    'autoTopUp.upToPerDay': 'Up to {{cap}}x/day · {{count}} triggered today',
    'autoTopUp.turnOff': 'Turn off',
    'autoTopUp.turnOn': 'Turn on',
    'autoTopUp.checking': 'Checking…',
    'autoTopUp.checkNow': 'Check now',
    'autoTopUp.checkError': 'Could not check auto top-up.',
  },
  rw: {
    'login.tagline': "Injira kuri konti yawe ya Itunda.",
    'login.phoneNumber': 'Numero ya telefoni',
    'login.password': "Ijambo ry'ibanga",
    'login.showPassword': "Erekana ijambo ry'ibanga",
    'login.hidePassword': "Hisha ijambo ry'ibanga",
    'login.signingIn': 'Kwinjira…',
    'login.signIn': 'Injira',
    'login.createAccount': 'Uri mushya kuri itunda? Fungura konti',
    'login.connectionError': 'Ntibishoboka guhuza ubu. Ongera ugerageze mu kanya.',
    'overview.netWorth': 'Umutungo wose',
    'overview.accounts': 'Konti',
    'overview.savings': "Ubwizigame: {{amount}} RWF mu migambi {{count}}",
    'overview.loans': 'Inguzanyo: {{amount}} RWF zisigaye, {{count}} zikoreshwa',
    'overview.investments': "Ishoramari: {{amount}} RWF yatanzwe, ibintu {{count}}",
    'overview.insurance': "Ubwishingizi: gahunda {{count}} zikora, {{amount}} RWF ku kwezi",
    'overview.linkedAccounts': 'Konti zihujwe',
    'overview.demoBalance': "Amafaranga y'ikitegererezo: {{currency}} {{amount}}",
    'overview.unlink': 'Kuraho ihuza',
    'overview.linkAccountPrompt': 'Huza konti ya banki cyangwa Mobile Money',
    'overview.providerNamePlaceholder': "Izina ry'ikigo",
    'overview.accountPhonePlaceholder': 'Numero ya konti / telefoni',
    'overview.linking': 'Guhuza…',
    'overview.linkAccount': 'Huza konti',
    'overview.loadError': "Ntibishoboka gushaka amakuru y'umutungo wawe.",
    'overview.linkError': 'Ntibishoboka guhuza iyo konti.',
    'overview.unlinkError': 'Ntibishoboka kuraho iyo konti.',
    'overview.verificationFailed': 'Ntibishoboka kwemeza iyo konti ya {{provider}}. Ntiyahujwe.',
    'transfer.title': 'Kohereza amafaranga',
    'transfer.recipientPlaceholder': "Numero ya telefoni cyangwa konti y'uwakira",
    'transfer.amountPlaceholder': 'Amafaranga (RWF)',
    'transfer.contactsLabel': 'Abo wabitse',
    'transfer.addContact': '+ Ongeraho',
    'transfer.cancel': 'Hagarika',
    'transfer.namePlaceholder': 'Izina',
    'transfer.phonePlaceholder': 'Numero ya telefoni',
    'transfer.saveContact': 'Bika uyu muntu',
    'transfer.noContacts': 'Nta bantu wabitse. Ongeraho umwe kugira ngo wihute mu kohereza amafaranga ubutaha.',
    'transfer.continue': 'Komeza',
    'transfer.confirmTitle': 'Emeza kohereza amafaranga',
    'transfer.toRecipient': 'Kuri {{recipient}}',
    'transfer.amountLine': 'Amafaranga: {{amount}} RWF',
    'transfer.scamWarningTitle': 'Witondere mbere yo kohereza',
    'transfer.scamWarningBody': "Uyu muntu yatanzweho raporo n'abakoresha itunda {{count}}. Genzura neza mbere yo kohereza.",
    'transfer.sending': 'Kohereza…',
    'transfer.send': 'Ohereza {{amount}} RWF',
    'transfer.newBalance': 'Amafaranga asigaye: {{amount}} RWF',
    'transfer.done': 'Byarangiye',
    'transfer.saveContactError': 'Ntibishoboka kubika uwo muntu.',
    'transfer.sendError': 'Ntibishoboka kurangiza kohereza amafaranga.',
    'dashboard.mainAccount': 'Konti nyamukuru',
    'dashboard.transfer': 'Kohereza',
    'dashboard.topUp': 'Ongera amafaranga',
    'home.loadError': "Ntibishoboka gushaka amakuru ya konti yawe.",
    'quickActions.scanToPay': 'Kwishyura ukoresheje QR',
    'quickActions.cards': 'Amakarita',
    'deviceStepUp.title': '🔒 Emeza iyi terefoni',
    'deviceStepUp.body': "Iyi ni terefoni nshya kuri konti yawe. Ongera wandike ijambo ry'ibanga kugira ngo wemeze ko ishobora kohereza amafaranga, hanyuma ugerageze nanone.",
    'deviceStepUp.passwordPlaceholder': "Ijambo ry'ibanga",
    'deviceStepUp.showPassword': "Erekana ijambo ry'ibanga",
    'deviceStepUp.hidePassword': "Hisha ijambo ry'ibanga",
    'deviceStepUp.cancel': 'Hagarika',
    'deviceStepUp.verifying': 'Kwemeza…',
    'deviceStepUp.verify': 'Emeza terefoni',
    'deviceStepUp.genericError': 'Ntibishoboka kwemeza iyi terefoni.',
    'scamReport.promptQuestion': 'Kuki utanga raporo kuri {{identifier}}?',
    'scamReport.thanks': 'Murakoze -- iyi numero yatanzweho raporo.',
    'scamReport.reporting': 'Kohereza raporo…',
    'scamReport.reportLink': 'Tanga raporo kuri iyi numero',
    'home.recentActivity': 'Ibikorwa vya vuba',
    'home.noTransactions': "Nta bikorwa urakora. Iyo wohereje, wakiriye, cyangwa wakoresheje amafaranga, byose bizagaragara hano.",
    'home.unusuallyLarge': 'Menshi kurusha uko bisanzwe',
    'discover.title': 'Menya',
    'discover.new': 'GISHYA',
    'miniWallet.title': 'Konti ntoya',
    'miniWallet.cancel': 'Hagarika',
    'miniWallet.addMoney': '+ Ongeraho amafaranga',
    'miniWallet.ageIneligible': 'Konti ntoya ziboneka gusa ku myaka 7-18.',
    'miniWallet.openError': 'Ntibishoboka gufungura konti ntoya.',
    'miniWallet.birthDateError': 'Ntibishoboka kubika itariki y\'amavuko yawe.',
    'miniWallet.depositError': 'Ntibishoboka kongeraho amafaranga kuri konti ntoya.',
    'miniWallet.description': "Konti ntoya y'itangira ku myaka 7-18 -- ntirengeje 500,000 RWF, ntirengeje 300,000 RWF ku munsi cyangwa 2,000,000 RWF ku kwezi.",
    'miniWallet.opening': 'Gufungura…',
    'miniWallet.open': 'Fungura konti ntoya',
    'miniWallet.birthDatePrompt': "Andika itariki y'amavuko yawe kugira ngo tumenye niba ubishoboye.",
    'miniWallet.checking': 'Kugenzura…',
    'miniWallet.checkEligibility': 'Genzura niba wemerewe',
    'miniWallet.amountPlaceholder': 'Amafaranga (RWF)',
    'miniWallet.adding': 'Kongeraho…',
    'miniWallet.add': 'Ongeraho',
    'scheduledTransfers.title': 'Kohereza byateganyijwe',
    'scheduledTransfers.cancel': 'Hagarika',
    'scheduledTransfers.schedule': '+ Tegura',
    'scheduledTransfers.recipientPlaceholder': "Numero ya telefoni cyangwa konti",
    'scheduledTransfers.amountPlaceholder': 'Amafaranga (RWF)',
    'scheduledTransfers.descriptionPlaceholder': 'Ibisobanuro (si ngombwa)',
    'scheduledTransfers.scheduling': 'Gutegura…',
    'scheduledTransfers.scheduleButton': 'Tegura kohereza',
    'scheduledTransfers.createError': 'Ntibishoboka gutegura iyi kohereza.',
    'scheduledTransfers.cancelError': 'Ntibishoboka guhagarika iyi kohereza yateganyijwe.',
    'scheduledTransfers.noTransfers': 'Nta kohereza byateganyijwe urakora. Tegura kimwe kugira ngo wohereze amafaranga ku itariki uzaza.',
    'scheduledTransfers.statusScheduled': 'Byateganyijwe',
    'scheduledTransfers.statusSent': 'Byoherejwe',
    'scheduledTransfers.statusCancelled': 'Byahagaritswe',
    'scheduledTransfers.statusFailed': 'Byanze',
    'autoTransfers.title': 'Kohereza byikoresha',
    'autoTransfers.cancel': 'Hagarika',
    'autoTransfers.setUp': '+ Tunganya',
    'autoTransfers.recipientPlaceholder': "Numero ya telefoni cyangwa konti",
    'autoTransfers.amountPlaceholder': 'Amafaranga (RWF)',
    'autoTransfers.weekly': 'Buri cyumweru',
    'autoTransfers.monthly': 'Buri kwezi',
    'autoTransfers.dayOfMonth': 'Umunsi wa {{day}} w\'ukwezi',
    'autoTransfers.descriptionPlaceholder': 'Ibisobanuro (si ngombwa)',
    'autoTransfers.settingUp': 'Gutunganya…',
    'autoTransfers.setUpButton': 'Tunganya kohereza byikoresha',
    'autoTransfers.createError': 'Ntibishoboka gutunganya iki kohereza cyikoresha.',
    'autoTransfers.toggleError': 'Ntibishoboka kuvugurura iki kohereza cyikoresha.',
    'autoTransfers.cancelError': 'Ntibishoboka guhagarika iki kohereza cyikoresha.',
    'autoTransfers.noTransfers': 'Nta kohereza byikoresha utunganyije. Tunganya kimwe kugira ngo wohereze amafaranga ku gihe cyagenwe automatike.',
    'autoTransfers.weeklyLabel': 'Buri cyumweru ({{day}})',
    'autoTransfers.monthlyLabel': 'Buri kwezi (umunsi wa {{day}})',
    'autoTransfers.statusActive': 'Birakora',
    'autoTransfers.statusPaused': 'Byahagaritswe by\'agateganyo',
    'autoTransfers.statusCancelled': 'Byahagaritswe',
    'autoTransfers.pause': 'Hagarika by\'agateganyo',
    'autoTransfers.resume': 'Komeza',
    'autoTransfers.cancelAction': 'Hagarika',
    'weekday.monday': 'Kuwa mbere',
    'weekday.tuesday': 'Kuwa kabiri',
    'weekday.wednesday': 'Kuwa gatatu',
    'weekday.thursday': 'Kuwa kane',
    'weekday.friday': 'Kuwa gatanu',
    'weekday.saturday': 'Kuwa gatandatu',
    'weekday.sunday': 'Ku cyumweru',
    'requestMoney.title': 'Saba amafaranga',
    'requestMoney.cancel': 'Hagarika',
    'requestMoney.newRequest': '+ Icyifuzo gishya',
    'requestMoney.amountPlaceholder': 'Amafaranga (RWF)',
    'requestMoney.whatsItFor': 'Ni ibiki? (si ngombwa)',
    'requestMoney.creating': 'Gukora…',
    'requestMoney.createButton': 'Kora icyifuzo',
    'requestMoney.shareCode': 'Sangiza uyu kode -- uzarangira mu minota 15',
    'requestMoney.payCodePlaceholder': 'Ishyura ukoresheje kode',
    'requestMoney.paying': 'Kwishyura…',
    'requestMoney.pay': 'Ishyura',
    'requestMoney.createError': 'Ntibishoboka gukora iki cyifuzo.',
    'requestMoney.payError': 'Ntibishoboka kwishyura iki cyifuzo.',
    'requestMoney.myRequests': 'Ibyifuzo byanjye',
    'requestMoney.statusPending': 'Bitegerejwe',
    'requestMoney.statusPaid': 'Byishyuwe',
    'requestMoney.statusExpired': 'Byarangiye',
    'autoTopUp.title': 'Kwongera amafaranga byikoresha',
    'autoTopUp.cancel': 'Hagarika',
    'autoTopUp.edit': 'Hindura',
    'autoTopUp.setUp': '+ Tunganya',
    'autoTopUp.linkFirst': 'Huza konti ya banki cyangwa Mobile Money mbere yo gukoresha iyi serivisi.',
    'autoTopUp.selectAccount': 'Hitamo konti yahujwe',
    'autoTopUp.thresholdPlaceholder': 'Ongera amafaranga iyo asigaye ari munsi ya (RWF)',
    'autoTopUp.topUpPlaceholder': 'Amafaranga yo kongera (RWF)',
    'autoTopUp.maxPerDay': 'Inshuro ntarengwa ku munsi',
    'autoTopUp.saving': 'Kubika…',
    'autoTopUp.save': 'Bika',
    'autoTopUp.saveError': 'Ntibishoboka kubika iyi migenzo.',
    'autoTopUp.on': 'Birakora',
    'autoTopUp.off': 'Ntibikora',
    'autoTopUp.summaryLine': '{{state}} — ongera {{topUp}} RWF iyo amafaranga asigaye ari munsi ya {{threshold}} RWF',
    'autoTopUp.upToPerDay': 'Inshuro {{cap}}/umunsi ntarengwa · {{count}} zakozwe uyu munsi',
    'autoTopUp.turnOff': 'Hagarika',
    'autoTopUp.turnOn': 'Koresha',
    'autoTopUp.checking': 'Kugenzura…',
    'autoTopUp.checkNow': 'Genzura nonaha',
    'autoTopUp.checkError': 'Ntibishoboka kugenzura kwongera amafaranga byikoresha.',
  },
};
