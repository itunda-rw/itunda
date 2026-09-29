// Real first slice of pay-checkout localization (2026-08-15) -- ports the exact same
// architecture bank-mfe/merchant-mfe's own i18n already proved out (see those files'
// own doc comments for the full "why"). This page is arguably the single
// highest-reach real surface in the whole itunda web estate to localize: it's reached
// by ANY buyer via an external merchant's own payment link -- no itunda login, no
// itunda app, not even necessarily an itunda account -- so unlike every other MFE
// this session touched, there is no shared localStorage token carrying a language
// preference from elsewhere in itunda. Detection here is purely
// `navigator.language` + a real visible switcher, never assumed.
//
// Small, single-screen scope matches the page itself (129 lines, one real route) --
// full coverage in one pass, not a partial slice like the larger MFEs needed.
// All translations here are a good-faith, careful effort -- NOT verified by a native
// speaker of either language -- and should get real native-speaker review before
// being treated as production-final, same honesty standard as every other itunda
// translation added this session.

export type Locale = 'en' | 'rw' | 'fr';

export const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Ikinyarwanda' },
  { code: 'fr', label: 'Français' },
];

export const DEFAULT_LOCALE: Locale = 'en';

export type TranslationKey =
  | 'checkout.noPaymentReference'
  | 'checkout.linkInvalidOrExpired'
  | 'checkout.loadError'
  | 'checkout.connectionError'
  | 'checkout.payWith'
  | 'checkout.scanInstruction'
  | 'checkout.paymentComplete'
  | 'checkout.returningToMerchant'
  | 'checkout.linkExpired';

export const translations: Record<Locale, Record<TranslationKey, string>> = {
  en: {
    'checkout.noPaymentReference': 'No payment reference in this link.',
    'checkout.linkInvalidOrExpired': 'This payment link is invalid or has expired.',
    'checkout.loadError': 'Could not load this payment.',
    'checkout.connectionError': 'Could not reach itunda. Check your connection and try again.',
    'checkout.payWith': 'Pay with itunda',
    'checkout.scanInstruction': 'Scan this with the itunda app to complete payment',
    'checkout.paymentComplete': '✓ Payment complete',
    'checkout.returningToMerchant': ' — returning to the merchant…',
    'checkout.linkExpired': 'This payment link has expired',
  },
  rw: {
    'checkout.noPaymentReference': 'Nta makuru y\'ubwishyu ari kuri iyi link.',
    'checkout.linkInvalidOrExpired': 'Iyi link y\'ubwishyu ntibaho cyangwa yarangiye igihe.',
    'checkout.loadError': 'Ntibishoboka gushakisha ubu bwishyu.',
    'checkout.connectionError': 'Ntibishoboka guhura na itunda. Reba interineti yawe hanyuma ongera ugerageze.',
    'checkout.payWith': 'Ishyura ukoresheje itunda',
    'checkout.scanInstruction': 'Sikana iyi ukoresheje porogaramu ya itunda kugira ngo urangize kwishyura',
    'checkout.paymentComplete': '✓ Kwishyura byarangiye',
    'checkout.returningToMerchant': ' — kugaruka ku ucuruza…',
    'checkout.linkExpired': 'Iyi link y\'ubwishyu yarangiye igihe',
  },
  fr: {
    'checkout.noPaymentReference': 'Aucune référence de paiement dans ce lien.',
    'checkout.linkInvalidOrExpired': 'Ce lien de paiement est invalide ou a expiré.',
    'checkout.loadError': 'Impossible de charger ce paiement.',
    'checkout.connectionError': 'Impossible de contacter itunda. Vérifiez votre connexion et réessayez.',
    'checkout.payWith': 'Payer avec itunda',
    'checkout.scanInstruction': 'Scannez ceci avec l\'application itunda pour finaliser le paiement',
    'checkout.paymentComplete': '✓ Paiement terminé',
    'checkout.returningToMerchant': ' — retour au commerçant…',
    'checkout.linkExpired': 'Ce lien de paiement a expiré',
  },
};
