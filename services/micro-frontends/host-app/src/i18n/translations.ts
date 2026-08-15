export type Locale = 'en' | 'rw' | 'fr';

export const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Kinyarwanda' },
  { code: 'fr', label: 'Français' },
];

export const DEFAULT_LOCALE: Locale = 'en';

export type TranslationKey = 'tabs.home' | 'tabs.identity';

// host-app's own real user-facing text is tiny (2 tab labels -- everything else is
// bank_mfe/kyc_mfe content, each with their own full translations.ts). Kept in the
// same flat Record<Locale, Record<TranslationKey, string>> shape as every other
// itunda MFE's i18n so it stays a plain, TS-enforced dictionary, not a special case.
export const translations: Record<Locale, Record<TranslationKey, string>> = {
  en: {
    'tabs.home': 'Home',
    'tabs.identity': 'Identity',
  },
  rw: {
    'tabs.home': 'Ahabanza',
    'tabs.identity': 'Umwirondoro',
  },
  fr: {
    'tabs.home': 'Accueil',
    'tabs.identity': 'Identité',
  },
};
