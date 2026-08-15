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
  | 'collect.cardChargeButton'
  // Real 4th-localization-pass additions (2026-08-15): EatsOrdersScreen -- the real
  // Coupang Eats/Baemin-style restaurant order queue, second-highest-traffic content
  // screen after Collect for any merchant running a restaurant.
  | 'eatsOrders.statusPlaced'
  | 'eatsOrders.statusAccepted'
  | 'eatsOrders.statusPreparing'
  | 'eatsOrders.statusReadyForPickup'
  | 'eatsOrders.statusRiderAssigned'
  | 'eatsOrders.statusPickedUp'
  | 'eatsOrders.statusDelivered'
  | 'eatsOrders.statusCancelled'
  | 'eatsOrders.pickupSuffix'
  | 'eatsOrders.loadError'
  | 'eatsOrders.updateError'
  | 'eatsOrders.completePickupError'
  | 'eatsOrders.notePrefix'
  | 'eatsOrders.updating'
  | 'eatsOrders.markPickedUp'
  | 'eatsOrders.markPrefix'
  | 'eatsOrders.empty'
  // Real 5th-localization-pass additions (2026-08-15): DineInScreen -- the real
  // 배민오더-style table/QR in-store ordering screen (QR generator + live table-order
  // queue), a real daily-use screen for any sit-down restaurant merchant.
  | 'dineIn.qrTitle'
  | 'dineIn.qrBody'
  | 'dineIn.tableNumberPlaceholder'
  | 'dineIn.generate'
  | 'dineIn.tablePrefix'
  | 'dineIn.ordersTitle'
  | 'dineIn.connectionError'
  | 'dineIn.updateError'
  | 'dineIn.empty'
  | 'dineIn.statusNew'
  | 'dineIn.statusAccepted'
  | 'dineIn.statusPreparing'
  | 'dineIn.statusServed'
  | 'dineIn.statusCancelled'
  | 'dineIn.actionAccept'
  | 'dineIn.actionStartPreparing'
  | 'dineIn.actionMarkServed'
  | 'dineIn.notePrefix'
  // Real 6th-localization-pass additions (2026-08-15): ReportsScreen -- real
  // settlement/collections reporting a merchant checks for reconciliation and tax
  // filing, and BusinessAccountScreen -- the real business profile/loan-gating screen.
  | 'reports.title'
  | 'reports.rangeSeparator'
  | 'reports.settledSuffix'
  | 'reports.refresh'
  | 'reports.last7Days'
  | 'reports.last30Days'
  | 'reports.fromLabel'
  | 'reports.toLabel'
  | 'reports.apply'
  | 'reports.validationBothDates'
  | 'reports.validationStartBeforeEnd'
  | 'reports.validationMaxRange'
  | 'reports.metricCollections'
  | 'reports.metricGross'
  | 'reports.metricFees'
  | 'reports.metricNet'
  | 'reports.channelsTitle'
  | 'reports.channelsEmpty'
  | 'reports.columnDate'
  | 'reports.columnCollections'
  | 'reports.columnGross'
  | 'reports.columnFees'
  | 'reports.columnNet'
  | 'business.title'
  | 'business.pitchBody'
  | 'business.opening'
  | 'business.open'
  | 'business.loadError'
  | 'business.openError'
  | 'business.balanceLabel'
  | 'business.transactionsTitle'
  | 'business.transactionsEmpty'
  | 'business.moveTitle'
  | 'business.amountPlaceholder'
  | 'business.amountValidationError'
  | 'business.moveError'
  | 'business.toBusiness'
  | 'business.toPersonal';

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
    'eatsOrders.statusPlaced': 'Placed',
    'eatsOrders.statusAccepted': 'Accepted',
    'eatsOrders.statusPreparing': 'Preparing',
    'eatsOrders.statusReadyForPickup': 'Ready for pickup',
    'eatsOrders.statusRiderAssigned': 'Rider on the way',
    'eatsOrders.statusPickedUp': 'Picked up — on the way',
    'eatsOrders.statusDelivered': 'Delivered',
    'eatsOrders.statusCancelled': 'Cancelled — refunded',
    'eatsOrders.pickupSuffix': ' · Pickup',
    'eatsOrders.loadError': 'Could not load your restaurant orders.',
    'eatsOrders.updateError': 'Could not update this order.',
    'eatsOrders.completePickupError': 'Could not complete this pickup.',
    'eatsOrders.notePrefix': 'Note:',
    'eatsOrders.updating': 'Updating…',
    'eatsOrders.markPickedUp': 'Mark picked up',
    'eatsOrders.markPrefix': 'Mark',
    'eatsOrders.empty': 'No Eats orders yet. Orders placed against your restaurant will show up here.',
    'dineIn.qrTitle': 'Table QR codes',
    'dineIn.qrBody': 'Print this and leave it on a table -- a customer scans it to order straight to that table.',
    'dineIn.tableNumberPlaceholder': 'Table number',
    'dineIn.generate': 'Generate',
    'dineIn.tablePrefix': 'Table',
    'dineIn.ordersTitle': 'Table orders',
    'dineIn.connectionError': "Couldn't reach itunda. Check your connection and try again.",
    'dineIn.updateError': "Couldn't update this order. Try again.",
    'dineIn.empty': 'No open table orders right now.',
    'dineIn.statusNew': 'New order',
    'dineIn.statusAccepted': 'Accepted',
    'dineIn.statusPreparing': 'Preparing',
    'dineIn.statusServed': 'Served',
    'dineIn.statusCancelled': 'Cancelled',
    'dineIn.actionAccept': 'Accept order',
    'dineIn.actionStartPreparing': 'Start preparing',
    'dineIn.actionMarkServed': 'Mark served',
    'dineIn.notePrefix': 'Note:',
    'reports.title': 'Collections report',
    'reports.rangeSeparator': 'to',
    'reports.settledSuffix': 'settled collections',
    'reports.refresh': 'Refresh',
    'reports.last7Days': 'Last 7 days',
    'reports.last30Days': 'Last 30 days',
    'reports.fromLabel': 'From',
    'reports.toLabel': 'To',
    'reports.apply': 'Apply',
    'reports.validationBothDates': 'Choose both a start and end date.',
    'reports.validationStartBeforeEnd': 'The start date must be on or before the end date.',
    'reports.validationMaxRange': 'Reports can cover up to 31 days at a time.',
    'reports.metricCollections': 'Collections',
    'reports.metricGross': 'Gross',
    'reports.metricFees': 'Fees',
    'reports.metricNet': 'Net settled',
    'reports.channelsTitle': 'Collection channels',
    'reports.channelsEmpty': 'No settled collections in this range.',
    'reports.columnDate': 'Date',
    'reports.columnCollections': 'Collections',
    'reports.columnGross': 'Gross',
    'reports.columnFees': 'Fees',
    'reports.columnNet': 'Net',
    'business.title': 'Business account',
    'business.pitchBody': "Keep your business money separate from your personal wallet. Your real card/QR collections still settle to your personal wallet as before — move money into your business account whenever you're ready to set it aside.",
    'business.opening': 'Opening…',
    'business.open': 'Open business account',
    'business.loadError': 'Could not load your business account.',
    'business.openError': 'Could not open a business account.',
    'business.balanceLabel': 'Business balance',
    'business.transactionsTitle': 'Business transactions',
    'business.transactionsEmpty': "No business transactions yet — once you send or receive money, it'll show up here.",
    'business.moveTitle': 'Move money',
    'business.amountPlaceholder': 'Amount (RWF)',
    'business.amountValidationError': 'Enter a real amount.',
    'business.moveError': "Couldn't move this money. Check your balance.",
    'business.toBusiness': 'To business',
    'business.toPersonal': 'To personal',
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
    'eatsOrders.statusPlaced': 'Byatanzwe',
    'eatsOrders.statusAccepted': 'Byemewe',
    'eatsOrders.statusPreparing': 'Bitegurwa',
    'eatsOrders.statusReadyForPickup': 'Biteguye gutorwa',
    'eatsOrders.statusRiderAssigned': 'Umutwara ari mu nzira',
    'eatsOrders.statusPickedUp': 'Byatorewe — biri mu nzira',
    'eatsOrders.statusDelivered': 'Byageze',
    'eatsOrders.statusCancelled': 'Byahagaritswe — hasubijwe amafaranga',
    'eatsOrders.pickupSuffix': ' · Gutora',
    'eatsOrders.loadError': 'Ntibishoboka gushakisha itumiza ryawe ry\'ibiryo.',
    'eatsOrders.updateError': 'Ntibishoboka kuvugurura iri tumiza.',
    'eatsOrders.completePickupError': 'Ntibishoboka kurangiza iri torwa.',
    'eatsOrders.notePrefix': 'Icyitonderwa:',
    'eatsOrders.updating': 'Kuvugurura…',
    'eatsOrders.markPickedUp': 'Emeza ko byatorewe',
    'eatsOrders.markPrefix': 'Emeza',
    'eatsOrders.empty': 'Nta itumiza ry\'ibiryo urafite. Itumiza ryakorewe resitora yawe rizagaragara hano.',
    'dineIn.qrTitle': 'QR code z\'ameza',
    'dineIn.qrBody': 'Chapa iyi uyishyire ku meza -- umukiriya ayisikana atumiza akoresheje iryo tebulo.',
    'dineIn.tableNumberPlaceholder': 'Nomero y\'itebulo',
    'dineIn.generate': 'Kora',
    'dineIn.tablePrefix': 'Itebulo',
    'dineIn.ordersTitle': 'Itumiza ry\'ameza',
    'dineIn.connectionError': 'Ntibishoboka kwihuza na itunda. Reba interineti yawe hanyuma ugerageze nanone.',
    'dineIn.updateError': 'Ntibishoboka kuvugurura iri tumiza. Ongera ugerageze.',
    'dineIn.empty': 'Nta itumiza ry\'ameza rifunguye ubu.',
    'dineIn.statusNew': 'Itumiza rishya',
    'dineIn.statusAccepted': 'Byemewe',
    'dineIn.statusPreparing': 'Bitegurwa',
    'dineIn.statusServed': 'Byatanzwe',
    'dineIn.statusCancelled': 'Byahagaritswe',
    'dineIn.actionAccept': 'Emeza itumiza',
    'dineIn.actionStartPreparing': 'Tangira gutegura',
    'dineIn.actionMarkServed': 'Emeza ko byatanzwe',
    'dineIn.notePrefix': 'Icyitonderwa:',
    'reports.title': 'Raporo y\'amafaranga yakiriwe',
    'reports.rangeSeparator': 'kugeza',
    'reports.settledSuffix': 'amafaranga yakiriwe yishyuwe',
    'reports.refresh': 'Vugurura',
    'reports.last7Days': 'Iminsi 7 ishize',
    'reports.last30Days': 'Iminsi 30 ishize',
    'reports.fromLabel': 'Guhera',
    'reports.toLabel': 'Kugeza',
    'reports.apply': 'Emeza',
    'reports.validationBothDates': 'Hitamo itariki y\'itangira n\'iy\'irangira.',
    'reports.validationStartBeforeEnd': 'Itariki y\'itangira igomba kuba mbere cyangwa ingana n\'iy\'irangira.',
    'reports.validationMaxRange': 'Raporo ishobora kugera ku minsi 31 icyarimwe.',
    'reports.metricCollections': 'Amafaranga yakiriwe',
    'reports.metricGross': 'Igiteranyo',
    'reports.metricFees': 'Amafaranga y\'ubuyobozi',
    'reports.metricNet': 'Amafaranga asigaye',
    'reports.channelsTitle': 'Uburyo bwo kwakira amafaranga',
    'reports.channelsEmpty': 'Nta mafaranga yakiriwe muri iki gihe.',
    'reports.columnDate': 'Itariki',
    'reports.columnCollections': 'Amafaranga yakiriwe',
    'reports.columnGross': 'Igiteranyo',
    'reports.columnFees': 'Amafaranga y\'ubuyobozi',
    'reports.columnNet': 'Asigaye',
    'business.title': 'Konti y\'ubucuruzi',
    'business.pitchBody': 'Tandukanya amafaranga y\'ubucuruzi bwawe n\'aya konti yawe bwite. Amafaranga wakira ku ikarita/QR akomeza kujya kuri konti yawe bwite nk\'uko byari bimeze — wimura amafaranga ku konti y\'ubucuruzi igihe cyose ubishaka.',
    'business.opening': 'Gufungura…',
    'business.open': 'Fungura konti y\'ubucuruzi',
    'business.loadError': 'Ntibishoboka gushakisha konti yawe y\'ubucuruzi.',
    'business.openError': 'Ntibishoboka gufungura konti y\'ubucuruzi.',
    'business.balanceLabel': 'Amafaranga y\'ubucuruzi',
    'business.transactionsTitle': 'Ibikorwa by\'ubucuruzi',
    'business.transactionsEmpty': 'Nta bikorwa by\'ubucuruzi urafite — igihe uzohereza cyangwa wakira amafaranga, bizagaragara hano.',
    'business.moveTitle': 'Kwimura amafaranga',
    'business.amountPlaceholder': 'Amafaranga (RWF)',
    'business.amountValidationError': 'Andika amafaranga y\'ukuri.',
    'business.moveError': 'Ntibishoboka kwimura aya mafaranga. Reba amafaranga ufite.',
    'business.toBusiness': 'Ku bucuruzi',
    'business.toPersonal': 'Ku giti cyawe',
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
    'eatsOrders.statusPlaced': 'Passée',
    'eatsOrders.statusAccepted': 'Acceptée',
    'eatsOrders.statusPreparing': 'En préparation',
    'eatsOrders.statusReadyForPickup': 'Prête pour le retrait',
    'eatsOrders.statusRiderAssigned': 'Livreur en route',
    'eatsOrders.statusPickedUp': 'Récupérée — en route',
    'eatsOrders.statusDelivered': 'Livrée',
    'eatsOrders.statusCancelled': 'Annulée — remboursée',
    'eatsOrders.pickupSuffix': ' · Retrait',
    'eatsOrders.loadError': 'Impossible de charger vos commandes de restaurant.',
    'eatsOrders.updateError': 'Impossible de mettre à jour cette commande.',
    'eatsOrders.completePickupError': 'Impossible de finaliser ce retrait.',
    'eatsOrders.notePrefix': 'Remarque :',
    'eatsOrders.updating': 'Mise à jour…',
    'eatsOrders.markPickedUp': 'Marquer comme récupérée',
    'eatsOrders.markPrefix': 'Marquer',
    'eatsOrders.empty': 'Aucune commande Eats pour le moment. Les commandes passées à votre restaurant apparaîtront ici.',
    'dineIn.qrTitle': 'Codes QR des tables',
    'dineIn.qrBody': 'Imprimez-le et laissez-le sur une table -- un client le scanne pour commander directement à cette table.',
    'dineIn.tableNumberPlaceholder': 'Numéro de table',
    'dineIn.generate': 'Générer',
    'dineIn.tablePrefix': 'Table',
    'dineIn.ordersTitle': 'Commandes de table',
    'dineIn.connectionError': "Connexion à itunda impossible. Vérifiez votre connexion et réessayez.",
    'dineIn.updateError': 'Impossible de mettre à jour cette commande. Réessayez.',
    'dineIn.empty': 'Aucune commande de table ouverte pour le moment.',
    'dineIn.statusNew': 'Nouvelle commande',
    'dineIn.statusAccepted': 'Acceptée',
    'dineIn.statusPreparing': 'En préparation',
    'dineIn.statusServed': 'Servie',
    'dineIn.statusCancelled': 'Annulée',
    'dineIn.actionAccept': 'Accepter la commande',
    'dineIn.actionStartPreparing': 'Commencer la préparation',
    'dineIn.actionMarkServed': 'Marquer comme servie',
    'dineIn.notePrefix': 'Remarque :',
    'reports.title': 'Rapport des encaissements',
    'reports.rangeSeparator': 'au',
    'reports.settledSuffix': 'encaissements réglés',
    'reports.refresh': 'Actualiser',
    'reports.last7Days': '7 derniers jours',
    'reports.last30Days': '30 derniers jours',
    'reports.fromLabel': 'Du',
    'reports.toLabel': 'Au',
    'reports.apply': 'Appliquer',
    'reports.validationBothDates': 'Choisissez une date de début et de fin.',
    'reports.validationStartBeforeEnd': 'La date de début doit être antérieure ou égale à la date de fin.',
    'reports.validationMaxRange': 'Un rapport peut couvrir jusqu\'à 31 jours à la fois.',
    'reports.metricCollections': 'Encaissements',
    'reports.metricGross': 'Brut',
    'reports.metricFees': 'Frais',
    'reports.metricNet': 'Net réglé',
    'reports.channelsTitle': 'Canaux d\'encaissement',
    'reports.channelsEmpty': 'Aucun encaissement réglé sur cette période.',
    'reports.columnDate': 'Date',
    'reports.columnCollections': 'Encaissements',
    'reports.columnGross': 'Brut',
    'reports.columnFees': 'Frais',
    'reports.columnNet': 'Net',
    'business.title': 'Compte professionnel',
    'business.pitchBody': "Séparez l'argent de votre entreprise de votre portefeuille personnel. Vos encaissements par carte/QR continuent d'être versés sur votre portefeuille personnel comme avant — transférez de l'argent vers votre compte professionnel quand vous le souhaitez.",
    'business.opening': 'Ouverture…',
    'business.open': 'Ouvrir un compte professionnel',
    'business.loadError': 'Impossible de charger votre compte professionnel.',
    'business.openError': "Impossible d'ouvrir un compte professionnel.",
    'business.balanceLabel': 'Solde professionnel',
    'business.transactionsTitle': 'Transactions professionnelles',
    'business.transactionsEmpty': "Aucune transaction professionnelle pour le moment — dès que vous envoyez ou recevez de l'argent, elle apparaîtra ici.",
    'business.moveTitle': "Transférer de l'argent",
    'business.amountPlaceholder': 'Montant (RWF)',
    'business.amountValidationError': 'Saisissez un montant valide.',
    'business.moveError': 'Impossible de transférer cet argent. Vérifiez votre solde.',
    'business.toBusiness': 'Vers professionnel',
    'business.toPersonal': 'Vers personnel',
  },
};
