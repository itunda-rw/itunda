// Real Itunda cash-agent operator console -- see the backend's AgentOperatorController.kt
// doc comment: "Store-facing API: the operator's JWT determines the agent; callers never
// supply an agent id." Distinct from lib/maps.ts's own customer-facing nearby-agent
// discovery and AgentCashScreen.kt's customer-facing withdrawal-code creation (Android) --
// this is the STAFF side, real till balance + real cash-in/cash-out + real till
// reconciliation, found with zero client anywhere on any of the 3 platforms despite the
// backend (AgentService.kt) being fully real and ledger-backed.

import { apiFetch, ApiError } from './api';
import { randomUUID } from './uuid';

export interface AgentOperatorInfo {
  id: string;
  agentId: string;
  userId: string;
  isActive: boolean;
  createdAt: string;
}

export interface AgentTillReconciliation {
  id: string;
  agentId: string;
  businessDate: string;
  expectedCash: number;
  countedCash: number;
  variance: number;
  submittedByUserId: string;
  status: string;
  reviewedByUserId: string | null;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export interface AgentTillSnapshot {
  agentId: string;
  agentName: string;
  expectedCash: number;
  todayCashIn: number;
  todayCashOut: number;
  reconciliation: AgentTillReconciliation | null;
}

export interface AgentActivityItem {
  id: string;
  type: string;
  amount: number;
  receiptNumber: string;
  ledgerTransactionId: string;
  createdAt: string;
}

export interface AgentCashResult {
  newBalance: number;
  operatorCommission: number;
}

export const fetchMyAgentOperator = () =>
  apiFetch<{ success: boolean; operator: AgentOperatorInfo }>('/api/v1/agent/me').then((r) => r.operator);

export const fetchAgentTill = () =>
  apiFetch<{ success: boolean; till: AgentTillSnapshot }>('/api/v1/agent/till').then((r) => r.till);

export const fetchAgentActivity = (limit = 30) =>
  apiFetch<{ success: boolean; activity: AgentActivityItem[] }>(`/api/v1/agent/activity?limit=${limit}`).then((r) => r.activity);

export const agentCashIn = (accountNumber: string, amount: number, receiptNumber: string) =>
  apiFetch<{ success: boolean } & AgentCashResult>('/api/v1/agent/cash-ins', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ accountNumber, amount, receiptNumber }),
  });

export const agentCashOut = (accountNumber: string, amount: number, receiptNumber: string, authorizationCode: string) =>
  apiFetch<{ success: boolean } & AgentCashResult>('/api/v1/agent/cash-outs', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ accountNumber, amount, receiptNumber, authorizationCode }),
  });

// Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory) --
// a lost response after a successful submission would previously resubmit here
// and hit TillReconciliationAlreadySubmittedException on the retry, a real
// cash-handling confusion risk (highest-priority item this thread names).
export const submitAgentTillCount = (countedCash: number) =>
  apiFetch<{ success: boolean; reconciliation: AgentTillReconciliation }>('/api/v1/agent/till-reconciliations', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ countedCash }),
  }).then((r) => r.reconciliation);

export const isNotAgentOperatorError = (err: unknown) => err instanceof ApiError && err.code === 'AGENT_OPERATOR_NOT_AUTHORIZED';
