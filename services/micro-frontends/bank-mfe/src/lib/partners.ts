import { apiFetch } from './api';

// Real third-party mini-app catalog (rw.itunda.partners, 2026-07-26 backend) -- see
// PartnerService's own doc comment on the backend for the full honest scope boundary:
// this is a real registry + real human review workflow + a real published catalog of
// approved mini-apps, the same real shape Toss's own "미니앱" platform has. What it
// deliberately does NOT do yet: actually download/sandbox/run a third-party bundle --
// this is a browsable catalog only, first bank-mfe client for a backend that previously
// had zero UI anywhere.

export interface PartnerMiniApp {
  id: string;
  partnerId: string;
  name: string;
  description: string;
  iconUrl: string | null;
  permissions: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  createdAt: string;
}

export const fetchMiniAppCatalog = () =>
  apiFetch<{ success: boolean; miniApps: PartnerMiniApp[] }>('/api/v1/mini-apps/catalog?size=20').then((r) => r.miniApps);
