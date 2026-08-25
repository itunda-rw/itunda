// Real first slice of Kinyarwanda/English localization (2026-08-08) -- found missing
// entirely via Toss ecosystem research comparing itunda against Paytm/PhonePe's own
// real regional-language UX (>50% of new fintech users in a comparable market prefer
// their own language over English, per docs/DESIGN_REFERENCES.md Section 19). Before
// this, every string in every itunda client was hardcoded English -- a real gap given
// itunda's own explicit financial-inclusion mission in a market where English is
// Rwanda's third official language, not its most widely spoken one.
//
// Deliberately small and honest about its own limits, matching this codebase's
// established "real but honestly scoped" discipline (see e.g. DemoExternalBalanceService's
// own doc comment): this is infrastructure plus ONE real screen (login, itunda's own
// single highest-traffic screen per LoginPage.tsx's existing comment), not a claim that
// the whole app is translated. The `rw` strings below are a good-faith, careful
// translation -- NOT verified by a native Kinyarwanda speaker -- and should get real
// native-speaker review before being treated as production-final, the same honesty
// standard this codebase already applies to anything it can't fully verify itself.

// Real 7th-localization-pass addition (2026-08-15): French, added alongside English and
// Kinyarwanda after a fresh ecosystem-research pass (Paytm/PhonePe's own real
// regional-language findings, see docs/DESIGN_REFERENCES.md Section 19) reconfirmed the
// original gap this file's own doc comment above already named -- itunda's actual launch
// market is Rwanda, where French is a real, official, widely-used administrative and
// educational language (not just English/Kinyarwanda). Same honesty discipline as the
// `rw` block below: a good-faith, careful translation, NOT verified by a native French
// speaker, and should get real native-speaker review before being treated as
// production-final.
//
// Real fix (2026-08-26): the per-locale string tables used to live inline here, pushing
// this file past the 500-line file-size-lint guideline. Split into translations.en.ts/
// translations.rw.ts/translations.fr.ts (one file per language, same pattern as any other
// data-only split in this codebase) -- this file keeps the TranslationKey union (the
// single source of truth every locale file is type-checked against) and the merge.
import { en } from './translations.en';
import { rw } from './translations.rw';
import { fr } from './translations.fr';

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
  | 'login.createAccount'
  | 'login.connectionError'
  // Real Toss-sourced passwordless-login rollout (2026-08-24) -- see LoginPage.tsx's
  // own doc comment. 'login.password'/'login.showPassword'/'login.hidePassword' above
  // are no longer used by this screen (superseded by the real 6-digit PIN pad) --
  // left in place rather than hunted down and removed across all 3 languages, since
  // an unused translation key is harmless, unlike a missing one.
  | 'login.next'
  | 'login.enterPin'
  | 'login.checkingDevice'
  // Real second slice (2026-08-08): account overview, itunda's own second-highest-traffic
  // screen (the one every user lands on right after login) -- following the exact phased
  // rollout named in docs/DESIGN_REFERENCES.md Section 19's own "real next steps" list.
  // Several of these carry a dynamic amount/count, hence the `{{placeholder}}` support
  // added to useI18n's `t()` alongside this -- a real, common minimal-i18n pattern
  // (named placeholders a translator can reorder per-language), not string concatenation
  // that would lock every locale into English word order.
  | 'overview.netWorth'
  | 'overview.accounts'
  | 'overview.savings'
  | 'overview.loans'
  | 'overview.investments'
  | 'overview.insurance'
  | 'overview.linkedAccounts'
  | 'overview.demoBalance'
  | 'overview.unlink'
  | 'overview.linkAccountPrompt'
  | 'overview.providerNamePlaceholder'
  | 'overview.accountPhonePlaceholder'
  | 'overview.linking'
  | 'overview.linkAccount'
  | 'overview.loadError'
  | 'overview.linkError'
  | 'overview.unlinkError'
  // Real gap caught while porting this exact screen to Android (2026-08-08): this
  // specific message -- shown when the backend saves a link attempt as
  // VERIFICATION_FAILED, a real 200 response, not a thrown error (see
  // BankDashboard.tsx's own handleLink comment) -- was missed in this file's own first
  // pass over this screen. Fixed here retroactively, and on Android at the same time,
  // not left inconsistent between the two.
  | 'overview.verificationFailed'
  // Real third slice (2026-08-08): P2P transfer, itunda's own single highest-stakes
  // money-moving screen -- following the same phased rollout, one screen at a time,
  // across all 3 platforms before moving to the next.
  | 'transfer.recipientPlaceholder'
  | 'transfer.addContact'
  | 'transfer.cancel'
  | 'transfer.namePlaceholder'
  | 'transfer.phonePlaceholder'
  | 'transfer.saveContact'
  | 'transfer.noContacts'
  | 'transfer.continue'
  | 'transfer.scamWarningTitle'
  | 'transfer.scamWarningBody'
  | 'transfer.sending'
  | 'transfer.send'
  | 'transfer.newBalance'
  | 'transfer.done'
  | 'transfer.sentHeadline'
  | 'transfer.saveContactError'
  | 'transfer.sendError'
  | 'transfer.insufficientBalance'
  | 'transfer.recipientStepTitle'
  | 'transfer.recentLabel'
  | 'transfer.balanceLabel'
  | 'transfer.memoPlaceholder'
  | 'transfer.next'
  | 'transfer.confirmSendNow'
  | 'transfer.feeCovered'
  | 'transfer.toLabel'
  | 'transfer.stepRecipient'
  | 'transfer.stepAmount'
  | 'transfer.stepConfirm'
  // The real entry point INTO the transfer flow above -- a separate component
  // (AccountBalance, the account card), found while verifying the transfer screen live:
  // translating the flow itself but leaving its own trigger button in English would
  // have been the same "translated the destination, not the door" gap as the missing
  // overview.verificationFailed key. "Top up" included even though it's a real,
  // honestly-disabled non-feature (see its own title= tooltip) -- disabled doesn't mean
  // invisible, the label still renders and should still be localized.
  | 'dashboard.mainAccount'
  | 'dashboard.transfer'
  | 'dashboard.topUp'
  // Real 5th-localization-pass additions (2026-08-09) -- finishing HomeView (the
  // actual default landing tab, not OverviewView, which is a deeper tab) rather than
  // starting a whole new screen: found it was only partially localized (just
  // AccountBalance + TransferFlow) while its own load-error message, quick actions,
  // device step-up prompt, and the ReportScamLink component -- explicitly named as a
  // known gap two passes ago -- were all still English.
  | 'home.loadError'
  | 'quickActions.cards'
  | 'deviceStepUp.title'
  | 'deviceStepUp.body'
  | 'deviceStepUp.passwordPlaceholder'
  | 'deviceStepUp.showPassword'
  | 'deviceStepUp.hidePassword'
  | 'deviceStepUp.cancel'
  | 'deviceStepUp.verifying'
  | 'deviceStepUp.verify'
  | 'deviceStepUp.genericError'
  | 'scamReport.promptQuestion'
  | 'scamReport.thanks'
  | 'scamReport.reporting'
  | 'scamReport.reportLink'
  | 'home.recentActivity'
  | 'home.noTransactions'
  | 'home.unusuallyLarge'
  // Real 6th-localization-pass additions (2026-08-09) -- the remaining HomeView cards
  // named as open follow-ups two passes ago: DiscoverSection, MiniAccountCard,
  // ScheduledTransfersCard, AutoTransfersCard, RequestMoneyCard, AutoTopUpCard.
  | 'discover.title'
  | 'discover.new'
  | 'coopRail.title'
  | 'coopRail.subtitle'
  | 'coopRail.sacco.title'
  | 'coopRail.sacco.subtitle'
  | 'coopRail.ikimina.title'
  | 'coopRail.ikimina.subtitle'
  | 'coopRail.motoOwnership.title'
  | 'coopRail.motoOwnership.subtitle'
  | 'coopRail.harvestAdvance.title'
  | 'coopRail.harvestAdvance.subtitle'
  | 'coopRail.seeAll'
  | 'youthAccount.title'
  | 'youthAccount.cancel'
  | 'youthAccount.addMoney'
  | 'youthAccount.ageIneligible'
  | 'youthAccount.openError'
  | 'youthAccount.birthDateError'
  | 'youthAccount.depositError'
  | 'youthAccount.description'
  | 'youthAccount.opening'
  | 'youthAccount.open'
  | 'youthAccount.birthDatePrompt'
  | 'youthAccount.checking'
  | 'youthAccount.checkEligibility'
  | 'youthAccount.amountPlaceholder'
  | 'youthAccount.adding'
  | 'youthAccount.add'
  | 'scheduledTransfers.title'
  | 'scheduledTransfers.cancel'
  | 'scheduledTransfers.schedule'
  | 'scheduledTransfers.recipientPlaceholder'
  | 'scheduledTransfers.amountPlaceholder'
  | 'scheduledTransfers.descriptionPlaceholder'
  | 'scheduledTransfers.scheduling'
  | 'scheduledTransfers.scheduleButton'
  | 'scheduledTransfers.createError'
  | 'scheduledTransfers.cancelError'
  | 'scheduledTransfers.noTransfers'
  | 'scheduledTransfers.statusScheduled'
  | 'scheduledTransfers.statusSent'
  | 'scheduledTransfers.statusCancelled'
  | 'scheduledTransfers.statusFailed'
  | 'delayedTransfers.title'
  | 'delayedTransfers.subtitle'
  | 'delayedTransfers.new'
  | 'delayedTransfers.sendingTo'
  | 'delayedTransfers.sendButton'
  | 'delayedTransfers.createError'
  | 'delayedTransfers.cancelError'
  | 'delayedTransfers.noTransfers'
  | 'delayedTransfers.recipientFallback'
  | 'delayedTransfers.releasesIn'
  | 'delayedTransfers.statusPending'
  | 'delayedTransfers.statusCompleted'
  | 'delayedTransfers.statusCancelled'
  | 'autoTransfers.title'
  | 'autoTransfers.cancel'
  | 'autoTransfers.setUp'
  | 'autoTransfers.recipientPlaceholder'
  | 'autoTransfers.amountPlaceholder'
  | 'autoTransfers.weekly'
  | 'autoTransfers.monthly'
  | 'autoTransfers.dayOfMonth'
  | 'autoTransfers.descriptionPlaceholder'
  | 'autoTransfers.settingUp'
  | 'autoTransfers.setUpButton'
  | 'autoTransfers.createError'
  | 'autoTransfers.toggleError'
  | 'autoTransfers.cancelError'
  | 'autoTransfers.noTransfers'
  | 'autoTransfers.weeklyLabel'
  | 'autoTransfers.monthlyLabel'
  | 'autoTransfers.statusActive'
  | 'autoTransfers.statusPaused'
  | 'autoTransfers.statusCancelled'
  | 'autoTransfers.pause'
  | 'autoTransfers.resume'
  | 'autoTransfers.cancelAction'
  | 'weekday.monday'
  | 'weekday.tuesday'
  | 'weekday.wednesday'
  | 'weekday.thursday'
  | 'weekday.friday'
  | 'weekday.saturday'
  | 'weekday.sunday'
  | 'requestMoney.title'
  | 'requestMoney.cancel'
  | 'requestMoney.newRequest'
  | 'requestMoney.amountPlaceholder'
  | 'requestMoney.whatsItFor'
  | 'requestMoney.creating'
  | 'requestMoney.createButton'
  | 'requestMoney.shareCode'
  | 'requestMoney.payCodePlaceholder'
  | 'requestMoney.paying'
  | 'requestMoney.pay'
  | 'requestMoney.createError'
  | 'requestMoney.payError'
  | 'requestMoney.myRequests'
  | 'requestMoney.statusPending'
  | 'requestMoney.statusPaid'
  | 'requestMoney.statusExpired'
  | 'autoTopUp.title'
  | 'autoTopUp.cancel'
  | 'autoTopUp.edit'
  | 'autoTopUp.setUp'
  | 'autoTopUp.linkFirst'
  | 'autoTopUp.selectAccount'
  | 'autoTopUp.thresholdPlaceholder'
  | 'autoTopUp.topUpPlaceholder'
  | 'autoTopUp.maxPerDay'
  | 'autoTopUp.saving'
  | 'autoTopUp.save'
  | 'autoTopUp.saveError'
  | 'autoTopUp.on'
  | 'autoTopUp.off'
  | 'autoTopUp.summaryLine'
  | 'autoTopUp.upToPerDay'
  | 'autoTopUp.turnOff'
  | 'autoTopUp.turnOn'
  | 'autoTopUp.checking'
  | 'autoTopUp.checkNow'
  | 'autoTopUp.checkError'
  | 'common.loadError'
  | 'common.actionError';

export const translations: Record<Locale, Record<TranslationKey, string>> = { en, rw, fr };
