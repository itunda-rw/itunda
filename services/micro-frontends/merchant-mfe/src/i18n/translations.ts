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
  | 'business.toPersonal'
  // Real 7th-localization-pass additions (2026-08-15): VendorCashAdvanceScreen -- the
  // real Isoko Vendor Cash Advance flow. First screen in this MFE needing real
  // {{placeholder}} interpolation (see I18nContext.tsx's own doc comment).
  | 'vendorAdvance.title'
  | 'vendorAdvance.pitchBody'
  | 'vendorAdvance.loadError'
  | 'vendorAdvance.applyError'
  | 'vendorAdvance.disburseError'
  | 'vendorAdvance.amountValidationError'
  | 'vendorAdvance.repayError'
  | 'vendorAdvance.eligibleFor'
  | 'vendorAdvance.offerBody'
  | 'vendorAdvance.applying'
  | 'vendorAdvance.applyButton'
  | 'vendorAdvance.notEligibleBody'
  | 'vendorAdvance.readyToDisburseBody'
  | 'vendorAdvance.disbursing'
  | 'vendorAdvance.disburseButton'
  | 'vendorAdvance.remainingOwedLabel'
  | 'vendorAdvance.progressBody'
  | 'vendorAdvance.progressBodyWithLastCollection'
  | 'vendorAdvance.repayEarlyTitle'
  | 'vendorAdvance.amountPlaceholder'
  | 'vendorAdvance.repaying'
  | 'vendorAdvance.repayButton'
  // Real 8th-localization-pass additions (2026-08-15): BillingScreen -- the real
  // Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing plan management.
  | 'billing.loadError'
  | 'billing.plansTitle'
  | 'billing.plansBody'
  | 'billing.loading'
  | 'billing.plansEmpty'
  | 'billing.createTitle'
  | 'billing.planNameLabel'
  | 'billing.planNamePlaceholder'
  | 'billing.descriptionLabel'
  | 'billing.descriptionPlaceholder'
  | 'billing.amountLabel'
  | 'billing.intervalLabel'
  | 'billing.createError'
  | 'billing.creating'
  | 'billing.createButton'
  | 'billing.statusActive'
  | 'billing.statusDeactivated'
  | 'billing.everyDaySingular'
  | 'billing.everyDaysPlural'
  | 'billing.deactivateError'
  | 'billing.deactivateButton'
  // Real 9th-localization-pass additions (2026-08-15): CouponsScreen -- real merchant
  // coupons + 단골 loyalty gating, create/list/deactivate half.
  | 'coupons.loadError'
  | 'coupons.title'
  | 'coupons.body'
  | 'coupons.loading'
  | 'coupons.empty'
  | 'coupons.createTitle'
  | 'coupons.titleLabel'
  | 'coupons.titlePlaceholder'
  | 'coupons.descriptionLabel'
  | 'coupons.descriptionPlaceholder'
  | 'coupons.discountTypeLabel'
  | 'coupons.discountTypePercent'
  | 'coupons.discountTypeFixed'
  | 'coupons.percentLabel'
  | 'coupons.fixedAmountLabel'
  | 'coupons.expiresLabel'
  | 'coupons.regularsOnlyLabel'
  | 'coupons.createError'
  | 'coupons.creating'
  | 'coupons.createButton'
  | 'coupons.statusActive'
  | 'coupons.statusDeactivated'
  | 'coupons.percentOff'
  | 'coupons.fixedOff'
  | 'coupons.regularsOnlySuffix'
  | 'coupons.expiresSuffix'
  | 'coupons.deactivateError'
  | 'coupons.deactivateButton'
  // Real 10th-localization-pass additions (2026-08-15): AdsScreen -- real
  // 당근(Karrot) 반경 타기팅-style radius-targeted local ads.
  | 'ads.loadError'
  | 'ads.activeTitle'
  | 'ads.activeBody'
  | 'ads.geolocationUnsupported'
  | 'ads.locationSaveError'
  | 'ads.locationFetchError'
  | 'ads.locationSetupTitle'
  | 'ads.locationSetupBody'
  | 'ads.gettingLocation'
  | 'ads.shareLocationButton'
  | 'ads.createTitle'
  | 'ads.titleLabel'
  | 'ads.titlePlaceholder'
  | 'ads.descriptionLabel'
  | 'ads.descriptionPlaceholder'
  | 'ads.radiusLabel'
  | 'ads.durationLabel'
  | 'ads.durationOption'
  | 'ads.createError'
  | 'ads.chargeNotice'
  | 'ads.starting'
  | 'ads.payAndRunButton'
  // Real 11th-localization-pass additions (2026-08-15): PayrollScreen -- real B2B
  // payroll, genuine wallet-to-wallet money movement (not a demo).
  | 'payroll.loadError'
  | 'payroll.addEmployeeTitle'
  | 'payroll.addEmployeeBody'
  | 'payroll.phoneNumberLabel'
  | 'payroll.monthlySalaryLabel'
  | 'payroll.addError'
  | 'payroll.adding'
  | 'payroll.addButton'
  | 'payroll.runError'
  | 'payroll.retryButton'
  | 'payroll.loading'
  | 'payroll.rosterTitle'
  | 'payroll.running'
  | 'payroll.runPayrollButton'
  | 'payroll.rosterEmpty'
  | 'payroll.columnEmployee'
  | 'payroll.columnMonthlySalary'
  | 'payroll.removeButton'
  | 'payroll.paidTitle'
  | 'payroll.employeesPaidCount'
  | 'payroll.backToRoster'
  // Real 12th-localization-pass additions (2026-08-15): BookingScreen -- real local-
  // business appointment booking, owner side (weekly availability + booking queue).
  | 'booking.dayMon'
  | 'booking.dayTue'
  | 'booking.dayWed'
  | 'booking.dayThu'
  | 'booking.dayFri'
  | 'booking.daySat'
  | 'booking.daySun'
  | 'booking.availabilityTitle'
  | 'booking.availabilityBody'
  | 'booking.availabilitySaveError'
  | 'booking.availabilityEmpty'
  | 'booking.removeButton'
  | 'booking.timeValidationError'
  | 'booking.saving'
  | 'booking.addWindowButton'
  | 'booking.bookingsTitle'
  | 'booking.loadError'
  | 'booking.empty'
  | 'booking.notePrefix'
  | 'booking.confirmButton'
  | 'booking.declineButton'
  | 'booking.updateError'
  | 'booking.updating'
  | 'booking.markCompletedButton'
  | 'booking.statusRequested'
  | 'booking.statusConfirmed'
  | 'booking.statusDeclined'
  | 'booking.statusCancelled'
  | 'booking.statusCompleted';

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
    'vendorAdvance.title': 'Isoko Vendor Cash Advance',
    'vendorAdvance.pitchBody': "A cash advance against your own real itunda sales history. There's no fixed repayment schedule -- itunda automatically collects a share of your real QR/card sales here each day until it's paid off. This can only see and collect sales that actually go through itunda; cash you collect off-platform isn't part of this at all.",
    'vendorAdvance.loadError': 'Could not load your vendor cash advance.',
    'vendorAdvance.applyError': "Couldn't apply for a vendor cash advance.",
    'vendorAdvance.disburseError': "Couldn't disburse this advance.",
    'vendorAdvance.amountValidationError': 'Enter a real amount.',
    'vendorAdvance.repayError': "Couldn't repay this advance. Check your balance.",
    'vendorAdvance.eligibleFor': "You're eligible for",
    'vendorAdvance.offerBody': 'One-time fee: {{feeAmount}} RWF -- itunda then collects {{ratePercent}}% of your real daily itunda-collected sales here until {{totalRepay}} RWF is repaid. Based on your real average of {{avgDaily}} RWF/day over your last {{tradingDays}} real trading days.',
    'vendorAdvance.applying': 'Applying…',
    'vendorAdvance.applyButton': 'Apply for this advance',
    'vendorAdvance.notEligibleBody': 'Not eligible yet -- {{reason}}. Keep collecting real QR/card sales through itunda and check back.',
    'vendorAdvance.readyToDisburseBody': 'Your {{principalAmount}} RWF advance was approved and is ready to disburse to your wallet.',
    'vendorAdvance.disbursing': 'Disbursing…',
    'vendorAdvance.disburseButton': 'Disburse to my wallet',
    'vendorAdvance.remainingOwedLabel': 'Remaining owed',
    'vendorAdvance.progressBody': 'of {{totalOwed}} RWF total owed -- {{ratePercent}}% of your real daily itunda sales is collected automatically.',
    'vendorAdvance.progressBodyWithLastCollection': 'of {{totalOwed}} RWF total owed -- {{ratePercent}}% of your real daily itunda sales is collected automatically, last collected {{lastCollectionDate}}.',
    'vendorAdvance.repayEarlyTitle': 'Repay early',
    'vendorAdvance.amountPlaceholder': 'Amount (RWF)',
    'vendorAdvance.repaying': 'Repaying…',
    'vendorAdvance.repayButton': 'Repay now',
    'billing.loadError': 'Could not load your billing plans.',
    'billing.plansTitle': 'Your billing plans',
    'billing.plansBody': 'A customer who subscribes is charged immediately, then again automatically every cycle until they cancel.',
    'billing.loading': 'Loading…',
    'billing.plansEmpty': 'No billing plans yet — use the form above to create your first one.',
    'billing.createTitle': 'Create a billing plan',
    'billing.planNameLabel': 'Plan name',
    'billing.planNamePlaceholder': 'Monthly coffee subscription',
    'billing.descriptionLabel': 'Description (optional)',
    'billing.descriptionPlaceholder': 'One bag of beans, delivered monthly',
    'billing.amountLabel': 'Amount (RWF)',
    'billing.intervalLabel': 'Every (days)',
    'billing.createError': 'Could not create this plan.',
    'billing.creating': 'Creating…',
    'billing.createButton': 'Create plan',
    'billing.statusActive': 'Active',
    'billing.statusDeactivated': 'Deactivated',
    'billing.everyDaySingular': '{{amount}} RWF every {{days}} day',
    'billing.everyDaysPlural': '{{amount}} RWF every {{days}} days',
    'billing.deactivateError': 'Could not deactivate this plan.',
    'billing.deactivateButton': 'Deactivate',
    'coupons.loadError': 'Could not load your coupons.',
    'coupons.title': 'Your coupons',
    'coupons.body': "A customer applies a coupon when paying by code — it's redeemed once per customer.",
    'coupons.loading': 'Loading…',
    'coupons.empty': 'No coupons yet — use the form above to create your first one.',
    'coupons.createTitle': 'Create a coupon',
    'coupons.titleLabel': 'Title',
    'coupons.titlePlaceholder': '10% off your next visit',
    'coupons.descriptionLabel': 'Description (optional)',
    'coupons.descriptionPlaceholder': 'Valid on any purchase',
    'coupons.discountTypeLabel': 'Discount type',
    'coupons.discountTypePercent': 'Percent off',
    'coupons.discountTypeFixed': 'Fixed amount off',
    'coupons.percentLabel': 'Percent (1-100)',
    'coupons.fixedAmountLabel': 'Amount (RWF)',
    'coupons.expiresLabel': 'Expires (optional)',
    'coupons.regularsOnlyLabel': 'Reserve for regular customers only (3+ past payments)',
    'coupons.createError': 'Could not create this coupon.',
    'coupons.creating': 'Creating…',
    'coupons.createButton': 'Create coupon',
    'coupons.statusActive': 'Active',
    'coupons.statusDeactivated': 'Deactivated',
    'coupons.percentOff': '{{value}}% off',
    'coupons.fixedOff': '{{value}} RWF off',
    'coupons.regularsOnlySuffix': ' · Regulars only',
    'coupons.expiresSuffix': ' · Expires {{date}}',
    'coupons.deactivateError': 'Could not deactivate this coupon.',
    'coupons.deactivateButton': 'Deactivate',
    'ads.loadError': 'Could not load your business account.',
    'ads.activeTitle': 'Your active ad',
    'ads.activeBody': '{{radius}}m radius · runs until {{date}}',
    'ads.geolocationUnsupported': 'This browser does not support real location access.',
    'ads.locationSaveError': 'Could not save your location.',
    'ads.locationFetchError': 'Could not get your real location. Check your browser permissions.',
    'ads.locationSetupTitle': 'Set your business location',
    'ads.locationSetupBody': "A radius-targeted ad needs your business's real location to match nearby customers.",
    'ads.gettingLocation': 'Getting location…',
    'ads.shareLocationButton': 'Share my location',
    'ads.createTitle': 'Run a local ad',
    'ads.titleLabel': 'Title',
    'ads.titlePlaceholder': 'Fresh bread every morning',
    'ads.descriptionLabel': 'Description (optional)',
    'ads.descriptionPlaceholder': 'Stop by for 10% off this week',
    'ads.radiusLabel': 'Radius',
    'ads.durationLabel': 'Duration',
    'ads.durationOption': '{{days}} days — {{price}} RWF',
    'ads.createError': 'Could not create this ad.',
    'ads.chargeNotice': '{{price}} RWF will be charged from your wallet. If you already have an active ad, this extends it.',
    'ads.starting': 'Starting…',
    'ads.payAndRunButton': 'Pay {{price}} RWF & run ad',
    'payroll.loadError': 'Could not load the payroll roster.',
    'payroll.addEmployeeTitle': 'Add an employee',
    'payroll.addEmployeeBody': "Must be an existing Itunda user's phone number — payroll pays directly into their wallet.",
    'payroll.phoneNumberLabel': 'Phone number',
    'payroll.monthlySalaryLabel': 'Monthly salary (RWF)',
    'payroll.addError': 'Could not add this employee.',
    'payroll.adding': 'Adding…',
    'payroll.addButton': 'Add',
    'payroll.runError': 'Could not run payroll.',
    'payroll.retryButton': 'Retry',
    'payroll.loading': 'Loading…',
    'payroll.rosterTitle': 'Roster ({{count}})',
    'payroll.running': 'Running…',
    'payroll.runPayrollButton': 'Run payroll ({{total}} RWF)',
    'payroll.rosterEmpty': 'No employees on the roster yet.',
    'payroll.columnEmployee': 'Employee',
    'payroll.columnMonthlySalary': 'Monthly salary',
    'payroll.removeButton': 'Remove',
    'payroll.paidTitle': 'Payroll paid',
    'payroll.employeesPaidCount': '{{count}} employees paid',
    'payroll.backToRoster': 'Back to roster',
    'booking.dayMon': 'Mon',
    'booking.dayTue': 'Tue',
    'booking.dayWed': 'Wed',
    'booking.dayThu': 'Thu',
    'booking.dayFri': 'Fri',
    'booking.daySat': 'Sat',
    'booking.daySun': 'Sun',
    'booking.availabilityTitle': 'Weekly availability',
    'booking.availabilityBody': 'Customers can only request an appointment inside these windows.',
    'booking.availabilitySaveError': "Couldn't save your availability. Try again.",
    'booking.availabilityEmpty': 'No availability set yet — add a window below.',
    'booking.removeButton': 'Remove',
    'booking.timeValidationError': 'Enter a real start time before the end time (HH:mm).',
    'booking.saving': 'Saving…',
    'booking.addWindowButton': 'Add window',
    'booking.bookingsTitle': 'Bookings',
    'booking.loadError': 'Could not load your bookings.',
    'booking.empty': 'No open bookings right now.',
    'booking.notePrefix': 'Note:',
    'booking.confirmButton': 'Confirm',
    'booking.declineButton': 'Decline',
    'booking.updateError': "Couldn't update this booking.",
    'booking.updating': 'Updating…',
    'booking.markCompletedButton': 'Mark completed',
    'booking.statusRequested': 'Requested',
    'booking.statusConfirmed': 'Confirmed',
    'booking.statusDeclined': 'Declined',
    'booking.statusCancelled': 'Cancelled',
    'booking.statusCompleted': 'Completed',
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
    'vendorAdvance.title': 'Isoko Vendor Cash Advance',
    'vendorAdvance.pitchBody': 'Inguzanyo ishingiye ku mateka y\'ukuri y\'ibyo ucuruza kuri itunda. Nta gahunda ihamye yo kwishyura -- itunda ifata igice cy\'amafaranga wakiriye ku QR/ikarita buri munsi kugeza yishyuwe. Iyi nguzanyo ireba gusa amafaranga anyura kuri itunda; amafaranga ukusanya hanze ya itunda ntabwo agize uruhare muri iyi.',
    'vendorAdvance.loadError': 'Ntibishoboka gushakisha inguzanyo yawe.',
    'vendorAdvance.applyError': 'Ntibishoboka gusaba iyi nguzanyo.',
    'vendorAdvance.disburseError': 'Ntibishoboka koherereza iyi nguzanyo.',
    'vendorAdvance.amountValidationError': 'Andika amafaranga y\'ukuri.',
    'vendorAdvance.repayError': 'Ntibishoboka kwishyura iyi nguzanyo. Reba amafaranga ufite.',
    'vendorAdvance.eligibleFor': 'Ushobora kubona',
    'vendorAdvance.offerBody': 'Amafaranga y\'inshuro imwe: {{feeAmount}} RWF -- itunda hanyuma ifata {{ratePercent}}% by\'ibyo ucuruza kuri itunda buri munsi kugeza {{totalRepay}} RWF yishyuwe. Bishingiye ku ruhare rwawe rwa buri munsi rwa {{avgDaily}} RWF mu minsi {{tradingDays}} ushize wacuruzaga.',
    'vendorAdvance.applying': 'Gusaba…',
    'vendorAdvance.applyButton': 'Saba iyi nguzanyo',
    'vendorAdvance.notEligibleBody': 'Ntabwo wemererwa ubu -- {{reason}}. Komeza gucuruza kuri itunda hanyuma ugaruke.',
    'vendorAdvance.readyToDisburseBody': 'Inguzanyo yawe ya {{principalAmount}} RWF yemewe kandi iteguye koherezwa kuri konti yawe.',
    'vendorAdvance.disbursing': 'Kohereza…',
    'vendorAdvance.disburseButton': 'Ohereza kuri konti yanjye',
    'vendorAdvance.remainingOwedLabel': 'Asigaye kwishyurwa',
    'vendorAdvance.progressBody': 'kuri {{totalOwed}} RWF byose ubwiyunge -- {{ratePercent}}% by\'ibyo ucuruza buri munsi bifatwa mu buryo bwikora.',
    'vendorAdvance.progressBodyWithLastCollection': 'kuri {{totalOwed}} RWF byose ubwiyunge -- {{ratePercent}}% by\'ibyo ucuruza buri munsi bifatwa mu buryo bwikora, byanyuma byafashwe {{lastCollectionDate}}.',
    'vendorAdvance.repayEarlyTitle': 'Kwishyura mbere y\'igihe',
    'vendorAdvance.amountPlaceholder': 'Amafaranga (RWF)',
    'vendorAdvance.repaying': 'Kwishyura…',
    'vendorAdvance.repayButton': 'Ishyura nonaha',
    'billing.loadError': 'Ntibishoboka gushakisha gahunda zawe zo kwishyura.',
    'billing.plansTitle': 'Gahunda zawe zo kwishyura',
    'billing.plansBody': 'Umukiriya wiyandikisha yishyura ako kanya, hanyuma yishyura buri gihe mu buryo bwikora kugeza ahagaritse.',
    'billing.loading': 'Gushakisha…',
    'billing.plansEmpty': 'Nta gahunda yo kwishyura urafite — koresha ifishi hejuru wandike iya mbere.',
    'billing.createTitle': 'Kora gahunda yo kwishyura',
    'billing.planNameLabel': 'Izina rya gahunda',
    'billing.planNamePlaceholder': 'Kwiyandikisha kwa kawa buri kwezi',
    'billing.descriptionLabel': 'Ibisobanuro (bitari ngombwa)',
    'billing.descriptionPlaceholder': 'Isaho rimwe ry\'imbuto, ritangwa buri kwezi',
    'billing.amountLabel': 'Amafaranga (RWF)',
    'billing.intervalLabel': 'Buri (iminsi)',
    'billing.createError': 'Ntibishoboka gukora iyi gahunda.',
    'billing.creating': 'Gukora…',
    'billing.createButton': 'Kora gahunda',
    'billing.statusActive': 'Ikora',
    'billing.statusDeactivated': 'Yahagaritswe',
    'billing.everyDaySingular': '{{amount}} RWF buri munsi {{days}}',
    'billing.everyDaysPlural': '{{amount}} RWF buri minsi {{days}}',
    'billing.deactivateError': 'Ntibishoboka guhagarika iyi gahunda.',
    'billing.deactivateButton': 'Hagarika',
    'coupons.loadError': 'Ntibishoboka gushakisha amakuponi yawe.',
    'coupons.title': 'Amakuponi yawe',
    'coupons.body': 'Umukiriya akoresha kuponi igihe yishyura akoresheje kode — ikoreshwa rimwe kuri buri mukiriya.',
    'coupons.loading': 'Gushakisha…',
    'coupons.empty': 'Nta kuponi ufite — koresha ifishi hejuru wandike iya mbere.',
    'coupons.createTitle': 'Kora kuponi',
    'coupons.titleLabel': 'Umutwe',
    'coupons.titlePlaceholder': 'Igabanuka rya 10% ku ruzuma rwawe rukurikira',
    'coupons.descriptionLabel': 'Ibisobanuro (bitari ngombwa)',
    'coupons.descriptionPlaceholder': 'Ikora kuri buri kiguzi',
    'coupons.discountTypeLabel': 'Ubwoko bw\'igabanuka',
    'coupons.discountTypePercent': 'Igabanuka rya %',
    'coupons.discountTypeFixed': 'Igabanuka ry\'amafaranga ahamye',
    'coupons.percentLabel': 'Ijanisha (1-100)',
    'coupons.fixedAmountLabel': 'Amafaranga (RWF)',
    'coupons.expiresLabel': 'Irangira (bitari ngombwa)',
    'coupons.regularsOnlyLabel': 'Byemererwa gusa abakiriya basanzwe (kwishyura inshuro 3+)',
    'coupons.createError': 'Ntibishoboka gukora iyi kuponi.',
    'coupons.creating': 'Gukora…',
    'coupons.createButton': 'Kora kuponi',
    'coupons.statusActive': 'Ikora',
    'coupons.statusDeactivated': 'Yahagaritswe',
    'coupons.percentOff': 'Igabanuka rya {{value}}%',
    'coupons.fixedOff': 'Igabanuka rya {{value}} RWF',
    'coupons.regularsOnlySuffix': ' · Abakiriya basanzwe gusa',
    'coupons.expiresSuffix': ' · Irangira {{date}}',
    'coupons.deactivateError': 'Ntibishoboka guhagarika iyi kuponi.',
    'coupons.deactivateButton': 'Hagarika',
    'ads.loadError': 'Ntibishoboka gushakisha konti yawe y\'ubucuruzi.',
    'ads.activeTitle': 'Itangazo ryawe rikora',
    'ads.activeBody': 'Uburebure bwa m {{radius}} · rikomeza kugeza {{date}}',
    'ads.geolocationUnsupported': 'Iyi porogaramu ntabwo ishoboye kubona aho uri.',
    'ads.locationSaveError': 'Ntibishoboka kubika aho uri.',
    'ads.locationFetchError': 'Ntibishoboka kumenya aho uri. Reba uburenganzira bwa porogaramu yawe.',
    'ads.locationSetupTitle': 'Shyiraho aho ubucuruzi bwawe buherereye',
    'ads.locationSetupBody': 'Itangazo rigenewe abantu bo hafi rikeneye aho ubucuruzi bwawe buherereye kugira ngo rigere ku bakiriya bo hafi.',
    'ads.gettingLocation': 'Kumenya aho uri…',
    'ads.shareLocationButton': 'Menyekanisha aho ndi',
    'ads.createTitle': 'Tangiza itangazo ry\'aho uri',
    'ads.titleLabel': 'Umutwe',
    'ads.titlePlaceholder': 'Umugati mushya buri gitondo',
    'ads.descriptionLabel': 'Ibisobanuro (bitari ngombwa)',
    'ads.descriptionPlaceholder': 'Ngwino ubone igabanuka rya 10% iki cyumweru',
    'ads.radiusLabel': 'Uburebure',
    'ads.durationLabel': 'Igihe',
    'ads.durationOption': 'Iminsi {{days}} — {{price}} RWF',
    'ads.createError': 'Ntibishoboka gukora iri tangazo.',
    'ads.chargeNotice': 'Amafaranga {{price}} RWF azakurwa kuri konti yawe. Niba usanzwe ufite itangazo rikora, iri rirongera igihe.',
    'ads.starting': 'Gutangira…',
    'ads.payAndRunButton': 'Ishyura {{price}} RWF utangize itangazo',
    'payroll.loadError': 'Ntibishoboka gushakisha urutonde rw\'abakozi.',
    'payroll.addEmployeeTitle': 'Ongeraho umukozi',
    'payroll.addEmployeeBody': 'Igomba kuba nomero ya telefoni y\'ukoresha itunda usanzweho — umushahara ujya ako kanya kuri konti ye.',
    'payroll.phoneNumberLabel': 'Nomero ya telefoni',
    'payroll.monthlySalaryLabel': 'Umushahara wa buri kwezi (RWF)',
    'payroll.addError': 'Ntibishoboka kongeraho uyu mukozi.',
    'payroll.adding': 'Kongeraho…',
    'payroll.addButton': 'Ongeraho',
    'payroll.runError': 'Ntibishoboka gutanga imishahara.',
    'payroll.retryButton': 'Ongera ugerageze',
    'payroll.loading': 'Gushakisha…',
    'payroll.rosterTitle': 'Urutonde ({{count}})',
    'payroll.running': 'Birimo gutangwa…',
    'payroll.runPayrollButton': 'Tanga imishahara ({{total}} RWF)',
    'payroll.rosterEmpty': 'Nta mukozi uri ku rutonde.',
    'payroll.columnEmployee': 'Umukozi',
    'payroll.columnMonthlySalary': 'Umushahara wa buri kwezi',
    'payroll.removeButton': 'Kuraho',
    'payroll.paidTitle': 'Imishahara yatanzwe',
    'payroll.employeesPaidCount': 'Abakozi {{count}} bishyuwe',
    'payroll.backToRoster': 'Subira ku rutonde',
    'booking.dayMon': 'Mbe',
    'booking.dayTue': 'Kab',
    'booking.dayWed': 'Gtu',
    'booking.dayThu': 'Kan',
    'booking.dayFri': 'Gnu',
    'booking.daySat': 'Gnd',
    'booking.daySun': 'Cyu',
    'booking.availabilityTitle': 'Igihe ukora buri cyumweru',
    'booking.availabilityBody': 'Abakiriya bashobora gusaba gahunda gusa muri ibi bihe.',
    'booking.availabilitySaveError': 'Ntibishoboka kubika igihe wemera. Ongera ugerageze.',
    'booking.availabilityEmpty': 'Nta gihe washyizeho — ongeraho hepfo.',
    'booking.removeButton': 'Kuraho',
    'booking.timeValidationError': 'Andika igihe cy\'itangira mbere y\'iy\'irangira (HH:mm).',
    'booking.saving': 'Kubika…',
    'booking.addWindowButton': 'Ongeraho igihe',
    'booking.bookingsTitle': 'Gahunda',
    'booking.loadError': 'Ntibishoboka gushakisha gahunda zawe.',
    'booking.empty': 'Nta gahunda ifunguye ubu.',
    'booking.notePrefix': 'Icyitonderwa:',
    'booking.confirmButton': 'Emeza',
    'booking.declineButton': 'Anga',
    'booking.updateError': 'Ntibishoboka kuvugurura iyi gahunda.',
    'booking.updating': 'Kuvugurura…',
    'booking.markCompletedButton': 'Emeza ko yarangiye',
    'booking.statusRequested': 'Yasabwe',
    'booking.statusConfirmed': 'Yemejwe',
    'booking.statusDeclined': 'Yanzwe',
    'booking.statusCancelled': 'Yahagaritswe',
    'booking.statusCompleted': 'Yarangiye',
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
    'vendorAdvance.title': 'Avance de trésorerie Isoko',
    'vendorAdvance.pitchBody': "Une avance de trésorerie basée sur votre propre historique de ventes itunda. Il n'y a pas d'échéancier de remboursement fixe -- itunda prélève automatiquement une part de vos ventes QR/carte réelles chaque jour jusqu'à remboursement complet. Seules les ventes qui passent réellement par itunda sont concernées ; l'argent collecté en dehors de la plateforme n'en fait pas partie.",
    'vendorAdvance.loadError': 'Impossible de charger votre avance de trésorerie.',
    'vendorAdvance.applyError': "Impossible de faire une demande d'avance de trésorerie.",
    'vendorAdvance.disburseError': 'Impossible de débloquer cette avance.',
    'vendorAdvance.amountValidationError': 'Saisissez un montant valide.',
    'vendorAdvance.repayError': 'Impossible de rembourser cette avance. Vérifiez votre solde.',
    'vendorAdvance.eligibleFor': 'Vous êtes éligible à',
    'vendorAdvance.offerBody': 'Frais unique : {{feeAmount}} RWF -- itunda prélève ensuite {{ratePercent}}% de vos ventes quotidiennes réelles via itunda jusqu\'au remboursement de {{totalRepay}} RWF. Basé sur votre moyenne réelle de {{avgDaily}} RWF/jour sur vos {{tradingDays}} derniers jours d\'activité réels.',
    'vendorAdvance.applying': 'Envoi en cours…',
    'vendorAdvance.applyButton': 'Demander cette avance',
    'vendorAdvance.notEligibleBody': "Pas encore éligible -- {{reason}}. Continuez à encaisser de vraies ventes QR/carte via itunda et revenez plus tard.",
    'vendorAdvance.readyToDisburseBody': 'Votre avance de {{principalAmount}} RWF a été approuvée et est prête à être versée sur votre portefeuille.',
    'vendorAdvance.disbursing': 'Déblocage en cours…',
    'vendorAdvance.disburseButton': 'Verser sur mon portefeuille',
    'vendorAdvance.remainingOwedLabel': 'Solde restant dû',
    'vendorAdvance.progressBody': 'sur {{totalOwed}} RWF au total dus -- {{ratePercent}}% de vos ventes quotidiennes réelles via itunda est prélevé automatiquement.',
    'vendorAdvance.progressBodyWithLastCollection': 'sur {{totalOwed}} RWF au total dus -- {{ratePercent}}% de vos ventes quotidiennes réelles via itunda est prélevé automatiquement, dernier prélèvement le {{lastCollectionDate}}.',
    'vendorAdvance.repayEarlyTitle': 'Rembourser par anticipation',
    'vendorAdvance.amountPlaceholder': 'Montant (RWF)',
    'vendorAdvance.repaying': 'Remboursement en cours…',
    'vendorAdvance.repayButton': 'Rembourser maintenant',
    'billing.loadError': 'Impossible de charger vos formules de facturation.',
    'billing.plansTitle': 'Vos formules de facturation',
    'billing.plansBody': "Un client qui s'abonne est débité immédiatement, puis à nouveau automatiquement à chaque cycle jusqu'à annulation.",
    'billing.loading': 'Chargement…',
    'billing.plansEmpty': "Aucune formule pour le moment — utilisez le formulaire ci-dessus pour créer la première.",
    'billing.createTitle': 'Créer une formule de facturation',
    'billing.planNameLabel': 'Nom de la formule',
    'billing.planNamePlaceholder': 'Abonnement café mensuel',
    'billing.descriptionLabel': 'Description (facultatif)',
    'billing.descriptionPlaceholder': 'Un sac de grains, livré chaque mois',
    'billing.amountLabel': 'Montant (RWF)',
    'billing.intervalLabel': 'Tous les (jours)',
    'billing.createError': 'Impossible de créer cette formule.',
    'billing.creating': 'Création en cours…',
    'billing.createButton': 'Créer la formule',
    'billing.statusActive': 'Active',
    'billing.statusDeactivated': 'Désactivée',
    'billing.everyDaySingular': '{{amount}} RWF tous les {{days}} jour',
    'billing.everyDaysPlural': '{{amount}} RWF tous les {{days}} jours',
    'billing.deactivateError': 'Impossible de désactiver cette formule.',
    'billing.deactivateButton': 'Désactiver',
    'coupons.loadError': 'Impossible de charger vos coupons.',
    'coupons.title': 'Vos coupons',
    'coupons.body': "Un client applique un coupon en payant par code — il est utilisé une seule fois par client.",
    'coupons.loading': 'Chargement…',
    'coupons.empty': 'Aucun coupon pour le moment — utilisez le formulaire ci-dessus pour créer le premier.',
    'coupons.createTitle': 'Créer un coupon',
    'coupons.titleLabel': 'Titre',
    'coupons.titlePlaceholder': '10% de réduction sur votre prochaine visite',
    'coupons.descriptionLabel': 'Description (facultatif)',
    'coupons.descriptionPlaceholder': 'Valable sur tout achat',
    'coupons.discountTypeLabel': 'Type de remise',
    'coupons.discountTypePercent': 'Pourcentage de réduction',
    'coupons.discountTypeFixed': 'Montant fixe de réduction',
    'coupons.percentLabel': 'Pourcentage (1-100)',
    'coupons.fixedAmountLabel': 'Montant (RWF)',
    'coupons.expiresLabel': 'Expiration (facultatif)',
    'coupons.regularsOnlyLabel': 'Réserver aux clients réguliers uniquement (3+ paiements passés)',
    'coupons.createError': 'Impossible de créer ce coupon.',
    'coupons.creating': 'Création en cours…',
    'coupons.createButton': 'Créer le coupon',
    'coupons.statusActive': 'Actif',
    'coupons.statusDeactivated': 'Désactivé',
    'coupons.percentOff': '{{value}}% de réduction',
    'coupons.fixedOff': '{{value}} RWF de réduction',
    'coupons.regularsOnlySuffix': ' · Clients réguliers uniquement',
    'coupons.expiresSuffix': ' · Expire le {{date}}',
    'coupons.deactivateError': 'Impossible de désactiver ce coupon.',
    'coupons.deactivateButton': 'Désactiver',
    'ads.loadError': 'Impossible de charger votre compte professionnel.',
    'ads.activeTitle': 'Votre publicité active',
    'ads.activeBody': 'Rayon de {{radius}} m · active jusqu\'au {{date}}',
    'ads.geolocationUnsupported': "Ce navigateur ne prend pas en charge l'accès réel à la position.",
    'ads.locationSaveError': 'Impossible d\'enregistrer votre position.',
    'ads.locationFetchError': "Impossible d'obtenir votre position réelle. Vérifiez les autorisations de votre navigateur.",
    'ads.locationSetupTitle': 'Définissez la position de votre entreprise',
    'ads.locationSetupBody': "Une publicité ciblée par rayon a besoin de la position réelle de votre entreprise pour toucher les clients à proximité.",
    'ads.gettingLocation': 'Récupération de la position…',
    'ads.shareLocationButton': 'Partager ma position',
    'ads.createTitle': 'Lancer une publicité locale',
    'ads.titleLabel': 'Titre',
    'ads.titlePlaceholder': 'Pain frais chaque matin',
    'ads.descriptionLabel': 'Description (facultatif)',
    'ads.descriptionPlaceholder': 'Passez profiter de 10% de réduction cette semaine',
    'ads.radiusLabel': 'Rayon',
    'ads.durationLabel': 'Durée',
    'ads.durationOption': '{{days}} jours — {{price}} RWF',
    'ads.createError': 'Impossible de créer cette publicité.',
    'ads.chargeNotice': '{{price}} RWF seront débités de votre portefeuille. Si vous avez déjà une publicité active, celle-ci sera prolongée.',
    'ads.starting': 'Démarrage…',
    'ads.payAndRunButton': 'Payer {{price}} RWF et lancer',
    'payroll.loadError': 'Impossible de charger la liste du personnel.',
    'payroll.addEmployeeTitle': 'Ajouter un employé',
    'payroll.addEmployeeBody': "Doit être le numéro de téléphone d'un utilisateur itunda existant — la paie est versée directement sur son portefeuille.",
    'payroll.phoneNumberLabel': 'Numéro de téléphone',
    'payroll.monthlySalaryLabel': 'Salaire mensuel (RWF)',
    'payroll.addError': 'Impossible d\'ajouter cet employé.',
    'payroll.adding': 'Ajout en cours…',
    'payroll.addButton': 'Ajouter',
    'payroll.runError': 'Impossible de traiter la paie.',
    'payroll.retryButton': 'Réessayer',
    'payroll.loading': 'Chargement…',
    'payroll.rosterTitle': 'Personnel ({{count}})',
    'payroll.running': 'Traitement en cours…',
    'payroll.runPayrollButton': 'Traiter la paie ({{total}} RWF)',
    'payroll.rosterEmpty': 'Aucun employé sur la liste pour le moment.',
    'payroll.columnEmployee': 'Employé',
    'payroll.columnMonthlySalary': 'Salaire mensuel',
    'payroll.removeButton': 'Retirer',
    'payroll.paidTitle': 'Paie versée',
    'payroll.employeesPaidCount': '{{count}} employés payés',
    'payroll.backToRoster': 'Retour à la liste',
    'booking.dayMon': 'Lun',
    'booking.dayTue': 'Mar',
    'booking.dayWed': 'Mer',
    'booking.dayThu': 'Jeu',
    'booking.dayFri': 'Ven',
    'booking.daySat': 'Sam',
    'booking.daySun': 'Dim',
    'booking.availabilityTitle': 'Disponibilités hebdomadaires',
    'booking.availabilityBody': 'Les clients ne peuvent demander un rendez-vous que dans ces créneaux.',
    'booking.availabilitySaveError': 'Impossible d\'enregistrer vos disponibilités. Réessayez.',
    'booking.availabilityEmpty': 'Aucune disponibilité définie pour le moment — ajoutez un créneau ci-dessous.',
    'booking.removeButton': 'Retirer',
    'booking.timeValidationError': "Saisissez une heure de début réelle avant l'heure de fin (HH:mm).",
    'booking.saving': 'Enregistrement…',
    'booking.addWindowButton': 'Ajouter un créneau',
    'booking.bookingsTitle': 'Réservations',
    'booking.loadError': 'Impossible de charger vos réservations.',
    'booking.empty': 'Aucune réservation ouverte pour le moment.',
    'booking.notePrefix': 'Remarque :',
    'booking.confirmButton': 'Confirmer',
    'booking.declineButton': 'Refuser',
    'booking.updateError': 'Impossible de mettre à jour cette réservation.',
    'booking.updating': 'Mise à jour…',
    'booking.markCompletedButton': 'Marquer comme terminée',
    'booking.statusRequested': 'Demandée',
    'booking.statusConfirmed': 'Confirmée',
    'booking.statusDeclined': 'Refusée',
    'booking.statusCancelled': 'Annulée',
    'booking.statusCompleted': 'Terminée',
  },
};
