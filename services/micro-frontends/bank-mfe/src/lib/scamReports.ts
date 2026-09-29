import { apiFetch } from './api';

// Real Toss 사기계좌 조회 (fraud-account lookup before transfer) -- see backend
// ScamReportService's own doc comment for the full sourced account and honest scope
// boundary: itunda's own crowd-sourced report registry, not a real police-database
// integration.

export interface ScamReport {
  id: string;
  reporterId: string;
  reportedIdentifier: string;
  reason: string;
  createdAt: string;
}

export interface ScamCheckResult {
  identifier: string;
  reportCount: number;
  warn: boolean;
}

export const checkScamStatus = (identifier: string) =>
  apiFetch<{ success: boolean; result: ScamCheckResult }>(`/api/v1/p2p/scam-reports/check?identifier=${encodeURIComponent(identifier)}`).then((r) => r.result);

export const reportScam = (identifier: string, reason: string) =>
  apiFetch<{ success: boolean; report: ScamReport }>('/api/v1/p2p/scam-reports', {
    method: 'POST',
    body: JSON.stringify({ identifier, reason }),
  }).then((r) => r.report);

export const fetchMyScamReports = () =>
  apiFetch<{ success: boolean; reports: ScamReport[] }>('/api/v1/p2p/scam-reports/mine').then((r) => r.reports);
