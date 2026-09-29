import { apiFetch } from './api';

// Split out of queues.ts (2026-09-07, Bills product-completeness pass) once that
// file crossed the file-size-lint 500-line guideline -- same real "code that
// changes together lives together" extraction merchantAdminQueues.ts already
// established. Everything here is OverviewView.tsx's own concern (system-wide
// dashboard/rail-health/diagnostics), not any per-item review queue.

// Real system overview + per-rail health (SystemController.kt on the backend) -- both
// compute from real persisted data (today's settled transaction volume/count, active
// consent count, real attempt-tracked rail success rate/latency via
// ProviderHealthTracker), not mocks, but had zero caller anywhere in any client until
// found via a fresh endpoint-coverage sweep, same pattern as MarketplaceEscrowDispute
// in queues.ts (item 125).
export interface SystemDashboard {
  generatedAt: string;
  country: string;
  currency: string;
  operations: { todayVolume: number; todayCompletedTransactionCount: number };
  operatingLayer: { activeConsents: number };
}

export interface PaymentRail {
  railId: string;
  displayName: string;
  totalAttempts: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  avgLatencyMs: number;
  status: 'HEALTHY' | 'INCIDENT';
}

export const fetchSystemDashboard = () =>
  apiFetch<{ success: boolean; dashboard: SystemDashboard }>('/api/v1/system/dashboard').then((r) => r.dashboard);

export const fetchPaymentRails = () =>
  apiFetch<{ success: boolean; rails: PaymentRail[] }>('/api/v1/system/rails').then((r) => r.rails);

export interface MtnMomoConnectivityResult {
  referenceId: string;
  status: string;
  financialTransactionId: string | null;
  latencyMs: number;
}

// Real, live external diagnostic (SystemController.testMtnMomoConnectivity) -- see
// MtnMomoSandboxClient's own doc comment for why this is deliberately NOT wired into
// any real user-facing transfer/bill flow, ADMIN-gated the same way every other
// /api/v1/system/** route already is. Found via scripts/uncalled-endpoint-sweep.py:
// fully built, zero caller anywhere -- an admin had no way to actually run this check
// except raw curl/Postman.
export const testMtnMomoConnectivity = () =>
  apiFetch<{ success: boolean } & MtnMomoConnectivityResult>('/api/v1/system/mtn-momo/connectivity-test', {
    method: 'POST',
  });

export interface AutoPaySweepResult {
  processed: Array<{ id: string; referenceNumber: string; amount: number; providerId: string; billId: string }>;
}

// Real, orphaned admin sweep (BillsController.processAutoPayments /
// BillAutoPayProcessor.process()) -- real money movement across every user with a
// due, in-cap auto-pay bill, ADMIN-gated, found via the same
// scripts/uncalled-endpoint-sweep.py that found testMtnMomoConnectivity above: an
// admin previously had no way to run this except raw curl/Postman. Unlike that
// read-only diagnostic, this moves real money for every eligible user at once --
// BillsAutoPaySweepCard gates it behind a real confirm step, same discipline
// EscrowDisputesQueue's release/refund buttons already established.
export const processAutoPaymentsSweep = () =>
  apiFetch<{ success: boolean } & AutoPaySweepResult>('/api/v1/bills/process-auto-payments', {
    method: 'POST',
  });
