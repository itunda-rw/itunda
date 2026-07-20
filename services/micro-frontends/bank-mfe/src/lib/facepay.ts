// Real Face Pay -- see the backend's FacePayEnrollment.kt doc comment for the full
// account (closes docs/TOSS_PARITY_MATRIX.md's Face Pay row, modeled on Toss's real,
// sourced Face Pay product). The backend has been fully real since 2026-07-13
// (enroll/revoke/status/collect, its own real ledger channel), but had zero UI
// touchpoint anywhere in bank-mfe until now.

import { apiFetch } from './api';
import type { CollectPaymentResult } from './shopping';

export interface FacePayEnrollment {
  id: string;
  userId: string;
  active: boolean;
  enrolledAt: string;
  revokedAt: string | null;
}

export const fetchFacePayStatus = () =>
  apiFetch<{ success: boolean; enrolled: boolean; enrollment: FacePayEnrollment | null }>('/api/v1/facepay/status');

export const enrollFacePay = () =>
  apiFetch<{ success: boolean; enrollment: FacePayEnrollment }>('/api/v1/facepay/enroll', { method: 'POST' }).then((r) => r.enrollment);

export const revokeFacePay = () =>
  apiFetch<{ success: boolean; enrollment: FacePayEnrollment }>('/api/v1/facepay/revoke', { method: 'POST' }).then((r) => r.enrollment);

export const collectWithFacePay = (intentId: string) =>
  apiFetch<{ success: boolean } & CollectPaymentResult>(`/api/v1/facepay/collect/${intentId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  });
