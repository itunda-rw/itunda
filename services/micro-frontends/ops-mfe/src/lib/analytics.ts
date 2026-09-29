import { apiFetch } from './api';

// Real, orphaned ADMIN endpoint (AnalyticsController.getSummary) -- computes real
// per-event usage counts and a real coop-rail return-rate from the analytics_events
// table, but had zero caller anywhere (confirmed via scripts/uncalled-endpoint-sweep.py)
// until this fresh product-completeness pass found it. Same "real backend capability,
// zero client" shape system.ts's own endpoints were in before OverviewView.tsx.
export interface AnalyticsSummary {
  windowDays: number;
  totalActiveUsers: number;
  events: Record<string, { totalEvents: number; distinctUsers: number }>;
  coopRailReturnRate: {
    usersWhoTapped: number;
    returnedNextDayOrLater: number;
    rate: number | null;
  };
}

export const fetchAnalyticsSummary = (days = 30) =>
  apiFetch<{ success: boolean } & AnalyticsSummary>(`/api/v1/analytics/summary?days=${days}`);
