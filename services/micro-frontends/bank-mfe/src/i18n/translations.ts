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

type TranslationKey =
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
  | 'overview.verificationFailed';

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
  },
};
