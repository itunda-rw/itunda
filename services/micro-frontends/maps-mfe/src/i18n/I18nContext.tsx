import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { DEFAULT_LOCALE, translations, type Locale } from './translations';

const STORAGE_KEY = 'itunda.locale';

// No external i18n library, same deliberate choice bank-mfe/kyc-mfe's own
// I18nContext.tsx already made. The SAME 'itunda.locale' localStorage key is used
// here deliberately -- maps-mfe is mounted alongside bank-mfe inside host-app (Module
// Federation, same document/origin), so a language choice made anywhere in itunda is
// honored here too, not a second, disconnected preference.

function detectInitialLocale(): Locale {
  const stored = typeof window !== 'undefined' ? window.localStorage.getItem(STORAGE_KEY) : null;
  if (stored === 'en' || stored === 'rw' || stored === 'fr') return stored;
  const browserLocale = (typeof navigator !== 'undefined' ? navigator.language : '').toLowerCase();
  if (browserLocale.startsWith('rw')) return 'rw';
  if (browserLocale.startsWith('fr')) return 'fr';
  return DEFAULT_LOCALE;
}

interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: keyof (typeof translations)['en']) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

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
    // Same real cross-microfrontend sync as bank-mfe's/kyc-mfe's I18nContext.tsx
    // (2026-08-15) -- see those files' own comment for why a native 'storage' event
    // isn't enough here.
    window.dispatchEvent(new CustomEvent<Locale>('itunda:locale-change', { detail: next }));
  }, []);

  const t = useCallback(
    (key: keyof (typeof translations)['en']) => translations[locale][key] ?? translations[DEFAULT_LOCALE][key] ?? key,
    [locale],
  );

  return <I18nContext.Provider value={{ locale, setLocale, t }}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used within an I18nProvider');
  return ctx;
}
