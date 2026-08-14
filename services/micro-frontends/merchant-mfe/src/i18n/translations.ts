// Real first slice of merchant-mfe localization (2026-08-15) -- ports the exact same
// architecture bank-mfe's own i18n/translations.ts already proved out (see that file's
// own doc comment for the full "why": itunda's actual launch market is Rwanda, where
// English is the third official language, not the most widely spoken one), rather than
// inventing a new pattern. Deliberately starts with all 3 locales together (English,
// Kinyarwanda, French) from day one -- bank-mfe's own history added French only in a
// later pass, after shipping English+Kinyarwanda first; not repeating that gap here.
//
// itunda's own merchant/agent network is arguably even more locally-Rwandan-first than
// bank-mfe's own customer base (real small shop owners, not necessarily English-fluent),
// making this at least as high-value a target as bank-mfe was, not a lower-priority
// afterthought. Same honest scope discipline as bank-mfe's own first pass: infrastructure
// plus ONE real screen (Login, this MFE's own single first-traffic screen), not a claim
// that the whole app is translated. All translations here are a good-faith, careful
// effort -- NOT verified by a native speaker of either language -- and should get real
// native-speaker review before being treated as production-final.

export type Locale = 'en' | 'rw' | 'fr';

export const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Ikinyarwanda' },
  { code: 'fr', label: 'Français' },
];

export const DEFAULT_LOCALE: Locale = 'en';

export type TranslationKey =
  | 'login.tagline'
  | 'login.phoneNumber'
  | 'login.password'
  | 'login.showPassword'
  | 'login.hidePassword'
  | 'login.signingIn'
  | 'login.signIn'
  | 'login.connectionError'
  // Real 2nd-localization-pass additions (2026-08-15): MerchantDashboard's own nav
  // shell -- visible on every session regardless of which tab a merchant is using,
  // same "the shell every session hits" reasoning that made bank-mfe's own Home/
  // Overview tab its own 2nd priority screen. Individual tab CONTENT screens
  // (CollectScreen, PosScreen, etc.) are each their own much larger file and are a
  // real, separate, not-yet-done follow-up -- named honestly, not silently folded in.
  | 'dashboard.loadError'
  | 'dashboard.signOut'
  | 'tabs.collect'
  | 'tabs.pos'
  | 'tabs.eatsOrders'
  | 'tabs.dineIn'
  | 'tabs.bookings'
  | 'tabs.reports'
  | 'tabs.reviews'
  | 'tabs.billing'
  | 'tabs.coupons'
  | 'tabs.ads'
  | 'tabs.business'
  | 'tabs.cashAdvance'
  | 'tabs.payroll'
  | 'tabs.settings';

export const translations: Record<Locale, Record<TranslationKey, string>> = {
  en: {
    'login.tagline': 'Sign in to your Itunda account.',
    'login.phoneNumber': 'Phone number',
    'login.password': 'Password',
    'login.showPassword': 'Show password',
    'login.hidePassword': 'Hide password',
    'login.signingIn': 'Signing in…',
    'login.signIn': 'Sign in',
    'login.connectionError': "Can't connect right now. Please try again in a moment.",
    'dashboard.loadError': 'Could not load your business account.',
    'dashboard.signOut': 'Sign out',
    'tabs.collect': 'Collect',
    'tabs.pos': 'POS',
    'tabs.eatsOrders': 'Eats orders',
    'tabs.dineIn': 'Dine-in',
    'tabs.bookings': 'Bookings',
    'tabs.reports': 'Reports',
    'tabs.reviews': 'Reviews',
    'tabs.billing': 'Billing',
    'tabs.coupons': 'Coupons',
    'tabs.ads': 'Ads',
    'tabs.business': 'Business',
    'tabs.cashAdvance': 'Cash advance',
    'tabs.payroll': 'Payroll',
    'tabs.settings': 'Settings',
  },
  rw: {
    'login.tagline': 'Injira kuri konti yawe ya Itunda.',
    'login.phoneNumber': 'Nomero ya telefoni',
    'login.password': 'Ijambo ry\'ibanga',
    'login.showPassword': 'Erekana ijambo ry\'ibanga',
    'login.hidePassword': 'Hisha ijambo ry\'ibanga',
    'login.signingIn': 'Kwinjira…',
    'login.signIn': 'Injira',
    'login.connectionError': 'Ntibishoboka kwihuza nonaha. Ongera ugerageze mu kanya gato.',
    'dashboard.loadError': 'Ntibishoboka gushakisha konti yawe y\'ubucuruzi.',
    'dashboard.signOut': 'Sohoka',
    'tabs.collect': 'Kwakira',
    'tabs.pos': 'POS',
    'tabs.eatsOrders': 'Itumiza ry\'ibiryo',
    'tabs.dineIn': 'Kurira ku meza',
    'tabs.bookings': 'Gahunda',
    'tabs.reports': 'Raporo',
    'tabs.reviews': 'Ibitekerezo',
    'tabs.billing': 'Kwishyura',
    'tabs.coupons': 'Amakuponi',
    'tabs.ads': 'Kwamamaza',
    'tabs.business': 'Ubucuruzi',
    'tabs.cashAdvance': 'Inguzanyo y\'amafaranga',
    'tabs.payroll': 'Imishahara',
    'tabs.settings': 'Igenamiterere',
  },
  fr: {
    'login.tagline': 'Connectez-vous à votre compte Itunda.',
    'login.phoneNumber': 'Numéro de téléphone',
    'login.password': 'Mot de passe',
    'login.showPassword': 'Afficher le mot de passe',
    'login.hidePassword': 'Masquer le mot de passe',
    'login.signingIn': 'Connexion en cours…',
    'login.signIn': 'Se connecter',
    'login.connectionError': 'Connexion impossible pour le moment. Veuillez réessayer dans un instant.',
    'dashboard.loadError': 'Impossible de charger votre compte professionnel.',
    'dashboard.signOut': 'Se déconnecter',
    'tabs.collect': 'Encaisser',
    'tabs.pos': 'Caisse',
    'tabs.eatsOrders': 'Commandes Eats',
    'tabs.dineIn': 'Sur place',
    'tabs.bookings': 'Réservations',
    'tabs.reports': 'Rapports',
    'tabs.reviews': 'Avis',
    'tabs.billing': 'Facturation',
    'tabs.coupons': 'Coupons',
    'tabs.ads': 'Publicités',
    'tabs.business': 'Entreprise',
    'tabs.cashAdvance': 'Avance de trésorerie',
    'tabs.payroll': 'Paie',
    'tabs.settings': 'Paramètres',
  },
};
