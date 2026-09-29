// Same real fix as bank-mfe's own RemoteI18nProvider.tsx (2026-08-15) -- see that
// file's comment for why host-app needs to import THIS module's I18nProvider
// specifically, not use its own.
export { I18nProvider as default } from './i18n/I18nContext';
