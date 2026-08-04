/**
 * The native-module contract for Saronite's brownfield bridge — one module,
 * defined once, that the host Android/iOS app must implement.
 *
 * This mirrors the real pattern found in Toss's open-source Granite repo:
 * a single `GraniteBrownfieldModule` spec (there generated from a private
 * `brick-module`/`brick-codegen` TypeScript-to-Kotlin/Swift tool) exposing
 * `closeView()`, `getSchemeUri()`, and an `onVisibilityChanged` event, with
 * JS call sites resolving it via `NativeModules.GraniteBrownfieldModule`.
 *
 * `brick-module`/`brick-codegen` themselves are not public, so this spec is
 * hand-written against React Native's real, public `NativeModules` /
 * `NativeEventEmitter` API instead of generated — same shape, honestly
 * different tooling.
 */
import { NativeEventEmitter, NativeModules } from 'react-native';
import type { EmitterSubscription } from 'react-native';

export interface WalletSummary {
  id: string;
  type: string;
  name: string;
  number: string;
  balance: number;
  currency: string;
  icon: string;
  connected: boolean;
}

/** Shape returned by itunda's real `GET /wallet/balance` (see
 * backend/src/controllers/wallet.controller.ts:getAggregatedBalance). */
export interface WalletBalanceResult {
  totalBalance: number;
  currency: string;
  wallets: WalletSummary[];
}

/** Mirrors a single bill from `GET /bills/pending`
 * (backend/src/controllers/bills.controller.ts:getPendingBills). */
export interface PendingBill {
  id: string;
  provider: string;
  amount: number;
  dueDate: string;
  status: string;
  accountNumber: string;
}

export interface PendingBillsResult {
  bills: PendingBill[];
}

/** Mirrors the transaction returned by `POST /bills/pay`
 * (backend/src/controllers/bills.controller.ts:payBill). */
export interface PayBillResult {
  message: string;
  transactionId: string;
  referenceNumber: string;
  status: string;
}

/** Mirrors a single task from `GET /rewards/tasks`
 * (backend/src/controllers/rewards.controller.ts:getRewardTasks). */
export interface RewardTask {
  id: string;
  title: string;
  subtitle: string;
  rewardAmount: number;
  claimed: boolean;
  claimedAt?: string;
}

export interface RewardTasksResult {
  tasks: RewardTask[];
  rewardsTotal: number;
}

/** Mirrors the response of `POST /rewards/claim`
 * (backend/src/controllers/rewards.controller.ts:claimReward). */
export interface ClaimRewardResult {
  message: string;
  rewardAmount: number;
  newBalance: number;
}

/** Mirrors a single plan from `GET /insurance/plans`
 * (services/backend/insurance's InsuranceController.getPlans). */
export interface InsurancePlan {
  id: string;
  name: string;
  category: string;
  provider: string;
  monthlyPremium: number;
  coverageAmount: number;
  description: string;
  features: string[];
  rating: number;
  enrolledCount: number;
  color: string;
}

export interface InsurancePlansResult {
  plans: InsurancePlan[];
}

/** Mirrors a single policy from `GET /insurance/my-policies`
 * (services/backend/insurance's InsuranceController.getMyPolicies). */
export interface InsurancePolicy {
  id: string;
  planId: string;
  planName: string;
  category: string;
  status: string;
  startDate: string;
  endDate: string;
  monthlyPremium: number;
  nextPaymentDate: string;
  policyNumber: string;
}

export interface MyPoliciesResult {
  policies: InsurancePolicy[];
}

/** Mirrors the response of `POST /insurance/enroll`
 * (services/backend/insurance's InsuranceController.enrollInPlan). */
export interface EnrollInsuranceResult {
  message: string;
  policy: InsurancePolicy;
}

/** Real Ejo Heza ya Moto-style premium savings fund -- mirrors a single fund from
 * `GET /insurance/premium-funds` (services/backend/insurance's
 * InsuranceController.fundMap). Lets a user save toward a specific policy's next
 * premium ahead of time, so the backend's recurring collection scheduler can draw on
 * it instead of lapsing the policy. */
export interface InsurancePremiumFund {
  id: string;
  policyId: string;
  targetAmount: number;
  currentAmount: number;
  dailyContribution: number;
  status: 'active' | 'cancelled';
  createdAt: string;
}

/** Mirrors the response of `POST /insurance/policies/{policyId}/premium-fund`. */
export interface CreatePremiumFundResult {
  success: boolean;
  fund: InsurancePremiumFund;
}

/** Mirrors the response of `POST /insurance/premium-funds/{fundId}/contribute`. */
export interface ContributeToFundResult {
  success: boolean;
  fund: InsurancePremiumFund;
}

/** Mirrors the response of `POST /insurance/premium-funds/{fundId}/cancel`. */
export interface CancelFundResult {
  success: boolean;
  fund: InsurancePremiumFund;
}

/** Mirrors the response of `GET /insurance/premium-funds`. */
export interface MyPremiumFundsResult {
  success: boolean;
  funds: InsurancePremiumFund[];
}

