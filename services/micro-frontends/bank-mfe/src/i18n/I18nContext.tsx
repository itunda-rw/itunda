import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { DEFAULT_LOCALE, translations, type Locale } from './translations';

const STORAGE_KEY = 'itunda.locale';

// No external i18n library pulled in deliberately -- this workspace is Yarn PnP, and a
// flat key-value dictionary covers itunda's real current scope (2 locales, 2 translated
// screens so far) without the weight of a framework built for dozens of locales and
// plural/gender rule tables itunda doesn't need yet. Reassess if/when real coverage
// grows past what a plain dictionary comfortably handles.

function detectInitialLocale(): Locale {
  const stored = typeof window !== 'undefined' ? window.localStorage.getItem(STORAGE_KEY) : null;
  if (stored === 'en' || stored === 'rw' || stored === 'fr') return stored;
  const browserLocale = (typeof navigator !== 'undefined' ? navigator.language : '').toLowerCase();
  if (browserLocale.startsWith('rw')) return 'rw';
  if (browserLocale.startsWith('fr')) return 'fr';
  return DEFAULT_LOCALE;
}

type TranslationParams = Record<string, string | number>;

interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: keyof (typeof translations)['en'], params?: TranslationParams) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

// Real {{placeholder}} interpolation (2026-08-08, added alongside the overview.* keys
// that need it) -- named placeholders instead of string concatenation, so a translation
// can reorder them per-language instead of being locked into English word order.
function interpolate(template: string, params?: TranslationParams): string {
  if (!params) return template;
  return template.replace(/\{\{(\w+)\}\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectInitialLocale);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Private-browsing/storage-disabled: locale still works for this session, just
      // doesn't persist across a reload. Not worth failing the language switch over.
    }
    // Real cross-microfrontend sync (2026-08-15): bank-mfe and kyc-mfe are both loaded
    // live via Module Federation into host-app's SAME document -- a native 'storage'
    // event never fires for same-document localStorage writes (it only fires in OTHER
    // tabs/windows), so without this, host-app's own tab labels would stay in whatever
    // language they started in until a full page reload. This custom event lets any
    // itunda surface sharing this locale key react to a change made in a sibling MFE
    // immediately, in the same page.
    window.dispatchEvent(new CustomEvent<Locale>('itunda:locale-change', { detail: next }));
  }, []);

  const t = useCallback(
    (key: keyof (typeof translations)['en'], params?: TranslationParams) =>
      interpolate(translations[locale][key] ?? translations[DEFAULT_LOCALE][key] ?? key, params),
    [locale],
  );

  return <I18nContext.Provider value={{ locale, setLocale, t }}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used within an I18nProvider');
  return ctx;
}
