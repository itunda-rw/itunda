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
  | 'tabs.settings'
  // Real 3rd-localization-pass additions (2026-08-15): CollectScreen, the default/
  // first-shown tab (highest-traffic real content screen, same "first thing every
  // session sees" priority the shell itself got in the 2nd pass).
  | 'collect.modeQr'
  | 'collect.modeStatic'
  | 'collect.modeCard'
  | 'collect.modeVoucher'
  | 'collect.amount'
  | 'collect.description'
  | 'collect.staticRegisterFirst'
  | 'collect.staticLoadError'
  | 'collect.staticTitle'
  | 'collect.staticBody'
  | 'collect.voucherRedeemError'
  | 'collect.voucherRedeemedTitle'
  | 'collect.voucherRedeemAnother'
  | 'collect.voucherTitle'
  | 'collect.voucherBody'
  | 'collect.voucherIdLabel'
  | 'collect.voucherRedeeming'
  | 'collect.voucherRedeemButton'
  | 'collect.qrGenerateError'
  | 'collect.qrShowTitle'
  | 'collect.qrExpiresPrefix'
  | 'collect.qrNew'
  | 'collect.qrTitle'
  | 'collect.qrGenerating'
  | 'collect.qrGenerateButton'
  | 'collect.cardChargeError'
  | 'collect.cardChargedTitle'
  | 'collect.cardChargeAnother'
  | 'collect.cardTitle'
  | 'collect.cardDemoNote'
  | 'collect.cardNumberLabel'
  | 'collect.cardExpiryMonth'
  | 'collect.cardExpiryYear'
  | 'collect.cardCvc'
  | 'collect.cardCharging'
  | 'collect.cardChargeButton';

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
    'collect.modeQr': 'QR code',
    'collect.modeStatic': 'Static QR',
    'collect.modeCard': 'Card',
    'collect.modeVoucher': 'Voucher',
    'collect.amount': 'Amount (RWF)',
    'collect.description': 'Description',
    'collect.staticRegisterFirst': 'Register as a merchant first.',
    'collect.staticLoadError': 'Could not load your static QR code.',
    'collect.staticTitle': 'Your permanent QR code',
    'collect.staticBody': "Print this once and display it at your till -- a customer scans it, enters their own amount, and pays. No app needed on your end at sale time.",
    'collect.voucherRedeemError': 'Could not redeem this voucher.',
    'collect.voucherRedeemedTitle': 'Voucher redeemed',
    'collect.voucherRedeemAnother': 'Redeem another voucher',
    'collect.voucherTitle': 'Redeem a gift voucher',
    'collect.voucherBody': 'Ask the customer to show you their voucher, then enter its id here.',
    'collect.voucherIdLabel': 'Voucher id',
    'collect.voucherRedeeming': 'Redeeming…',
    'collect.voucherRedeemButton': 'Redeem',
    'collect.qrGenerateError': 'Could not generate a QR code.',
    'collect.qrShowTitle': 'Show this to your customer',
    'collect.qrExpiresPrefix': 'Expires',
    'collect.qrNew': 'New QR code',
    'collect.qrTitle': 'Collect a payment',
    'collect.qrGenerating': 'Generating…',
    'collect.qrGenerateButton': 'Generate QR code',
    'collect.cardChargeError': 'Could not charge this card.',
    'collect.cardChargedTitle': 'Card charged',
    'collect.cardChargeAnother': 'Charge another card',
    'collect.cardTitle': 'Charge a card',
    'collect.cardDemoNote': 'Demo authorization only — try 4242 4242 4242 4242 (approves) or 4000 0000 0000 0002 (declines).',
    'collect.cardNumberLabel': 'Card number',
    'collect.cardExpiryMonth': 'Expiry month',
    'collect.cardExpiryYear': 'Expiry year',
    'collect.cardCvc': 'CVC',
    'collect.cardCharging': 'Charging…',
    'collect.cardChargeButton': 'Charge card',
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
    'collect.modeQr': 'QR code',
    'collect.modeStatic': 'QR ihoraho',
    'collect.modeCard': 'Ikarita',
    'collect.modeVoucher': 'Vocha',
    'collect.amount': 'Amafaranga (RWF)',
    'collect.description': 'Ibisobanuro',
    'collect.staticRegisterFirst': 'Banza wiyandikishe nk\'ucuruza.',
    'collect.staticLoadError': 'Ntibishoboka gushakisha QR yawe ihoraho.',
    'collect.staticTitle': 'QR yawe ihoraho',
    'collect.staticBody': 'Chapa iyi rimwe uyishyire ku cyicaro cyawe -- umukiriya ayisikana, yandika amafaranga we ubwe, akishyura. Nta porogaramu ukeneye kuri wowe igihe cyo kugurisha.',
    'collect.voucherRedeemError': 'Ntibishoboka gukoresha iyi vocha.',
    'collect.voucherRedeemedTitle': 'Vocha yakoreshejwe',
    'collect.voucherRedeemAnother': 'Koresha indi vocha',
    'collect.voucherTitle': 'Koresha vocha',
    'collect.voucherBody': 'Saba umukiriya kukwereka vocha ye, hanyuma wandike nomero yayo hano.',
    'collect.voucherIdLabel': 'Nomero ya vocha',
    'collect.voucherRedeeming': 'Gukoresha…',
    'collect.voucherRedeemButton': 'Koresha',
    'collect.qrGenerateError': 'Ntibishoboka gukora QR code.',
    'collect.qrShowTitle': 'Erekana iki ku mukiriya wawe',
    'collect.qrExpiresPrefix': 'Irangira',
    'collect.qrNew': 'QR code nshya',
    'collect.qrTitle': 'Kwakira amafaranga',
    'collect.qrGenerating': 'Gukora…',
    'collect.qrGenerateButton': 'Kora QR code',
    'collect.cardChargeError': 'Ntibishoboka gukuramo amafaranga kuri iyi karita.',
    'collect.cardChargedTitle': 'Amafaranga yakuwe kuri karita',
    'collect.cardChargeAnother': 'Kuramo amafaranga kuri indi karita',
    'collect.cardTitle': 'Kuramo amafaranga kuri karita',
    'collect.cardDemoNote': 'Ni ikizamini gusa — gerageza 4242 4242 4242 4242 (byemewe) cyangwa 4000 0000 0000 0002 (byanze).',
    'collect.cardNumberLabel': 'Nomero ya karita',
    'collect.cardExpiryMonth': 'Ukwezi irangirira',
    'collect.cardExpiryYear': 'Umwaka irangirira',
    'collect.cardCvc': 'CVC',
    'collect.cardCharging': 'Gukuramo…',
    'collect.cardChargeButton': 'Kuramo amafaranga',
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
    'collect.modeQr': 'Code QR',
    'collect.modeStatic': 'QR fixe',
    'collect.modeCard': 'Carte',
    'collect.modeVoucher': 'Bon cadeau',
    'collect.amount': 'Montant (RWF)',
    'collect.description': 'Description',
    'collect.staticRegisterFirst': 'Inscrivez-vous d\'abord en tant que commerçant.',
    'collect.staticLoadError': 'Impossible de charger votre code QR fixe.',
    'collect.staticTitle': 'Votre code QR permanent',
    'collect.staticBody': 'Imprimez-le une fois et affichez-le à votre caisse -- le client le scanne, saisit lui-même le montant, et paie. Aucune application nécessaire de votre côté au moment de la vente.',
    'collect.voucherRedeemError': 'Impossible d\'utiliser ce bon.',
    'collect.voucherRedeemedTitle': 'Bon utilisé',
    'collect.voucherRedeemAnother': 'Utiliser un autre bon',
    'collect.voucherTitle': 'Utiliser un bon cadeau',
    'collect.voucherBody': 'Demandez au client de vous montrer son bon, puis saisissez son identifiant ici.',
    'collect.voucherIdLabel': 'Identifiant du bon',
    'collect.voucherRedeeming': 'Utilisation en cours…',
    'collect.voucherRedeemButton': 'Utiliser',
    'collect.qrGenerateError': 'Impossible de générer un code QR.',
    'collect.qrShowTitle': 'Montrez ceci à votre client',
    'collect.qrExpiresPrefix': 'Expire',
    'collect.qrNew': 'Nouveau code QR',
    'collect.qrTitle': 'Encaisser un paiement',
    'collect.qrGenerating': 'Génération en cours…',
    'collect.qrGenerateButton': 'Générer le code QR',
    'collect.cardChargeError': 'Impossible de débiter cette carte.',
    'collect.cardChargedTitle': 'Carte débitée',
    'collect.cardChargeAnother': 'Débiter une autre carte',
    'collect.cardTitle': 'Débiter une carte',
    'collect.cardDemoNote': 'Autorisation de démonstration uniquement — essayez 4242 4242 4242 4242 (approuvée) ou 4000 0000 0000 0002 (refusée).',
    'collect.cardNumberLabel': 'Numéro de carte',
    'collect.cardExpiryMonth': 'Mois d\'expiration',
    'collect.cardExpiryYear': 'Année d\'expiration',
    'collect.cardCvc': 'CVC',
    'collect.cardCharging': 'Débit en cours…',
    'collect.cardChargeButton': 'Débiter la carte',
  },
};
