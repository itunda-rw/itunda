// Real insurance plans/enroll/claims client -- see the backend's InsuranceService.kt
// doc comment for the full sourced account. Found with zero bank-mfe UI beyond a
// single read-only summary line ("Insurance: N active plan(s)" in OverviewView) --
// browse plans, enroll, my policies, and file/view claims (GET/POST /api/v1/insurance/*)
// had never been wired to any bank-mfe screen despite being fully real, ledger-backed
// endpoints. Android/iOS already have plans/enroll/my-policies via the Saronite RN
// mini-app bridge (SaroniteBridge.kt/SaroniteBrownfieldModule.swift), but claims
// filing/viewing has zero client anywhere on any of the 3 platforms -- honestly still
// open there, named as a follow-up rather than silently left unbuilt.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

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

export interface InsuranceClaim {
  id: string;
  policyId: string;
  description: string;
  amount: number;
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  decisionReason: string | null;
}

export const fetchInsurancePlans = () =>
  apiFetch<{ success: boolean; plans: InsurancePlan[] }>('/api/v1/insurance/plans').then((r) => r.plans);

export const fetchMyPolicies = () =>
  apiFetch<{ success: boolean; policies: InsurancePolicy[] }>('/api/v1/insurance/my-policies').then((r) => r.policies);

export const enrollInPlan = (planId: string) =>
  apiFetch<{ success: boolean; message: string; policy: InsurancePolicy }>('/api/v1/insurance/enroll', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ planId }),
  }).then((r) => r.policy);

export const submitClaim = (policyId: string, description: string, amount: number) =>
  apiFetch<{ success: boolean; claim: InsuranceClaim }>('/api/v1/insurance/claims', {
    method: 'POST',
    body: JSON.stringify({ policyId, description, amount }),
  }).then((r) => r.claim);

export const fetchMyClaims = () =>
  apiFetch<{ success: boolean; claims: InsuranceClaim[] }>('/api/v1/insurance/claims').then((r) => r.claims);
