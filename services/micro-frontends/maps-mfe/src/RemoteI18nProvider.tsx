// Same real fix as bank-mfe's/kyc-mfe's own RemoteI18nProvider.tsx (2026-08-15) --
// see those files' comments for why bank-mfe needs to import THIS module's
// I18nProvider specifically to wrap <MapView />, not use its own: MapView's own
// useI18n() call binds to maps-mfe's own bundled Context object, which bank-mfe's own
// I18nProvider cannot satisfy even though both share the same React instance.
export { I18nProvider as default } from './i18n/I18nContext';
