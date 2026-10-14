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
import { sanitizeSaroniteDebugValue, type SaroniteDebugMessage, type SaroniteDebugTransport } from '@itunda/saronite-debug-protocol';

type SaroniteDebugGlobals = typeof globalThis & {
  __saroniteDebugTransport?: SaroniteDebugTransport;
  __SARONITE_DEBUG_BUILD__?: boolean;
};

const debugGlobals = globalThis as SaroniteDebugGlobals;

function emitNativeDebug(
  message: Omit<Extract<SaroniteDebugMessage, { type: 'log' }>, 'version'>,
): void {
  if (debugGlobals.__SARONITE_DEBUG_BUILD__ !== true) return;
  debugGlobals.__saroniteDebugTransport?.send({ version: 1, ...message });
}
import type {
  WalletSummary,
  WalletBalanceResult,
  PendingBill,
  PendingBillsResult,
  PayBillResult,
  BuyAirtimeResult,
  BillProvider,
  BillProvidersResult,
  BillAutoPaySetting,
  AutoPaySettingsResult,
  SetAutoPayResult,
  RewardTask,
  RewardTasksResult,
  ClaimRewardResult,
  InsurancePlan,
  InsurancePlansResult,
  InsurancePolicy,
  MyPoliciesResult,
  EnrollInsuranceResult,
  InsurancePremiumFund,
  CreatePremiumFundResult,
  ContributeToFundResult,
  CancelFundResult,
  MyPremiumFundsResult,
  InsuranceClaim,
  SubmitClaimResult,
  MyClaimsResult,
  WeatherIndexCropType,
  CropIndexCatalogEntry,
  CropIndexCatalogResult,
  WeatherIndexPolicy,
  MyCropIndexPoliciesResult,
  CropIndexPolicyResult,
  SeasonRainfallIndex,
  CropIndexSeasonIndexResult,
  ReferralInfo,
  StepRewardTierInfo,
  StepReportResult,
  TodayStepsResult,
  ProfileResult,
} from './types';
export * from './types';
import type { EmitterSubscription } from 'react-native';

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
  getBillProviders(): Promise<BillProvidersResult>;
  buyAirtime(
    phoneNumber: string,
    amount: number,
    provider: string,
  ): Promise<BuyAirtimeResult>;
  getAutoPaySettings(): Promise<AutoPaySettingsResult>;
  setAutoPay(
    providerId: string,
    accountNumber: string,
    maxAmount: number,
  ): Promise<SetAutoPayResult>;
  clearAutoPay(providerId: string): Promise<void>;
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
  getCropIndexCatalog(): Promise<CropIndexCatalogResult>;
  getMyCropIndexPolicies(): Promise<MyCropIndexPoliciesResult>;
  enrollCropIndexPolicy(
    cropType: WeatherIndexCropType,
    district: string,
    season: string,
    insuredAmount: number,
  ): Promise<CropIndexPolicyResult>;
  cancelCropIndexPolicy(policyId: string): Promise<CropIndexPolicyResult>;
  getCropIndexSeasonIndex(district: string, season: string): Promise<CropIndexSeasonIndexResult>;
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

const NativeOrMissingSaronite: SaroniteBrownfieldModuleSpec = NativeSaronite
  ? NativeSaronite
  : (new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      },
    ) as SaroniteBrownfieldModuleSpec);

const CAPABILITY_BY_METHOD: Record<string, string> = {
  closeView: 'navigation', openURL: 'navigation',
  getWalletBalance: 'storage', getPendingBills: 'payment', payBill: 'payment',
  getBillProviders: 'payment', buyAirtime: 'payment', getAutoPaySettings: 'payment',
  setAutoPay: 'payment', clearAutoPay: 'payment', getRewardTasks: 'game',
  claimRewardTask: 'game', getInsurancePlans: 'insurance', getMyPolicies: 'insurance',
  enrollInsurance: 'insurance', createPremiumFund: 'insurance', contributeToFund: 'insurance',
  cancelPremiumFund: 'insurance', getMyPremiumFunds: 'insurance', submitClaim: 'insurance',
  getMyClaims: 'insurance', getCropIndexCatalog: 'insurance', getMyCropIndexPolicies: 'insurance',
  enrollCropIndexPolicy: 'insurance', cancelCropIndexPolicy: 'insurance',
  getCropIndexSeasonIndex: 'insurance', getReferralInfo: 'partner', reportSteps: 'game',
  getTodaySteps: 'game', updateProfilePhoto: 'profile', requestEmailVerification: 'auth',
  confirmEmailVerification: 'auth',
};

