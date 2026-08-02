// Real Rwanda moto-taxi ownership savings-to-loan plan client -- see the backend's
// MotoOwnershipService.kt doc comment for the full sourced account. A real ~600,000
// RWF entry-level moto-taxi bike is a documented purchase price (Anadolu Agency, 14
// May 2021 -- profiles a rider who saved for years to buy her own bike after paying
// daily rent to a bike owner). Rent-to-own is a proven-relevant mechanic in this
// exact sector (Frontier Tech Hub's Kigali e-moto pilot: Ampersand's rent-to-own
// model increased driver revenue 78%/month; WeeTracker/WEF coverage of the same).
// This fills the gap left by Rwanda's dissolved taxi-moto cooperatives (Africa-Press,
// 2026). Honest v1 limitation: once converted to a loan, this is an UNSECURED
// facility -- itunda has no path to a real chattel lien or RURA vehicle-registry
// hold, so it cannot repossess the bike or verify it was actually purchased.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type MotoOwnershipPlanStatus = 'SAVING' | 'LOAN_ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface MotoOwnershipPlan {
  id: string;
  userId: string;
  bikePrice: number;
  downPaymentTarget: number;
  savedAmount: number;
  dailyContribution: number;
  loanOutstanding: number;
  status: MotoOwnershipPlanStatus;
  lastAutoContributionAt: string | null;
  createdAt: string;
}

export const createMotoOwnershipPlan = (bikePrice: number, dailyContribution: number) =>
  apiFetch<{ success: boolean; plan: MotoOwnershipPlan }>('/api/v1/moto-ownership/plans', {
    method: 'POST',
    body: JSON.stringify({ bikePrice, dailyContribution }),
  }).then((r) => r.plan);

export const contributeToMotoOwnershipPlan = (planId: string, amount: number) =>
  apiFetch<{ success: boolean; plan: MotoOwnershipPlan }>(`/api/v1/moto-ownership/plans/${planId}/contribute`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.plan);

export const cancelMotoOwnershipPlan = (planId: string) =>
  apiFetch<{ success: boolean; plan: MotoOwnershipPlan }>(`/api/v1/moto-ownership/plans/${planId}/cancel`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.plan);

export const convertMotoOwnershipPlanToLoan = (planId: string) =>
  apiFetch<{ success: boolean; plan: MotoOwnershipPlan }>(`/api/v1/moto-ownership/plans/${planId}/convert-to-loan`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.plan);

export const repayMotoOwnershipPlan = (planId: string, amount: number) =>
  apiFetch<{ success: boolean; plan: MotoOwnershipPlan }>(`/api/v1/moto-ownership/plans/${planId}/repay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.plan);

export const fetchMyMotoOwnershipPlans = () =>
  apiFetch<{ success: boolean; plans: MotoOwnershipPlan[] }>('/api/v1/moto-ownership/plans/me').then((r) => r.plans);
