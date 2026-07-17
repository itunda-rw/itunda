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

/** Mirrors the response of `GET /rewards/referral`
 * (services/backend/rewards's RewardsController.referral). */
export interface ReferralInfo {
  referralCode: string | null;
  referredCount: number;
  completedReferralCount: number;
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
  getReferralInfo(): Promise<ReferralInfo>;
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