const INSPECTABLE_METHODS = new Set<string>([
  'closeView', 'openURL', 'getWalletBalance', 'getPendingBills', 'payBill',
  'getBillProviders', 'buyAirtime', 'getAutoPaySettings', 'setAutoPay',
  'clearAutoPay', 'getRewardTasks', 'claimRewardTask', 'getInsurancePlans',
  'getMyPolicies', 'enrollInsurance', 'createPremiumFund', 'contributeToFund',
  'cancelPremiumFund', 'getMyPremiumFunds', 'submitClaim', 'getMyClaims',
  'getCropIndexCatalog', 'getMyCropIndexPolicies', 'enrollCropIndexPolicy',
  'cancelCropIndexPolicy', 'getCropIndexSeasonIndex', 'getReferralInfo',
  'reportSteps', 'getTodaySteps', 'updateProfilePhoto', 'requestEmailVerification',
  'confirmEmailVerification',
]);

const devtools = (): { record(log: {
  direction: 'request' | 'response' | 'event';
  capability: string;
  method: string;
  payload?: unknown;
  durationMs?: number;
  error?: string;
}): void } | undefined =>
  typeof globalThis !== 'undefined' ? (globalThis as typeof globalThis & {
    __saronite?: { record: (log: {
      direction: 'request' | 'response' | 'event';
      capability: string;
      method: string;
      payload?: unknown;
      durationMs?: number;
      error?: string;
    }) => void };
  }).__saronite : undefined;

export const SaroniteBrownfieldModule: SaroniteBrownfieldModuleSpec = new Proxy(
  NativeOrMissingSaronite,
  {
    get(target, property, receiver) {
      const value = Reflect.get(target, property, receiver);
      if (typeof property !== 'string' || !INSPECTABLE_METHODS.has(property) || typeof value !== 'function') {
        return value;
      }
      return (...args: unknown[]) => {
        const inspector = devtools();
        const started = Date.now();
        const capability = CAPABILITY_BY_METHOD[property] ?? 'runtime';
        const requestPayload = sanitizeSaroniteDebugValue(args);
        inspector?.record({ direction: 'request', capability, method: property, payload: requestPayload });
        emitNativeDebug({ type: 'log', id: `native-${started}`, timestamp: started, direction: 'request', capability, method: property, payload: requestPayload });
        const result = (value as (...input: unknown[]) => unknown).apply(target, args);
        if (result && typeof (result as Promise<unknown>).then === 'function') {
          return (result as Promise<unknown>).then(
            (response) => {
              const durationMs = Date.now() - started;
              const responsePayload = sanitizeSaroniteDebugValue(response);
              inspector?.record({ direction: 'response', capability, method: property, payload: responsePayload, durationMs });
              emitNativeDebug({ type: 'log', id: `native-${started}-response`, timestamp: Date.now(), direction: 'response', capability, method: property, payload: responsePayload, durationMs });
              return response;
            },
            (error) => {
              const message = error instanceof Error ? error.message : String(error);
              const durationMs = Date.now() - started;
              inspector?.record({ direction: 'response', capability, method: property, durationMs, error: message });
              emitNativeDebug({ type: 'log', id: `native-${started}-error`, timestamp: Date.now(), direction: 'response', capability, method: property, durationMs, error: String(sanitizeSaroniteDebugValue(message)) });
              throw error;
            },
          );
        }
        const durationMs = Date.now() - started;
        const responsePayload = sanitizeSaroniteDebugValue(result);
        inspector?.record({ direction: 'response', capability, method: property, payload: responsePayload, durationMs });
        emitNativeDebug({ type: 'log', id: `native-${started}-response`, timestamp: Date.now(), direction: 'response', capability, method: property, payload: responsePayload, durationMs });
        return result;
      };
    },
  },
);

const saroniteEventEmitter = new NativeEventEmitter(
  NativeModules.SaroniteBrownfieldModule,
);

export function onVisibilityChanged(
  listener: (event: VisibilityChangedEvent) => void,
): EmitterSubscription {
  return saroniteEventEmitter.addListener('onVisibilityChanged', listener);
}
