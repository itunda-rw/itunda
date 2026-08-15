import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { DEFAULT_LOCALE, translations, type Locale } from './translations';

const STORAGE_KEY = 'itunda.locale';

// Shares bank-mfe's/kyc-mfe's exact 'itunda.locale' localStorage key deliberately --
// host-app mounts both of those live via Module Federation into this SAME document, so
// a language chosen inside either child should be reflected in host-app's own tab
// labels too, not a third, disconnected preference.

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
  t: (key: keyof (typeof translations)['en']) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<Locale>(detectInitialLocale);

  // Real cross-microfrontend sync (2026-08-15): host-app itself has no language
  // switcher of its own (bank_mfe and kyc_mfe each already render one) -- it only
  // ever needs to REACT to a locale chosen inside one of those children, live, in the
  // same page. A native 'storage' event never fires for a same-document localStorage
  // write, so bank-mfe's/kyc-mfe's I18nContext.tsx each dispatch a real
  // 'itunda:locale-change' CustomEvent on every setLocale call; host-app just listens.
  useEffect(() => {
    const handler = (e: Event) => {
      const next = (e as CustomEvent<Locale>).detail;
      if (next === 'en' || next === 'rw' || next === 'fr') setLocale(next);
    };
    window.addEventListener('itunda:locale-change', handler);
    return () => window.removeEventListener('itunda:locale-change', handler);
  }, []);

  const t = useCallback(
    (key: keyof (typeof translations)['en']) => translations[locale][key] ?? translations[DEFAULT_LOCALE][key] ?? key,
    [locale],
  );

  return <I18nContext.Provider value={{ locale, t }}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used within an I18nProvider');
  return ctx;
}
