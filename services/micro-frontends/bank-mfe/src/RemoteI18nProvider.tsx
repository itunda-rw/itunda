// Real fix (2026-08-15) -- host-app federates in the bare `./BankDashboard`
// component (see vite.config.ts `exposes`), but BankDashboard's own `useI18n()`
// throws unless it's rendered inside THIS module's `I18nProvider` specifically:
// even though host-app has its own I18nProvider (i18n/I18nContext.tsx, module
// federation only shares `react`/`react-dom`, not app code), a Context object
// created inside one federated module is not the same Context object as one
// created inside another -- host-app's own provider can't satisfy a `useI18n()`
// call bundled inside bank-mfe's own remoteEntry.js. This default-export
// re-wrap lets host-app `React.lazy`-import the SAME I18nProvider bank-mfe's
// own components actually use, the same way it already lazy-imports
// BankDashboard itself.
export { I18nProvider as default } from './i18n/I18nContext';
