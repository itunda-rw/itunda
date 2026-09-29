// Real "cash out at an agent" client -- see the backend's
// AgentWithdrawalAuthorizationService.kt doc comment for the full sourced account
// (a one-time, 10-minute-expiry code a customer creates and hands to an itunda
// agent, consumed exactly once for the exact amount shown). Real gap closed
// 2026-09-07 (Agents product-completeness pass): Android's AgentCashScreen.kt has
// had this since it was built, but bank-mfe never wired
// POST/GET/POST .../api/v1/account/agent-withdrawal-authorizations[/cancel] to any
// screen despite the endpoints being fully real and already used natively.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type AgentWithdrawalAuthorizationStatus = 'ACTIVE' | 'CANCELLED' | 'CONSUMED' | 'EXPIRED';

export interface AgentWithdrawalAuthorization {
  id: string;
  code: string;
  amount: number;
  expiresAt: string;
  status: AgentWithdrawalAuthorizationStatus;
  createdAt: string;
}

export const fetchAgentWithdrawalAuthorizations = () =>
  apiFetch<{ success: boolean; authorizations: AgentWithdrawalAuthorization[] }>('/api/v1/account/agent-withdrawal-authorizations')
    .then((r) => r.authorizations);

// Idempotency-Key is generated here, not per-render -- the caller is expected to
// reuse the same key across a retry of the SAME create attempt (e.g. a network
// error) and only generate a fresh one when the user changes the amount, matching
// Android's AgentCashScreen.kt's own "editing amount = new intent, reset the key"
// convention exactly.
export const createAgentWithdrawalAuthorization = (amount: number, idempotencyKey: string = randomUUID()) =>
  apiFetch<{ success: boolean; authorization: AgentWithdrawalAuthorization }>('/api/v1/account/agent-withdrawal-authorizations', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ amount }),
  }).then((r) => r.authorization);

export const cancelAgentWithdrawalAuthorization = (code: string) =>
  apiFetch<{ success: boolean; authorization: AgentWithdrawalAuthorization }>('/api/v1/account/agent-withdrawal-authorizations/cancel', {
    method: 'POST',
    body: JSON.stringify({ code }),
  }).then((r) => r.authorization);
