// Real bill-pay/airtime client -- see the backend's BillsService.kt doc comment for
// the full sourced account (real ProviderConnector rail simulation, real posted
// Transaction rows since 2026-07-16). Found with zero client anywhere on any of the 3
// platforms except Android/iOS's Saronite RN mini-app bridge (SaroniteBridge.kt's
// payBill/buyAirtime, SaroniteBrownfieldModule.swift) -- bank-mfe itself had never
// wired GET /api/v1/bills/providers|pending or POST /api/v1/bills/pay|airtime to any
// screen despite them being fully real, ledger-backed endpoints.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface BillProvider {
  id: string;
  name: string;
  category: string;
  logo: string;
  isActive: boolean;
}

export interface PendingBill {
  id: string;
  provider: string;
  amount: number;
  dueDate: string;
  status: string;
  accountNumber: string;
}

export interface BillPaymentResult {
  id: string;
  referenceNumber: string;
  amount: number;
  fee: number;
  type: string;
  status: string;
  description: string;
  completedAt: string;
}

// Real Kakao Pay 자동납부 (automatic bill payment) client -- Kakao Pay lets a user
// register a recurring bill once and have it paid automatically every cycle, "so
// customers never miss a bill," capped by a real user-set maxAmount safety ceiling
// (see the backend's BillAutoPaySetting.kt doc comment for the full sourced account).
// Found with a fresh uncalled-endpoint sweep: POST/GET/DELETE /api/v1/bills/auto-pay
// were fully built, ledger-tested, and even had a real scheduler-safety bug already
// fixed (BillAutoPayProcessor, docs Section 115/118) -- but zero client on any of the
// 3 platforms ever called them. This file wires bank-mfe's BillsView to them.
export interface BillAutoPaySetting {
  id: string;
  userId: string;
  providerId: string;
  accountNumber: string;
  maxAmount: number;
  active: boolean;
  lastPaidBillId: string | null;
  createdAt: string;
}

export const fetchBillProviders = () =>
  apiFetch<{ success: boolean; providers: BillProvider[] }>('/api/v1/bills/providers').then((r) => r.providers);

export const fetchPendingBills = () =>
  apiFetch<{ success: boolean; bills: PendingBill[] }>('/api/v1/bills/pending').then((r) => r.bills);

export const fetchAutoPaySettings = () =>
  apiFetch<{ success: boolean; autoPay: BillAutoPaySetting[] }>('/api/v1/bills/auto-pay').then((r) => r.autoPay);

export const setAutoPay = (providerId: string, accountNumber: string, maxAmount: number) =>
  apiFetch<{ success: boolean; autoPay: BillAutoPaySetting }>('/api/v1/bills/auto-pay', {
    method: 'POST',
    body: JSON.stringify({ providerId, accountNumber, maxAmount }),
  }).then((r) => r.autoPay);

export const clearAutoPay = (providerId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/bills/auto-pay?providerId=${encodeURIComponent(providerId)}`, {
    method: 'DELETE',
  });

export const payBill = (billId: string, amount: number, accountNumber?: string, provider?: string) =>
  apiFetch<{ success: boolean; message: string; transaction: BillPaymentResult }>('/api/v1/bills/pay', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ billId, amount, accountNumber, provider }),
  }).then((r) => r.transaction);

export const buyAirtime = (phoneNumber: string, amount: number, provider?: string) =>
  apiFetch<{ success: boolean; message: string; transaction: BillPaymentResult }>('/api/v1/bills/airtime', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ phoneNumber, amount, provider }),
  }).then((r) => r.transaction);
