import { apiFetch } from './api';

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) daily mission row --
// see backend ShoppingMissionService.kt's own doc comment: every mission pays real RWF
// straight into the real account via LedgerService, no fabricated points currency.
// Same endpoints Android's ShopScreen.kt already ports (docs/DESIGN_REFERENCES.md
// Section 53).

export interface ShoppingMission {
  type: string;
  label: string;
  rewardAmount: number;
  completedToday: boolean;
  claimedEver: boolean;
}

export interface SpinOutcome {
  amount: number;
  odds: number;
}

export interface ShoppingMissionStatus {
  missions: ShoppingMission[];
  spinOutcomes: SpinOutcome[];
}

export const fetchShoppingMissionStatus = () =>
  apiFetch<{ success: boolean; missions: ShoppingMission[]; spinOutcomes: SpinOutcome[] }>('/api/v1/shopping/points').then((r) => ({
    missions: r.missions,
    spinOutcomes: r.spinOutcomes,
  }));

export const completeShoppingMission = (type: string) =>
  apiFetch<{ success: boolean; type: string; amountEarned: number; newAccountBalance: number }>(
    `/api/v1/shopping/points/missions/${type}/complete`,
    { method: 'POST' },
  );