/** Mirrors a single claim from `GET /insurance/claims` / `POST /insurance/claims`
 * (services/backend/insurance's InsuranceController.submitClaim/getMyClaims -- the raw
 * InsuranceClaim entity, no remapping). decisionReason is only set once an admin has
 * decided it (InsuranceClaimsAdminController.decideClaim). */
export interface InsuranceClaim {
  id: string;
  policyId: string;
  description: string;
  amount: number;
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  submittedAt: string;
  decisionReason: string | null;
}

/** Mirrors the response of `POST /insurance/claims`. */
export interface SubmitClaimResult {
  success: boolean;
  claim: InsuranceClaim;
}

/** Mirrors the response of `GET /insurance/claims`. */
export interface MyClaimsResult {
  success: boolean;
  claims: InsuranceClaim[];
}

/** Mirrors the response of `GET /rewards/referral`
 * (services/backend/rewards's RewardsController.referral). */
export interface ReferralInfo {
  referralCode: string | null;
  referredCount: number;
  completedReferralCount: number;
}

/** Real Toss 만보기 (walking rewards) -- mirrors the response of `POST /rewards/steps`
 * (services/backend/rewards's RewardsController.reportSteps /
 * StepRewardService.reportSteps). `steps` is honestly client-reported (see
 * StepRewardService's own doc comment on the backend for the sourced boundary: a real
 * sanity ceiling, not a real anti-spoofing measure). `newlyEarnedTiers` are the real
 * step thresholds (1000/5000/10000) newly crossed by THIS report, matching
 * StepRewardTier.stepsRequired exactly. */
export interface StepReportResult {
  steps: number;
  newlyEarnedTiers: number[];
  newlyEarnedAmount: number;
  totalEarnedToday: number;
}

/** Mirrors the response of `GET /rewards/steps/today`. */
export interface TodayStepsResult {
  steps: number;
}

/** Mirrors the response of `PUT /auth/profile/photo` and
 * `POST /auth/profile/verify-email/confirm` (services/backend/auth's
 * AuthController). Only the fields task_profile eligibility actually needs
 * on the mini-app side -- the rest of PublicUser isn't relevant here. */
export interface ProfileResult {
  profilePhotoUrl: string | null;
  emailVerified: boolean;
}

export interface SaroniteBrownfieldModuleConstants {
  /** The custom URL scheme the host app registered for returning to this mini-app. */
  schemeUri: string;
}

export interface VisibilityChangedEvent {
  visible: boolean;
}

export interface SaroniteBrownfieldModuleSpec {
  getConstants(): SaroniteBrownfieldModuleConstants;
  closeView(): Promise<void>;
  openURL(url: string): Promise<void>;
  getWalletBalance(): Promise<WalletBalanceResult>;
  getPendingBills(): Promise<PendingBillsResult>;
  payBill(
    billId: string,
    amount: number,
    accountNumber: string,
    provider: string,
  ): Promise<PayBillResult>;
  getRewardTasks(): Promise<RewardTasksResult>;
  claimRewardTask(taskId: string): Promise<ClaimRewardResult>;
  getInsurancePlans(): Promise<InsurancePlansResult>;
  getMyPolicies(): Promise<MyPoliciesResult>;
  enrollInsurance(planId: string): Promise<EnrollInsuranceResult>;
  createPremiumFund(policyId: string, dailyContribution: number): Promise<CreatePremiumFundResult>;
  contributeToFund(fundId: string, amount: number): Promise<ContributeToFundResult>;
  cancelFund(fundId: string): Promise<CancelFundResult>;
  getMyPremiumFunds(): Promise<MyPremiumFundsResult>;
  submitClaim(policyId: string, description: string, amount: number): Promise<SubmitClaimResult>;
  getMyClaims(): Promise<MyClaimsResult>;
  getReferralInfo(): Promise<ReferralInfo>;
  reportSteps(steps: number): Promise<StepReportResult>;
  getTodaySteps(): Promise<TodayStepsResult>;
  updateProfilePhoto(profilePhotoUrl: string): Promise<ProfileResult>;
  requestEmailVerification(): Promise<void>;
  confirmEmailVerification(token: string): Promise<ProfileResult>;
  /** Required by NativeEventEmitter on the old native-modules architecture. */
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const LINKING_ERROR =
  "Saronite's native module 'SaroniteBrownfieldModule' is not linked. " +
  'Make sure the host app has installed SaronitePackage and rebuilt the native app.';

const NativeSaronite = NativeModules.SaroniteBrownfieldModule as
  | SaroniteBrownfieldModuleSpec
  | undefined;

export const SaroniteBrownfieldModule: SaroniteBrownfieldModuleSpec = NativeSaronite
  ? NativeSaronite
  : (new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      },
    ) as SaroniteBrownfieldModuleSpec);

const saroniteEventEmitter = new NativeEventEmitter(
  NativeModules.SaroniteBrownfieldModule,
);

export function onVisibilityChanged(
  listener: (event: VisibilityChangedEvent) => void,
): EmitterSubscription {
  return saroniteEventEmitter.addListener('onVisibilityChanged', listener);
}
