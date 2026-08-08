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
  | 'login.connectionError';

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
  },
};
