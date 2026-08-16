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
  | 'reports.topProductsTitle'
  | 'reports.topProductsEmpty'
  | 'reports.columnProduct'
  | 'reports.columnUnits'
  | 'reports.columnRevenue'
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
  | 'booking.statusCompleted'
  // Real 13th-localization-pass additions (2026-08-15): ReviewsScreen -- real
  // post-appointment booking reviews + Commerce product reviews, owner-side reply.
  | 'reviews.bookingTitle'
  | 'reviews.ratingAverageSingular'
  | 'reviews.ratingAveragePlural'
  | 'reviews.loadError'
  | 'reviews.bookingEmpty'
  | 'reviews.productTitle'
  | 'reviews.productLoadError'
  | 'reviews.productEmpty'
  | 'reviews.yourReply'
  | 'reviews.replyPlaceholder'
  | 'reviews.replyError'
  | 'reviews.posting'
  | 'reviews.updateReply'
  | 'reviews.postReply'
  | 'reviews.cancel'
  | 'reviews.editReply'
  | 'reviews.reply'
  // Real 14th-localization-pass additions (2026-08-15): PosScreen -- the real
  // cash-register/POS UI (Register + Catalog modes, with Options/Pricing/Time-deal
  // sub-panels per product), merchant-mfe's largest and highest-daily-traffic screen.
  | 'pos.modeRegister'
  | 'pos.modeCatalog'
  | 'pos.catalogLoadError'
  | 'pos.loading'
  | 'pos.retry'
  | 'pos.registerEmpty'
  | 'pos.outOfStock'
  | 'pos.stockAvailable'
  | 'pos.cartTitle'
  | 'pos.cartEmpty'
  | 'pos.decreaseQuantityAria'
  | 'pos.increaseQuantityAria'
  | 'pos.total'
  | 'pos.checkoutButton'
  | 'pos.checkoutTitle'
  | 'pos.checkoutModeQr'
  | 'pos.checkoutModeCard'
  | 'pos.backToCart'
  | 'pos.qrGenerateError'
  | 'pos.doneNewSale'
  | 'pos.generating'
  | 'pos.cardChargeError'
  | 'pos.cardChargedResult'
  | 'pos.charging'
  | 'pos.chargeButton'
  | 'pos.uploadPhotoError'
  | 'pos.priceValidationError'
  | 'pos.originalPriceValidationError'
  | 'pos.stockValidationError'
  | 'pos.addProductError'
  | 'pos.adjustStockPrompt'
  | 'pos.updateStockError'
  | 'pos.surplusDealSetButton'
  | 'pos.surplusDealClearButton'
  | 'pos.surplusDealHoursPrompt'
  | 'pos.surplusDealHoursError'
  | 'pos.surplusDealStockPrompt'
  | 'pos.surplusDealError'
  | 'pos.addProductTitle'
  | 'pos.nameLabel'
  | 'pos.priceLabel'
  | 'pos.stockLabel'
  | 'pos.stockPlaceholderUnlimited'
  | 'pos.originalPriceLabel'
  | 'pos.photoLabel'
  | 'pos.uploading'
  | 'pos.changePhoto'
  | 'pos.addPhotoFromDevice'
  | 'pos.descriptionLabel'
  | 'pos.descriptionPlaceholder'
  | 'pos.adding'
  | 'pos.addButton'
  | 'pos.lowStockWarningSingular'
  | 'pos.lowStockWarningPlural'
  | 'pos.lowStockOutOfStock'
  | 'pos.lowStockLeft'
  | 'pos.catalogEmptyOwnForm'
  | 'pos.columnProduct'
  | 'pos.columnPrice'
  | 'pos.discountOff'
  | 'pos.unlimitedStock'
  | 'pos.inStock'
  | 'pos.optionsToggle'
  | 'pos.pricingToggle'
  | 'pos.timeDealToggle'
  | 'pos.adjustStockButton'
  | 'pos.removeButton'
  | 'pos.optionGroupsLoadError'
  | 'pos.existingOptionGroups'
  | 'pos.optionGroupsEmpty'
  | 'pos.removing'
  | 'pos.addOptionGroupTitle'
  | 'pos.groupNamePlaceholder'
  | 'pos.choicePlaceholder'
  | 'pos.choicePriceDeltaPlaceholder'
  | 'pos.removeChoiceAria'
  | 'pos.addAnotherChoice'
  | 'pos.addOptionGroupError'
  | 'pos.addOptionGroupButton'
  | 'pos.removeOptionGroupError'
  | 'pos.priceTiersLoadError'
  | 'pos.tierMinQuantityError'
  | 'pos.tierUnitPriceError'
  | 'pos.tierBelowRegularError'
  | 'pos.tierTooManyError'
  | 'pos.tierSaveError'
  | 'pos.bulkPricingTitle'
  | 'pos.bulkPricingBody'
  | 'pos.tierMinQuantityPlaceholder'
  | 'pos.tierUnitPricePlaceholder'
  | 'pos.removeTierAria'
  | 'pos.addTier'
  | 'pos.saved'
  | 'pos.saving'
  | 'pos.saveTiersButton'
  | 'pos.timeDealsLoadError'
  | 'pos.timeDealPriceError'
  | 'pos.timeDealQuantityError'
  | 'pos.timeDealTimesRequiredError'
  | 'pos.timeDealEndAfterStartError'
  | 'pos.timeDealCreateError'
  | 'pos.timeDealEndError'
  | 'pos.timeDealTitle'
  | 'pos.timeDealBody'
  | 'pos.timeDealEndNow'
  | 'pos.timeDealPricePlaceholder'
  | 'pos.timeDealQuantityPlaceholder'
  | 'pos.startTimeDeal'
  | 'pos.creating'
  | 'pos.pastDealsSingular'
  | 'pos.pastDealsPlural'
  // Real 15th (and final)-localization-pass additions (2026-08-15): SettingsScreen --
  // the last remaining tab-content screen, closing merchant-mfe's entire backlog.
  // Covers webhook config, followers broadcast, API integration, devices, category,
  // store settings, Eats Club opt-in, fee waiver, and KYB verification.
  | 'settings.saveError'
  | 'settings.saved'
  | 'settings.saving'
  | 'settings.saveButton'
  | 'settings.merchantIdPrefix'
  | 'settings.webhookUrlLabel'
  | 'settings.webhookUrlBody'
  | 'settings.followersTitle'
  | 'settings.loading'
  | 'settings.followersCountSingular'
  | 'settings.followersCountPlural'
  | 'settings.broadcastTitleLabel'
  | 'settings.broadcastTitlePlaceholder'
  | 'settings.broadcastMessageLabel'
  | 'settings.broadcastMessagePlaceholder'
  | 'settings.sending'
  | 'settings.broadcastButton'
  | 'settings.broadcastNeedsFollower'
  | 'settings.broadcastError'
  | 'settings.broadcastSentSingular'
  | 'settings.broadcastSentPlural'
  | 'settings.webhookDeliveriesLoadError'
  | 'settings.apiKeyGenerateError'
  | 'settings.replayError'
  | 'settings.apiIntegrationTitle'
  | 'settings.apiIntegrationBody'
  | 'settings.generating'
  | 'settings.generateApiKeyButton'
  | 'settings.recentDeliveriesTitle'
  | 'settings.deliveriesEmpty'
  | 'settings.attemptSingular'
  | 'settings.attemptPlural'
  | 'settings.statusDelivered'
  | 'settings.statusPending'
  | 'settings.statusExhausted'
  | 'settings.replaying'
  | 'settings.replayButton'
  | 'settings.devicesLoadError'
  | 'settings.deviceRemoveError'
  | 'settings.devicesTitle'
  | 'settings.devicesBody'
  | 'settings.devicesEmpty'
  | 'settings.unknownDevice'
  | 'settings.thisDeviceSuffix'
  | 'settings.deviceVerified'
  | 'settings.deviceNotVerified'
  | 'settings.lastSeenPrefix'
  | 'settings.removing'
  | 'settings.removeButton'
  | 'settings.categoryTitle'
  | 'settings.categoryExamplesLabel'
  | 'settings.categoryPlaceholder'
  | 'settings.storeSettingsTitle'
  | 'settings.storePhotoUrlLabel'
  | 'settings.minOrderAmountLabel'
  | 'settings.cashbackRateLabel'
  | 'settings.phoneNumberLabel'
  | 'settings.openingHoursLabel'
  | 'settings.avgPrepTimeMinutesLabel'
  | 'settings.acceptScheduledOrdersTitle'
  | 'settings.acceptScheduledOrdersBody'
  | 'settings.acceptingOrdersTitle'
  | 'settings.acceptingOrdersBody'
  | 'settings.on'
  | 'settings.off'
  | 'settings.paused'
  | 'settings.eatsClubTitle'
  | 'settings.eatsClubBody'
  | 'settings.eatsClubParticipating'
  | 'settings.eatsClubOptIn'
  | 'settings.feeWaiverTitle'
  | 'settings.feeWaiverActiveBody'
  | 'settings.feeWaiverEligibleBody'
  | 'settings.feeWaiverError'
  | 'settings.feeWaiverApplyButton'
  | 'settings.kybSubmitError'
  | 'settings.kybAutoMatched'
  | 'settings.kybAutoNotFound'
  | 'settings.kybAutoInvalidFormat'
  | 'settings.kybAutoDefault'
  | 'settings.kybTitle'
  | 'settings.kybVerifiedBadge'
  | 'settings.kybReviewingBody'
  | 'settings.kybTinLabel'
  | 'settings.kybSubmitting'
  | 'settings.kybSubmitButton'
  | 'settings.kybRejectedPlain'
  | 'settings.kybRejectedWithDetail'
  // Real 16th-localization-pass additions (2026-08-15): DeviceStepUpPrompt -- the
  // last remaining gap, a shared component (BusinessAccountScreen, PayrollScreen,
  // PosScreen, SettingsScreen) rather than any single screen's own responsibility.
  // Closes out ALL of merchant-mfe's localization backlog, shared components included.
  | 'deviceStepUp.verifyError'
  | 'deviceStepUp.title'
  | 'deviceStepUp.body'
  | 'deviceStepUp.passwordPlaceholder'
  | 'deviceStepUp.showPassword'
  | 'deviceStepUp.hidePassword'
  | 'deviceStepUp.cancel'
  | 'deviceStepUp.verifying'
  | 'deviceStepUp.verifyButton';

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
    'reports.topProductsTitle': 'Best-selling products',
    'reports.topProductsEmpty': 'No products sold in this range.',
    'reports.columnProduct': 'Product',
    'reports.columnUnits': 'Units sold',
    'reports.columnRevenue': 'Revenue',
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
    'reviews.bookingTitle': 'Booking reviews',
    'reviews.ratingAverageSingular': '{{average}} ★ average ({{count}} review)',
    'reviews.ratingAveragePlural': '{{average}} ★ average ({{count}} reviews)',
    'reviews.loadError': 'Could not load your reviews.',
    'reviews.bookingEmpty': 'No booking reviews yet — reviews will show up here once customers leave them after a booking.',
    'reviews.productTitle': 'Product reviews',
    'reviews.productLoadError': 'Could not load your product reviews.',
    'reviews.productEmpty': 'No product reviews yet — reviews will show up here once customers leave them after a purchase.',
    'reviews.yourReply': 'Your reply',
    'reviews.replyPlaceholder': 'Write a reply to this review',
    'reviews.replyError': 'Could not post your reply.',
    'reviews.posting': 'Posting…',
    'reviews.updateReply': 'Update reply',
    'reviews.postReply': 'Post reply',
    'reviews.cancel': 'Cancel',
    'reviews.editReply': 'Edit reply',
    'reviews.reply': 'Reply',
    'pos.modeRegister': 'Register',
    'pos.modeCatalog': 'Catalog',
    'pos.catalogLoadError': 'Could not load the catalog.',
    'pos.loading': 'Loading…',
    'pos.retry': 'Retry',
    'pos.registerEmpty': 'No products yet — add some in the Catalog tab first.',
    'pos.outOfStock': 'Out of stock',
    'pos.stockAvailable': '{{count}} available',
    'pos.cartTitle': 'Cart',
    'pos.cartEmpty': 'Tap a product to add it.',
    'pos.decreaseQuantityAria': 'Decrease quantity of {{name}}',
    'pos.increaseQuantityAria': 'Increase quantity of {{name}}',
    'pos.total': 'Total',
    'pos.checkoutButton': 'Checkout',
    'pos.checkoutTitle': 'Checkout',
    'pos.checkoutModeQr': 'QR code',
    'pos.checkoutModeCard': 'Card',
    'pos.backToCart': 'Back to cart',
    'pos.qrGenerateError': 'Could not generate a QR code.',
    'pos.doneNewSale': 'Done — new sale',
    'pos.generating': 'Generating…',
    'pos.cardChargeError': 'Could not charge this card.',
    'pos.cardChargedResult': 'Card charged — •••• {{last4}}',
    'pos.charging': 'Charging…',
    'pos.chargeButton': 'Charge {{amount}} RWF',
    'pos.uploadPhotoError': "Couldn't upload that photo. Check your connection and try again.",
    'pos.priceValidationError': 'Enter a price greater than zero.',
    'pos.originalPriceValidationError': 'The original price must be greater than the current price.',
    'pos.stockValidationError': 'Stock must be a whole number of zero or more. Leave it blank for unlimited availability.',
    'pos.addProductError': 'Could not add this product.',
    'pos.adjustStockPrompt': 'Set available units. Leave blank for unlimited availability.',
    'pos.updateStockError': 'Could not update stock.',
    'pos.surplusDealSetButton': 'Mark as closing deal',
    'pos.surplusDealClearButton': 'End closing deal',
    'pos.surplusDealHoursPrompt': 'Hours until this deal closes',
    'pos.surplusDealHoursError': 'Enter a real number of hours greater than zero.',
    'pos.surplusDealStockPrompt': 'How many units are you selling in this closing deal?',
    'pos.surplusDealError': 'Could not set this closing deal.',
    'pos.addProductTitle': 'Add a product',
    'pos.nameLabel': 'Name',
    'pos.priceLabel': 'Price (RWF)',
    'pos.stockLabel': 'Stock (optional)',
    'pos.stockPlaceholderUnlimited': 'Unlimited',
    'pos.originalPriceLabel': 'Original price (optional)',
    'pos.photoLabel': 'Product photo (optional)',
    'pos.uploading': 'Uploading…',
    'pos.changePhoto': 'Change photo',
    'pos.addPhotoFromDevice': 'Add photo from device',
    'pos.descriptionLabel': 'Description (optional)',
    'pos.descriptionPlaceholder': 'What customers should know about this item',
    'pos.adding': 'Adding…',
    'pos.addButton': 'Add',
    'pos.lowStockWarningSingular': '{{count}} product needs stock attention',
    'pos.lowStockWarningPlural': '{{count}} products need stock attention',
    'pos.lowStockOutOfStock': 'out of stock',
    'pos.lowStockLeft': '{{count}} left',
    'pos.catalogEmptyOwnForm': 'No products yet — use the form above to add your first one.',
    'pos.columnProduct': 'Product',
    'pos.columnPrice': 'Price',
    'pos.discountOff': '{{percent}}% off',
    'pos.unlimitedStock': 'Unlimited stock',
    'pos.inStock': '{{count}} in stock',
    'pos.optionsToggle': 'Options',
    'pos.pricingToggle': 'Pricing',
    'pos.timeDealToggle': 'Time deal',
    'pos.adjustStockButton': 'Adjust stock',
    'pos.removeButton': 'Remove',
    'pos.optionGroupsLoadError': 'Could not load option groups.',
    'pos.existingOptionGroups': 'Existing option groups',
    'pos.optionGroupsEmpty': 'No option groups yet -- a buyer will see a plain +/- stepper for this item until you add one (e.g. "Size" with Small/Regular/Large choices).',
    'pos.removing': 'Removing…',
    'pos.addOptionGroupTitle': 'Add an option group',
    'pos.groupNamePlaceholder': 'Group name (e.g. Size)',
    'pos.choicePlaceholder': 'Choice {{index}} (e.g. {{example}})',
    'pos.choicePriceDeltaPlaceholder': '+RWF',
    'pos.removeChoiceAria': 'Remove choice',
    'pos.addAnotherChoice': 'Add another choice',
    'pos.addOptionGroupError': 'Could not add this option group.',
    'pos.addOptionGroupButton': 'Add option group',
    'pos.removeOptionGroupError': 'Could not remove this option group.',
    'pos.priceTiersLoadError': 'Could not load price tiers.',
    'pos.tierMinQuantityError': 'Each minimum quantity must be a whole number of at least 1.',
    'pos.tierUnitPriceError': 'Each unit price must be greater than zero.',
    'pos.tierBelowRegularError': 'Each tier must cost less per unit than the regular price ({{price}} RWF).',
    'pos.tierTooManyError': 'Too many price tiers -- 10 is the real limit.',
    'pos.tierSaveError': 'Could not save these price tiers.',
    'pos.bulkPricingTitle': 'Bulk/wholesale pricing',
    'pos.bulkPricingBody': 'A buyer ordering at least the minimum quantity automatically pays the lower unit price at checkout -- real pricing, not a label. Leave empty for no bulk discount.',
    'pos.tierMinQuantityPlaceholder': 'Min quantity (e.g. 10)',
    'pos.tierUnitPricePlaceholder': 'Unit price (RWF)',
    'pos.removeTierAria': 'Remove tier',
    'pos.addTier': 'Add a tier',
    'pos.saved': 'Saved.',
    'pos.saving': 'Saving…',
    'pos.saveTiersButton': 'Save price tiers',
    'pos.timeDealsLoadError': 'Could not load time deals.',
    'pos.timeDealPriceError': 'The deal price must be greater than zero and less than the regular price ({{price}} RWF).',
    'pos.timeDealQuantityError': 'Quantity must be a whole number of at least 1.',
    'pos.timeDealTimesRequiredError': 'Set both a start and end time.',
    'pos.timeDealEndAfterStartError': 'The end time must be after the start time.',
    'pos.timeDealCreateError': 'Could not create this time deal.',
    'pos.timeDealEndError': 'Could not end this deal.',
    'pos.timeDealTitle': '⏰ Time deal',
    'pos.timeDealBody': "A real time-boxed, quantity-capped discount -- checkout automatically charges the deal price while it's live and stock remains, then reverts to the regular price.",
    'pos.timeDealEndNow': 'End now',
    'pos.timeDealPricePlaceholder': 'Deal price (RWF)',
    'pos.timeDealQuantityPlaceholder': 'Quantity',
    'pos.startTimeDeal': 'Start a time deal',
    'pos.creating': 'Creating…',
    'pos.pastDealsSingular': '{{count}} past deal for this product.',
    'pos.pastDealsPlural': '{{count}} past deals for this product.',
    'settings.saveError': 'Could not save.',
    'settings.saved': 'Saved.',
    'settings.saving': 'Saving…',
    'settings.saveButton': 'Save',
    'settings.merchantIdPrefix': 'Merchant ID',
    'settings.webhookUrlLabel': 'Webhook URL',
    'settings.webhookUrlBody': "We'll notify this address every time a payment completes. If it doesn't respond, we'll keep retrying for about 3 days.",
    'settings.followersTitle': 'Followers',
    'settings.loading': 'Loading…',
    'settings.followersCountSingular': '{{count}} customer following your store',
    'settings.followersCountPlural': '{{count}} customers following your store',
    'settings.broadcastTitleLabel': 'Title',
    'settings.broadcastTitlePlaceholder': 'New arrivals this week',
    'settings.broadcastMessageLabel': 'Message',
    'settings.broadcastMessagePlaceholder': "Tell your followers what's new.",
    'settings.sending': 'Sending…',
    'settings.broadcastButton': 'Broadcast to followers',
    'settings.broadcastNeedsFollower': 'You need at least one follower to send a broadcast.',
    'settings.broadcastError': 'Could not send this broadcast.',
    'settings.broadcastSentSingular': 'Sent to {{count}} follower.',
    'settings.broadcastSentPlural': 'Sent to {{count}} followers.',
    'settings.webhookDeliveriesLoadError': 'Could not load webhook deliveries.',
    'settings.apiKeyGenerateError': 'Could not generate an API key.',
    'settings.replayError': 'Could not replay this delivery.',
    'settings.apiIntegrationTitle': 'API integration',
    'settings.apiIntegrationBody': 'For merchants integrating their own systems with itunda.',
    'settings.generating': 'Generating…',
    'settings.generateApiKeyButton': 'Generate a new API key',
    'settings.recentDeliveriesTitle': 'Recent webhook deliveries',
    'settings.deliveriesEmpty': 'No webhook deliveries yet — deliveries will show up here once an event triggers your webhook.',
    'settings.attemptSingular': '{{count}} attempt',
    'settings.attemptPlural': '{{count}} attempts',
    'settings.statusDelivered': 'Delivered',
    'settings.statusPending': 'Pending',
    'settings.statusExhausted': 'Exhausted',
    'settings.replaying': 'Replaying…',
    'settings.replayButton': 'Replay',
    'settings.devicesLoadError': 'Could not load your devices.',
    'settings.deviceRemoveError': 'Could not remove this device.',
    'settings.devicesTitle': 'Devices',
    'settings.devicesBody': 'Devices that have signed in to this account. A device must be verified before it can move money.',
    'settings.devicesEmpty': 'No devices recorded yet — devices will show up here once you sign in.',
    'settings.unknownDevice': 'Unknown device',
    'settings.thisDeviceSuffix': '(this device)',
    'settings.deviceVerified': '✓ Verified — can move money',
    'settings.deviceNotVerified': '⚠ Not verified — sign-in only',
    'settings.lastSeenPrefix': 'Last seen',
    'settings.removing': 'Removing…',
    'settings.removeButton': 'Remove',
    'settings.categoryTitle': 'Category',
    'settings.categoryExamplesLabel': 'e.g. Rwandan, Chinese, Bakery, Cafe',
    'settings.categoryPlaceholder': 'Category',
    'settings.storeSettingsTitle': 'Store settings',
    'settings.storePhotoUrlLabel': 'Store photo URL',
    'settings.minOrderAmountLabel': 'Minimum order amount (RWF, blank = none)',
    'settings.cashbackRateLabel': 'Boosted cashback rate (0-5%, blank = standard rate)',
    'settings.phoneNumberLabel': 'Phone number (shown on the map, blank = hidden)',
    'settings.openingHoursLabel': 'Opening hours (shown on the map, blank = hidden)',
    'settings.avgPrepTimeMinutesLabel': 'Average kitchen prep time in minutes (blank = itunda default)',
    'settings.acceptScheduledOrdersTitle': 'Accept scheduled orders',
    'settings.acceptScheduledOrdersBody': 'Let buyers pick a future delivery/pickup time.',
    'settings.acceptingOrdersTitle': 'Accepting orders',
    'settings.acceptingOrdersBody': 'Pause temporarily if you\'re too busy to take new orders right now.',
    'settings.on': 'On',
    'settings.off': 'Off',
    'settings.paused': 'Paused',
    'settings.eatsClubTitle': 'Eats Club',
    'settings.eatsClubBody': 'Offer free delivery to buyers with an active Eats Club membership.',
    'settings.eatsClubParticipating': 'Participating',
    'settings.eatsClubOptIn': 'Opt in',
    'settings.feeWaiverTitle': 'Small-merchant fee waiver',
    'settings.feeWaiverActiveBody': 'Active -- you pay no platform fee on payments you collect.',
    'settings.feeWaiverEligibleBody': 'If your payment volume over the last 30 days is small, you may qualify for a full fee waiver.',
    'settings.feeWaiverError': 'Could not apply for a fee waiver.',
    'settings.feeWaiverApplyButton': 'Apply',
    'settings.kybSubmitError': 'Could not submit for KYB review.',
    'settings.kybAutoMatched': 'Your TIN checked out automatically.',
    'settings.kybAutoNotFound': "We couldn't find a match yet — a team member will take a look.",
    'settings.kybAutoInvalidFormat': 'The TIN format looked off — a team member will double-check it.',
    'settings.kybAutoDefault': 'A team member will take a look soon.',
    'settings.kybTitle': 'Business verification (KYB)',
    'settings.kybVerifiedBadge': '✓ Verified',
    'settings.kybReviewingBody': "We're reviewing your business details.",
    'settings.kybTinLabel': 'Business TIN (9 digits)',
    'settings.kybSubmitting': 'Submitting…',
    'settings.kybSubmitButton': 'Submit for verification',
    'settings.kybRejectedPlain': 'Previous submission was rejected.',
    'settings.kybRejectedWithDetail': 'Previous submission was rejected: {{detail}}',
    'deviceStepUp.verifyError': 'Could not verify this device.',
    'deviceStepUp.title': '🔒 Verify this device',
    'deviceStepUp.body': 'This is a new device for your account. Re-enter your password to allow it to move money, then try again.',
    'deviceStepUp.passwordPlaceholder': 'Password',
    'deviceStepUp.showPassword': 'Show password',
    'deviceStepUp.hidePassword': 'Hide password',
    'deviceStepUp.cancel': 'Cancel',
    'deviceStepUp.verifying': 'Verifying…',
    'deviceStepUp.verifyButton': 'Verify device',
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
    'reports.topProductsTitle': 'Ibicuruzwa bigurwa cyane',
    'reports.topProductsEmpty': 'Nta bicuruzwa byagurishijwe muri iki gihe.',
    'reports.columnProduct': 'Igicuruzwa',
    'reports.columnUnits': 'Umubare wagurishijwe',
    'reports.columnRevenue': 'Amafaranga yinjiye',
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
    'reviews.bookingTitle': 'Ibitekerezo ku gahunda',
    'reviews.ratingAverageSingular': 'Impuzandengo ★ {{average}} (igitekerezo {{count}})',
    'reviews.ratingAveragePlural': 'Impuzandengo ★ {{average}} (ibitekerezo {{count}})',
    'reviews.loadError': 'Ntibishoboka gushakisha ibitekerezo byawe.',
    'reviews.bookingEmpty': 'Nta bitekerezo ku gahunda urafite — bizagaragara hano igihe abakiriya bazabitanga nyuma ya gahunda.',
    'reviews.productTitle': 'Ibitekerezo ku bicuruzwa',
    'reviews.productLoadError': 'Ntibishoboka gushakisha ibitekerezo ku bicuruzwa byawe.',
    'reviews.productEmpty': 'Nta bitekerezo ku bicuruzwa urafite — bizagaragara hano igihe abakiriya bazabitanga nyuma yo kugura.',
    'reviews.yourReply': 'Igisubizo cyawe',
    'reviews.replyPlaceholder': 'Andika igisubizo kuri iki gitekerezo',
    'reviews.replyError': 'Ntibishoboka gutanga igisubizo cyawe.',
    'reviews.posting': 'Kohereza…',
    'reviews.updateReply': 'Vugurura igisubizo',
    'reviews.postReply': 'Ohereza igisubizo',
    'reviews.cancel': 'Hagarika',
    'reviews.editReply': 'Hindura igisubizo',
    'reviews.reply': 'Subiza',
    'pos.modeRegister': 'Igurisha',
    'pos.modeCatalog': 'Ibicuruzwa',
    'pos.catalogLoadError': 'Ntibishoboka gushakisha ibicuruzwa.',
    'pos.loading': 'Gushakisha…',
    'pos.retry': 'Ongera ugerageze',
    'pos.registerEmpty': 'Nta bicuruzwa ufite — banza wongeremo bimwe muri Ibicuruzwa.',
    'pos.outOfStock': 'Byashize',
    'pos.stockAvailable': '{{count}} biracyahari',
    'pos.cartTitle': 'Ikarito',
    'pos.cartEmpty': 'Kanda ku gicuruzwa kugira ngo kigongereho.',
    'pos.decreaseQuantityAria': 'Gabanya umubare wa {{name}}',
    'pos.increaseQuantityAria': 'Ongera umubare wa {{name}}',
    'pos.total': 'Igiteranyo',
    'pos.checkoutButton': 'Kwishyura',
    'pos.checkoutTitle': 'Kwishyura',
    'pos.checkoutModeQr': 'QR code',
    'pos.checkoutModeCard': 'Ikarita',
    'pos.backToCart': 'Subira ku ikarito',
    'pos.qrGenerateError': 'Ntibishoboka gukora QR code.',
    'pos.doneNewSale': 'Byarangiye — igurisha rishya',
    'pos.generating': 'Gukora…',
    'pos.cardChargeError': 'Ntibishoboka gukuramo amafaranga kuri iyi karita.',
    'pos.cardChargedResult': 'Amafaranga yakuwe — •••• {{last4}}',
    'pos.charging': 'Gukuramo…',
    'pos.chargeButton': 'Kuramo {{amount}} RWF',
    'pos.uploadPhotoError': 'Ntibishoboka kohereza iyo foto. Reba interineti yawe hanyuma ugerageze nanone.',
    'pos.priceValidationError': 'Andika igiciro kiruta zeru.',
    'pos.originalPriceValidationError': 'Igiciro cy\'umwimerere kigomba kuba kirenze igiciro kigezweho.',
    'pos.stockValidationError': 'Ibicuruzwa bigomba kuba umubare wuzuye wa zeru cyangwa wenda. Reka ubusa niba nta mubare uzwi.',
    'pos.addProductError': 'Ntibishoboka kongeraho iki gicuruzwa.',
    'pos.adjustStockPrompt': 'Shyiraho umubare uhari. Reka ubusa niba nta mubare uzwi.',
    'pos.updateStockError': 'Ntibishoboka kuvugurura ibicuruzwa.',
    'pos.surplusDealSetButton': 'Shyiraho nk\'igurisha ryo gufunga',
    'pos.surplusDealClearButton': 'Hagarika igurisha ryo gufunga',
    'pos.surplusDealHoursPrompt': 'Amasaha asigaye kugeza iri gurisha rirangiye',
    'pos.surplusDealHoursError': 'Andika umubare w\'amasaha uruta zeru.',
    'pos.surplusDealStockPrompt': 'Ni ibicuruzwa bingana iki ugurisha muri iri gurisha ryo gufunga?',
    'pos.surplusDealError': 'Ntibishoboka gushyiraho iri gurisha ryo gufunga.',
    'pos.addProductTitle': 'Ongeraho igicuruzwa',
    'pos.nameLabel': 'Izina',
    'pos.priceLabel': 'Igiciro (RWF)',
    'pos.stockLabel': 'Ibicuruzwa (bitari ngombwa)',
    'pos.stockPlaceholderUnlimited': 'Ntabwo bigira umupaka',
    'pos.originalPriceLabel': 'Igiciro cy\'umwimerere (bitari ngombwa)',
    'pos.photoLabel': 'Ifoto y\'igicuruzwa (bitari ngombwa)',
    'pos.uploading': 'Kohereza…',
    'pos.changePhoto': 'Hindura ifoto',
    'pos.addPhotoFromDevice': 'Ongeraho ifoto uva kuri telefoni',
    'pos.descriptionLabel': 'Ibisobanuro (bitari ngombwa)',
    'pos.descriptionPlaceholder': 'Ibyo abakiriya bagomba kumenya kuri iki gicuruzwa',
    'pos.adding': 'Kongeraho…',
    'pos.addButton': 'Ongeraho',
    'pos.lowStockWarningSingular': 'Igicuruzwa {{count}} gikeneye kwitabwaho',
    'pos.lowStockWarningPlural': 'Ibicuruzwa {{count}} bikeneye kwitabwaho',
    'pos.lowStockOutOfStock': 'byashize',
    'pos.lowStockLeft': '{{count}} bisigaye',
    'pos.catalogEmptyOwnForm': 'Nta bicuruzwa ufite — koresha ifishi hejuru wongereho icya mbere.',
    'pos.columnProduct': 'Igicuruzwa',
    'pos.columnPrice': 'Igiciro',
    'pos.discountOff': 'Igabanuka rya {{percent}}%',
    'pos.unlimitedStock': 'Ntabwo bigira umupaka',
    'pos.inStock': '{{count}} birahari',
    'pos.optionsToggle': 'Amahitamo',
    'pos.pricingToggle': 'Ibiciro',
    'pos.timeDealToggle': 'Igabanuka ry\'igihe',
    'pos.adjustStockButton': 'Vugurura ibicuruzwa',
    'pos.removeButton': 'Kuraho',
    'pos.optionGroupsLoadError': 'Ntibishoboka gushakisha amatsinda y\'amahitamo.',
    'pos.existingOptionGroups': 'Amatsinda y\'amahitamo asanzwe',
    'pos.optionGroupsEmpty': 'Nta itsinda ry\'amahitamo ufite -- umukiriya azabona ibare rya +/- gusa kuri iki gicuruzwa kugeza uwongeyeho rimwe (urugero "Ingano" hamwe n\'amahitamo Ntoya/Isanzwe/Nini).',
    'pos.removing': 'Gukuraho…',
    'pos.addOptionGroupTitle': 'Ongeraho itsinda ry\'amahitamo',
    'pos.groupNamePlaceholder': 'Izina ry\'itsinda (urugero Ingano)',
    'pos.choicePlaceholder': 'Ihitamo {{index}} (urugero {{example}})',
    'pos.choicePriceDeltaPlaceholder': '+RWF',
    'pos.removeChoiceAria': 'Kuraho ihitamo',
    'pos.addAnotherChoice': 'Ongeraho irindi hitamo',
    'pos.addOptionGroupError': 'Ntibishoboka kongeraho iri tsinda ry\'amahitamo.',
    'pos.addOptionGroupButton': 'Ongeraho itsinda ry\'amahitamo',
    'pos.removeOptionGroupError': 'Ntibishoboka gukuraho iri tsinda ry\'amahitamo.',
    'pos.priceTiersLoadError': 'Ntibishoboka gushakisha ibiciro by\'itumizwa rinini.',
    'pos.tierMinQuantityError': 'Buri mubare muto ugomba kuba umubare wuzuye w\'nibura 1.',
    'pos.tierUnitPriceError': 'Buri giciro cy\'kimwe kigomba kuba kiruta zeru.',
    'pos.tierBelowRegularError': 'Buri rwego rugomba kuba rufite igiciro kiri munsi y\'igiciro gisanzwe ({{price}} RWF).',
    'pos.tierTooManyError': 'Inzego z\'ibiciro nyinshi cyane -- 10 ni urugero rwemewe.',
    'pos.tierSaveError': 'Ntibishoboka kubika izi nzego z\'ibiciro.',
    'pos.bulkPricingTitle': 'Ibiciro by\'itumizwa rinini',
    'pos.bulkPricingBody': 'Umukiriya utumiza nibura umubare muto agomba kwishyura ako kanya igiciro gito kuri buri kimwe -- ibi ni ibiciro by\'ukuri, ntabwo ari ibare gusa. Reka ubusa niba nta gabanuka rinini uhereye.',
    'pos.tierMinQuantityPlaceholder': 'Umubare muto (urugero 10)',
    'pos.tierUnitPricePlaceholder': 'Igiciro cy\'kimwe (RWF)',
    'pos.removeTierAria': 'Kuraho urwego',
    'pos.addTier': 'Ongeraho urwego',
    'pos.saved': 'Byabitswe.',
    'pos.saving': 'Kubika…',
    'pos.saveTiersButton': 'Bika inzego z\'ibiciro',
    'pos.timeDealsLoadError': 'Ntibishoboka gushakisha amagabanuka y\'igihe.',
    'pos.timeDealPriceError': 'Igiciro cy\'igabanuka kigomba kuba kiruta zeru kandi kiri munsi y\'igiciro gisanzwe ({{price}} RWF).',
    'pos.timeDealQuantityError': 'Umubare ugomba kuba umubare wuzuye w\'nibura 1.',
    'pos.timeDealTimesRequiredError': 'Shyiraho igihe cy\'itangira n\'iy\'irangira.',
    'pos.timeDealEndAfterStartError': 'Igihe cy\'irangira kigomba kuba nyuma y\'igihe cy\'itangira.',
    'pos.timeDealCreateError': 'Ntibishoboka gutangiza iri gabanuka ry\'igihe.',
    'pos.timeDealEndError': 'Ntibishoboka guhagarika iri gabanuka.',
    'pos.timeDealTitle': '⏰ Igabanuka ry\'igihe',
    'pos.timeDealBody': 'Igabanuka ry\'ukuri rifite igihe n\'umubare ugenwe -- kwishyura bikorwa ako kanya ku giciro cy\'igabanuka igihe rikiriho kandi ibicuruzwa bikiri hafi, hanyuma bikagaruka ku giciro gisanzwe.',
    'pos.timeDealEndNow': 'Hagarika nonaha',
    'pos.timeDealPricePlaceholder': 'Igiciro cy\'igabanuka (RWF)',
    'pos.timeDealQuantityPlaceholder': 'Umubare',
    'pos.startTimeDeal': 'Tangiza igabanuka ry\'igihe',
    'pos.creating': 'Gutangiza…',
    'pos.pastDealsSingular': 'Igabanuka {{count}} rishize kuri iki gicuruzwa.',
    'pos.pastDealsPlural': 'Amagabanuka {{count}} yashize kuri iki gicuruzwa.',
    'settings.saveError': 'Ntibishoboka kubika.',
    'settings.saved': 'Byabitswe.',
    'settings.saving': 'Kubika…',
    'settings.saveButton': 'Bika',
    'settings.merchantIdPrefix': 'Nomero y\'ucuruza',
    'settings.webhookUrlLabel': 'URL ya Webhook',
    'settings.webhookUrlBody': 'Tuzamenyesha iyi aderesi buri gihe kwishyura kurangiye. Niba itagisubiza, tuzakomeza kugerageza mu minsi 3.',
    'settings.followersTitle': 'Abakurikira',
    'settings.loading': 'Gushakisha…',
    'settings.followersCountSingular': 'Umukiriya {{count}} akurikira iduka ryawe',
    'settings.followersCountPlural': 'Abakiriya {{count}} bakurikira iduka ryawe',
    'settings.broadcastTitleLabel': 'Umutwe',
    'settings.broadcastTitlePlaceholder': 'Ibicuruzwa bishya iki cyumweru',
    'settings.broadcastMessageLabel': 'Ubutumwa',
    'settings.broadcastMessagePlaceholder': 'Bwira abakurikira bawe ikintu gishya.',
    'settings.sending': 'Kohereza…',
    'settings.broadcastButton': 'Ohereza ubutumwa ku bakurikira',
    'settings.broadcastNeedsFollower': 'Ukeneye nibura umukurikira umwe kugira ngo wohereze ubutumwa.',
    'settings.broadcastError': 'Ntibishoboka kohereza ubu butumwa.',
    'settings.broadcastSentSingular': 'Byoherejwe ku mukurikira {{count}}.',
    'settings.broadcastSentPlural': 'Byoherejwe ku bakurikira {{count}}.',
    'settings.webhookDeliveriesLoadError': 'Ntibishoboka gushakisha amakuru ya webhook.',
    'settings.apiKeyGenerateError': 'Ntibishoboka gukora API key.',
    'settings.replayError': 'Ntibishoboka kongera kohereza iyi porogaramu.',
    'settings.apiIntegrationTitle': 'Guhuza na API',
    'settings.apiIntegrationBody': 'Ku bacuruza bahuza ibikoresho byabo na itunda.',
    'settings.generating': 'Gukora…',
    'settings.generateApiKeyButton': 'Kora API key nshya',
    'settings.recentDeliveriesTitle': 'Amakuru ya webhook aheruka',
    'settings.deliveriesEmpty': 'Nta makuru ya webhook urafite — azagaragara hano igihe ikintu kizakoresha webhook yawe.',
    'settings.attemptSingular': 'Kugerageza {{count}}',
    'settings.attemptPlural': 'Kugerageza {{count}}',
    'settings.statusDelivered': 'Byoherejwe',
    'settings.statusPending': 'Bitegereje',
    'settings.statusExhausted': 'Byanze burundu',
    'settings.replaying': 'Kongera kohereza…',
    'settings.replayButton': 'Kongera kohereza',
    'settings.devicesLoadError': 'Ntibishoboka gushakisha telefoni zawe.',
    'settings.deviceRemoveError': 'Ntibishoboka gukuraho iyi telefoni.',
    'settings.devicesTitle': 'Telefoni',
    'settings.devicesBody': 'Telefoni zinjiye kuri iyi konti. Telefoni igomba kwemezwa mbere yo gukoresha amafaranga.',
    'settings.devicesEmpty': 'Nta telefoni yanditswe — izagaragara hano igihe uzinjira.',
    'settings.unknownDevice': 'Telefoni itazwi',
    'settings.thisDeviceSuffix': '(iyi telefoni)',
    'settings.deviceVerified': '✓ Yemejwe — ishobora gukoresha amafaranga',
    'settings.deviceNotVerified': '⚠ Ntiyemejwe — kwinjira gusa',
    'settings.lastSeenPrefix': 'Yagaragaye bwa nyuma',
    'settings.removing': 'Gukuraho…',
    'settings.removeButton': 'Kuraho',
    'settings.categoryTitle': 'Ubwoko',
    'settings.categoryExamplesLabel': 'urugero: Ibinyarwanda, Ibishinwa, Umukate, Kafe',
    'settings.categoryPlaceholder': 'Ubwoko',
    'settings.storeSettingsTitle': 'Igenamiterere ry\'iduka',
    'settings.storePhotoUrlLabel': 'URL y\'ifoto y\'iduka',
    'settings.minOrderAmountLabel': 'Amafaranga make yo gutumiza (RWF, ubusa = nta nkeneye)',
    'settings.cashbackRateLabel': 'Igipimo cy\'amafaranga yongerewe (0-5%, ubusa = igipimo gisanzwe)',
    'settings.phoneNumberLabel': 'Nomero ya telefoni (igaragara kuri ikarita, ubusa = ihishwa)',
    'settings.openingHoursLabel': 'Amasaha yo gukorera (agaragara kuri ikarita, ubusa = ahishwa)',
    'settings.avgPrepTimeMinutesLabel': 'Iminota isanzwe yo gutegura ibiryo (ubusa = igipimo gisanzwe cya itunda)',
    'settings.acceptScheduledOrdersTitle': 'Kwemera itumiza rigenwe igihe',
    'settings.acceptScheduledOrdersBody': 'Reka abaguzi bahitemo igihe kizaza cyo kohererezwa/gutorwa.',
    'settings.acceptingOrdersTitle': 'Kwakira itumiza',
    'settings.acceptingOrdersBody': 'Hagarika by\'agateganyo niba wuzuye ku buryo udashobora kwakira itumiza rishya.',
    'settings.on': 'Birakora',
    'settings.off': 'Ntibikora',
    'settings.paused': 'Byahagaritswe',
    'settings.eatsClubTitle': 'Eats Club',
    'settings.eatsClubBody': 'Tanga ubwoherezwa kubuntu ku baguzi bafite Eats Club ikora.',
    'settings.eatsClubParticipating': 'Urimo',
    'settings.eatsClubOptIn': 'Injiramo',
    'settings.feeWaiverTitle': 'Ihagarikwa ry\'amafaranga y\'abacuruza bato',
    'settings.feeWaiverActiveBody': 'Birakora -- nta mafaranga y\'urubuga wishyura ku byo wakiriye.',
    'settings.feeWaiverEligibleBody': 'Niba amafaranga wakiriye mu minsi 30 ishize ari make, ushobora kwemererwa ihagarikwa ryuzuye ry\'amafaranga.',
    'settings.feeWaiverError': 'Ntibishoboka gusaba ihagarikwa ry\'amafaranga.',
    'settings.feeWaiverApplyButton': 'Saba',
    'settings.kybSubmitError': 'Ntibishoboka kohereza kugira ngo bisuzumwe (KYB).',
    'settings.kybAutoMatched': 'TIN yawe yemejwe mu buryo bwikora.',
    'settings.kybAutoNotFound': 'Ntitwabashije kubona ihuza -- umukozi azabireba.',
    'settings.kybAutoInvalidFormat': 'Imiterere ya TIN ntabwo isa neza -- umukozi azabireba nanone.',
    'settings.kybAutoDefault': 'Umukozi azabireba vuba.',
    'settings.kybTitle': 'Kwemeza ubucuruzi (KYB)',
    'settings.kybVerifiedBadge': '✓ Byemejwe',
    'settings.kybReviewingBody': 'Turi gusuzuma amakuru y\'ubucuruzi bwawe.',
    'settings.kybTinLabel': 'TIN y\'ubucuruzi (imibare 9)',
    'settings.kybSubmitting': 'Kohereza…',
    'settings.kybSubmitButton': 'Ohereza kugira ngo bisuzumwe',
    'settings.kybRejectedPlain': 'Icyo wohereje mbere cyanzwe.',
    'settings.kybRejectedWithDetail': 'Icyo wohereje mbere cyanzwe: {{detail}}',
    'deviceStepUp.verifyError': 'Ntibishoboka kwemeza iyi telefoni.',
    'deviceStepUp.title': '🔒 Emeza iyi telefoni',
    'deviceStepUp.body': 'Iyi ni telefoni nshya kuri konti yawe. Ongera wandike ijambo ry\'ibanga kugira ngo yemererwe gukoresha amafaranga, hanyuma wongere ugerageze.',
    'deviceStepUp.passwordPlaceholder': 'Ijambo ry\'ibanga',
    'deviceStepUp.showPassword': 'Erekana ijambo ry\'ibanga',
    'deviceStepUp.hidePassword': 'Hisha ijambo ry\'ibanga',
    'deviceStepUp.cancel': 'Hagarika',
    'deviceStepUp.verifying': 'Kwemeza…',
    'deviceStepUp.verifyButton': 'Emeza telefoni',
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
    'reports.topProductsTitle': 'Produits les plus vendus',
    'reports.topProductsEmpty': 'Aucun produit vendu sur cette période.',
    'reports.columnProduct': 'Produit',
    'reports.columnUnits': 'Unités vendues',
    'reports.columnRevenue': 'Revenu',
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
    'reviews.bookingTitle': 'Avis sur les réservations',
    'reviews.ratingAverageSingular': 'Moyenne de {{average}} ★ ({{count}} avis)',
    'reviews.ratingAveragePlural': 'Moyenne de {{average}} ★ ({{count}} avis)',
    'reviews.loadError': 'Impossible de charger vos avis.',
    'reviews.bookingEmpty': "Aucun avis sur les réservations pour le moment — les avis apparaîtront ici une fois que les clients les laisseront après une réservation.",
    'reviews.productTitle': 'Avis sur les produits',
    'reviews.productLoadError': 'Impossible de charger vos avis sur les produits.',
    'reviews.productEmpty': "Aucun avis produit pour le moment — les avis apparaîtront ici une fois que les clients les laisseront après un achat.",
    'reviews.yourReply': 'Votre réponse',
    'reviews.replyPlaceholder': 'Écrivez une réponse à cet avis',
    'reviews.replyError': 'Impossible de publier votre réponse.',
    'reviews.posting': 'Publication en cours…',
    'reviews.updateReply': 'Mettre à jour la réponse',
    'reviews.postReply': 'Publier la réponse',
    'reviews.cancel': 'Annuler',
    'reviews.editReply': 'Modifier la réponse',
    'reviews.reply': 'Répondre',
    'pos.modeRegister': 'Caisse',
    'pos.modeCatalog': 'Catalogue',
    'pos.catalogLoadError': 'Impossible de charger le catalogue.',
    'pos.loading': 'Chargement…',
    'pos.retry': 'Réessayer',
    'pos.registerEmpty': "Aucun produit pour le moment — ajoutez-en dans l'onglet Catalogue d'abord.",
    'pos.outOfStock': 'Épuisé',
    'pos.stockAvailable': '{{count}} disponible(s)',
    'pos.cartTitle': 'Panier',
    'pos.cartEmpty': 'Appuyez sur un produit pour l\'ajouter.',
    'pos.decreaseQuantityAria': 'Diminuer la quantité de {{name}}',
    'pos.increaseQuantityAria': 'Augmenter la quantité de {{name}}',
    'pos.total': 'Total',
    'pos.checkoutButton': 'Encaisser',
    'pos.checkoutTitle': 'Encaissement',
    'pos.checkoutModeQr': 'Code QR',
    'pos.checkoutModeCard': 'Carte',
    'pos.backToCart': 'Retour au panier',
    'pos.qrGenerateError': 'Impossible de générer un code QR.',
    'pos.doneNewSale': 'Terminé — nouvelle vente',
    'pos.generating': 'Génération en cours…',
    'pos.cardChargeError': 'Impossible de débiter cette carte.',
    'pos.cardChargedResult': 'Carte débitée — •••• {{last4}}',
    'pos.charging': 'Débit en cours…',
    'pos.chargeButton': 'Débiter {{amount}} RWF',
    'pos.uploadPhotoError': "Impossible d'envoyer cette photo. Vérifiez votre connexion et réessayez.",
    'pos.priceValidationError': 'Saisissez un prix supérieur à zéro.',
    'pos.originalPriceValidationError': 'Le prix d\'origine doit être supérieur au prix actuel.',
    'pos.stockValidationError': 'Le stock doit être un nombre entier de zéro ou plus. Laissez vide pour une disponibilité illimitée.',
    'pos.addProductError': 'Impossible d\'ajouter ce produit.',
    'pos.adjustStockPrompt': 'Définissez les unités disponibles. Laissez vide pour une disponibilité illimitée.',
    'pos.updateStockError': 'Impossible de mettre à jour le stock.',
    'pos.surplusDealSetButton': 'Marquer comme vente de clôture',
    'pos.surplusDealClearButton': 'Terminer la vente de clôture',
    'pos.surplusDealHoursPrompt': 'Heures avant la fin de cette offre',
    'pos.surplusDealHoursError': 'Entrez un nombre d\'heures réel supérieur à zéro.',
    'pos.surplusDealStockPrompt': 'Combien d\'unités vendez-vous dans cette vente de clôture ?',
    'pos.surplusDealError': 'Impossible de définir cette vente de clôture.',
    'pos.addProductTitle': 'Ajouter un produit',
    'pos.nameLabel': 'Nom',
    'pos.priceLabel': 'Prix (RWF)',
    'pos.stockLabel': 'Stock (facultatif)',
    'pos.stockPlaceholderUnlimited': 'Illimité',
    'pos.originalPriceLabel': "Prix d'origine (facultatif)",
    'pos.photoLabel': 'Photo du produit (facultatif)',
    'pos.uploading': 'Envoi en cours…',
    'pos.changePhoto': 'Changer la photo',
    'pos.addPhotoFromDevice': "Ajouter une photo depuis l'appareil",
    'pos.descriptionLabel': 'Description (facultatif)',
    'pos.descriptionPlaceholder': 'Ce que les clients doivent savoir sur cet article',
    'pos.adding': 'Ajout en cours…',
    'pos.addButton': 'Ajouter',
    'pos.lowStockWarningSingular': '{{count}} produit nécessite votre attention',
    'pos.lowStockWarningPlural': '{{count}} produits nécessitent votre attention',
    'pos.lowStockOutOfStock': 'épuisé',
    'pos.lowStockLeft': '{{count}} restant(s)',
    'pos.catalogEmptyOwnForm': "Aucun produit pour le moment — utilisez le formulaire ci-dessus pour ajouter le premier.",
    'pos.columnProduct': 'Produit',
    'pos.columnPrice': 'Prix',
    'pos.discountOff': '{{percent}}% de réduction',
    'pos.unlimitedStock': 'Stock illimité',
    'pos.inStock': '{{count}} en stock',
    'pos.optionsToggle': 'Options',
    'pos.pricingToggle': 'Tarification',
    'pos.timeDealToggle': 'Offre flash',
    'pos.adjustStockButton': 'Ajuster le stock',
    'pos.removeButton': 'Retirer',
    'pos.optionGroupsLoadError': "Impossible de charger les groupes d'options.",
    'pos.existingOptionGroups': "Groupes d'options existants",
    'pos.optionGroupsEmpty': 'Aucun groupe d\'options pour le moment -- un client verra un simple sélecteur +/- pour cet article jusqu\'à ce que vous en ajoutiez un (ex. "Taille" avec les choix Petit/Normal/Grand).',
    'pos.removing': 'Suppression en cours…',
    'pos.addOptionGroupTitle': "Ajouter un groupe d'options",
    'pos.groupNamePlaceholder': 'Nom du groupe (ex. Taille)',
    'pos.choicePlaceholder': 'Choix {{index}} (ex. {{example}})',
    'pos.choicePriceDeltaPlaceholder': '+RWF',
    'pos.removeChoiceAria': 'Retirer le choix',
    'pos.addAnotherChoice': 'Ajouter un autre choix',
    'pos.addOptionGroupError': "Impossible d'ajouter ce groupe d'options.",
    'pos.addOptionGroupButton': "Ajouter le groupe d'options",
    'pos.removeOptionGroupError': "Impossible de retirer ce groupe d'options.",
    'pos.priceTiersLoadError': 'Impossible de charger les paliers de prix.',
    'pos.tierMinQuantityError': 'Chaque quantité minimale doit être un nombre entier d\'au moins 1.',
    'pos.tierUnitPriceError': 'Chaque prix unitaire doit être supérieur à zéro.',
    'pos.tierBelowRegularError': 'Chaque palier doit coûter moins cher à l\'unité que le prix normal ({{price}} RWF).',
    'pos.tierTooManyError': 'Trop de paliers de prix -- 10 est la limite réelle.',
    'pos.tierSaveError': 'Impossible d\'enregistrer ces paliers de prix.',
    'pos.bulkPricingTitle': 'Tarification en gros',
    'pos.bulkPricingBody': "Un client commandant au moins la quantité minimale paie automatiquement le prix unitaire réduit à la caisse -- une tarification réelle, pas une simple étiquette. Laissez vide pour aucune remise en gros.",
    'pos.tierMinQuantityPlaceholder': 'Quantité min. (ex. 10)',
    'pos.tierUnitPricePlaceholder': 'Prix unitaire (RWF)',
    'pos.removeTierAria': 'Retirer le palier',
    'pos.addTier': 'Ajouter un palier',
    'pos.saved': 'Enregistré.',
    'pos.saving': 'Enregistrement…',
    'pos.saveTiersButton': 'Enregistrer les paliers de prix',
    'pos.timeDealsLoadError': 'Impossible de charger les offres flash.',
    'pos.timeDealPriceError': "Le prix de l'offre doit être supérieur à zéro et inférieur au prix normal ({{price}} RWF).",
    'pos.timeDealQuantityError': 'La quantité doit être un nombre entier d\'au moins 1.',
    'pos.timeDealTimesRequiredError': 'Définissez une heure de début et de fin.',
    'pos.timeDealEndAfterStartError': "L'heure de fin doit être après l'heure de début.",
    'pos.timeDealCreateError': 'Impossible de créer cette offre flash.',
    'pos.timeDealEndError': 'Impossible de terminer cette offre.',
    'pos.timeDealTitle': '⏰ Offre flash',
    'pos.timeDealBody': "Une vraie remise limitée dans le temps et en quantité -- la caisse applique automatiquement le prix de l'offre tant qu'elle est active et qu'il reste du stock, puis revient au prix normal.",
    'pos.timeDealEndNow': 'Terminer maintenant',
    'pos.timeDealPricePlaceholder': "Prix de l'offre (RWF)",
    'pos.timeDealQuantityPlaceholder': 'Quantité',
    'pos.startTimeDeal': 'Lancer une offre flash',
    'pos.creating': 'Création en cours…',
    'pos.pastDealsSingular': '{{count}} offre passée pour ce produit.',
    'pos.pastDealsPlural': '{{count}} offres passées pour ce produit.',
    'settings.saveError': 'Impossible d\'enregistrer.',
    'settings.saved': 'Enregistré.',
    'settings.saving': 'Enregistrement…',
    'settings.saveButton': 'Enregistrer',
    'settings.merchantIdPrefix': 'ID marchand',
    'settings.webhookUrlLabel': 'URL du webhook',
    'settings.webhookUrlBody': "Nous informerons cette adresse à chaque paiement complété. Si elle ne répond pas, nous continuerons à réessayer pendant environ 3 jours.",
    'settings.followersTitle': 'Abonnés',
    'settings.loading': 'Chargement…',
    'settings.followersCountSingular': '{{count}} client suit votre boutique',
    'settings.followersCountPlural': '{{count}} clients suivent votre boutique',
    'settings.broadcastTitleLabel': 'Titre',
    'settings.broadcastTitlePlaceholder': 'Nouveautés cette semaine',
    'settings.broadcastMessageLabel': 'Message',
    'settings.broadcastMessagePlaceholder': 'Dites à vos abonnés ce qui est nouveau.',
    'settings.sending': 'Envoi en cours…',
    'settings.broadcastButton': 'Diffuser aux abonnés',
    'settings.broadcastNeedsFollower': 'Vous avez besoin d\'au moins un abonné pour envoyer une diffusion.',
    'settings.broadcastError': 'Impossible d\'envoyer cette diffusion.',
    'settings.broadcastSentSingular': 'Envoyé à {{count}} abonné.',
    'settings.broadcastSentPlural': 'Envoyé à {{count}} abonnés.',
    'settings.webhookDeliveriesLoadError': 'Impossible de charger les livraisons du webhook.',
    'settings.apiKeyGenerateError': 'Impossible de générer une clé API.',
    'settings.replayError': 'Impossible de relancer cette livraison.',
    'settings.apiIntegrationTitle': 'Intégration API',
    'settings.apiIntegrationBody': 'Pour les commerçants intégrant leurs propres systèmes à itunda.',
    'settings.generating': 'Génération en cours…',
    'settings.generateApiKeyButton': 'Générer une nouvelle clé API',
    'settings.recentDeliveriesTitle': 'Livraisons récentes du webhook',
    'settings.deliveriesEmpty': "Aucune livraison de webhook pour le moment — les livraisons apparaîtront ici dès qu'un événement déclenchera votre webhook.",
    'settings.attemptSingular': '{{count}} tentative',
    'settings.attemptPlural': '{{count}} tentatives',
    'settings.statusDelivered': 'Livrée',
    'settings.statusPending': 'En attente',
    'settings.statusExhausted': 'Épuisée',
    'settings.replaying': 'Relance en cours…',
    'settings.replayButton': 'Relancer',
    'settings.devicesLoadError': 'Impossible de charger vos appareils.',
    'settings.deviceRemoveError': 'Impossible de retirer cet appareil.',
    'settings.devicesTitle': 'Appareils',
    'settings.devicesBody': "Appareils connectés à ce compte. Un appareil doit être vérifié avant de pouvoir déplacer de l'argent.",
    'settings.devicesEmpty': 'Aucun appareil enregistré pour le moment — les appareils apparaîtront ici dès votre connexion.',
    'settings.unknownDevice': 'Appareil inconnu',
    'settings.thisDeviceSuffix': '(cet appareil)',
    'settings.deviceVerified': "✓ Vérifié — peut déplacer de l'argent",
    'settings.deviceNotVerified': '⚠ Non vérifié — connexion uniquement',
    'settings.lastSeenPrefix': 'Vu pour la dernière fois',
    'settings.removing': 'Suppression en cours…',
    'settings.removeButton': 'Retirer',
    'settings.categoryTitle': 'Catégorie',
    'settings.categoryExamplesLabel': 'ex. Rwandais, Chinois, Boulangerie, Café',
    'settings.categoryPlaceholder': 'Catégorie',
    'settings.storeSettingsTitle': 'Paramètres de la boutique',
    'settings.storePhotoUrlLabel': 'URL de la photo de la boutique',
    'settings.minOrderAmountLabel': 'Montant minimum de commande (RWF, vide = aucun)',
    'settings.cashbackRateLabel': 'Taux de cashback boosté (0-5%, vide = taux standard)',
    'settings.phoneNumberLabel': 'Numéro de téléphone (affiché sur la carte, vide = masqué)',
    'settings.openingHoursLabel': "Heures d'ouverture (affichées sur la carte, vide = masquées)",
    'settings.avgPrepTimeMinutesLabel': 'Temps de préparation moyen en minutes (vide = valeur par défaut d\'itunda)',
    'settings.acceptScheduledOrdersTitle': 'Accepter les commandes programmées',
    'settings.acceptScheduledOrdersBody': 'Permettre aux clients de choisir une heure de livraison/retrait future.',
    'settings.acceptingOrdersTitle': 'Acceptation des commandes',
    'settings.acceptingOrdersBody': 'Suspendez temporairement si vous êtes trop occupé pour accepter de nouvelles commandes.',
    'settings.on': 'Activé',
    'settings.off': 'Désactivé',
    'settings.paused': 'Suspendu',
    'settings.eatsClubTitle': 'Eats Club',
    'settings.eatsClubBody': 'Offrez la livraison gratuite aux clients ayant un abonnement Eats Club actif.',
    'settings.eatsClubParticipating': 'Participant',
    'settings.eatsClubOptIn': 'Participer',
    'settings.feeWaiverTitle': 'Exonération de frais pour petits commerçants',
    'settings.feeWaiverActiveBody': "Active -- vous ne payez aucun frais de plateforme sur les paiements que vous encaissez.",
    'settings.feeWaiverEligibleBody': 'Si votre volume de paiements sur les 30 derniers jours est faible, vous pourriez être éligible à une exonération complète des frais.',
    'settings.feeWaiverError': "Impossible de faire une demande d'exonération de frais.",
    'settings.feeWaiverApplyButton': 'Demander',
    'settings.kybSubmitError': "Impossible de soumettre pour vérification KYB.",
    'settings.kybAutoMatched': 'Votre TIN a été vérifié automatiquement.',
    'settings.kybAutoNotFound': "Nous n'avons pas encore trouvé de correspondance — un membre de l'équipe va vérifier.",
    'settings.kybAutoInvalidFormat': "Le format du TIN semblait incorrect — un membre de l'équipe va le revérifier.",
    'settings.kybAutoDefault': "Un membre de l'équipe va vérifier bientôt.",
    'settings.kybTitle': 'Vérification professionnelle (KYB)',
    'settings.kybVerifiedBadge': '✓ Vérifié',
    'settings.kybReviewingBody': 'Nous examinons les informations de votre entreprise.',
    'settings.kybTinLabel': 'TIN professionnel (9 chiffres)',
    'settings.kybSubmitting': 'Envoi en cours…',
    'settings.kybSubmitButton': 'Soumettre pour vérification',
    'settings.kybRejectedPlain': 'La soumission précédente a été refusée.',
    'settings.kybRejectedWithDetail': 'La soumission précédente a été refusée : {{detail}}',
    'deviceStepUp.verifyError': 'Impossible de vérifier cet appareil.',
    'deviceStepUp.title': '🔒 Vérifier cet appareil',
    'deviceStepUp.body': "Il s'agit d'un nouvel appareil pour votre compte. Ressaisissez votre mot de passe pour l'autoriser à déplacer de l'argent, puis réessayez.",
    'deviceStepUp.passwordPlaceholder': 'Mot de passe',
    'deviceStepUp.showPassword': 'Afficher le mot de passe',
    'deviceStepUp.hidePassword': 'Masquer le mot de passe',
    'deviceStepUp.cancel': 'Annuler',
    'deviceStepUp.verifying': 'Vérification en cours…',
    'deviceStepUp.verifyButton': "Vérifier l'appareil",
  },
};
