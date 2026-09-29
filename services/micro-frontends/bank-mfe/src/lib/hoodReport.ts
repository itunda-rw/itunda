import { apiFetch } from './api';

// Real content-report submission (item 156) -- see the backend's HoodReport.kt /
// HoodReportService.kt doc comments. Distinct from the already-built ops-mfe admin
// review queue (GET /api/v1/system/hood-reports, a separate HoodReportAdminController)
// -- this is the customer-facing POST /api/v1/hood/reports a reporter actually files.
// Android (HoodShared.kt's HoodReportAction, shared across Marketplace/Community/Jobs/
// Property) and iOS (HoodScreen.swift) both already have this; bank-mfe had zero client
// despite the backend being real since well before this session.
export type HoodReportTargetType = 'MARKETPLACE_LISTING' | 'COMMUNITY_POST' | 'JOB_POST' | 'PROPERTY_LISTING';

export const submitHoodReport = (targetType: HoodReportTargetType, targetId: string, reason: string) =>
  apiFetch<{ success: boolean; report: { id: string } }>('/api/v1/hood/reports', {
    method: 'POST',
    body: JSON.stringify({ targetType, targetId, reason }),
  });
