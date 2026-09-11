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

// Real pagination-discard fix (2026-09-11, same systemic gap fixed
// throughout the sweep -- see project_itunda_pagination_discard_sweep
// memory) -- this real Pageable endpoint's page just wasn't ever sent,
// silently capping the mini-app catalog at the first 20 approved apps.
export const fetchMiniAppCatalog = (page = 0) =>
  apiFetch<{ success: boolean; miniApps: PartnerMiniApp[]; page: number; totalPages: number }>(
    `/api/v1/mini-apps/catalog?page=${page}&size=20`,
  );

// Real "verify with itunda" identity-verification-for-partners consent flow
// (Partners product-completeness pass, 2026-09-07) -- ported from Android's own
// real, already-live-verified IdentityVerificationConsentScreen (ItundaAppScreen.kt).
// Genuinely distinct from lib/verification.ts (email/phone verification) and the
// unrelated personal-KYC Identity tab -- this is itunda vouching for a user's real
// identity TO a third-party partner, reached via a `?verifyRequestId=` URL param
// (bank-mfe's own analog to the native apps' itunda://verify/{requestId} deep link).
export interface IdentityVerificationRequest {
  partnerName: string;
  status: 'PENDING' | 'APPROVED' | 'DECLINED' | 'EXPIRED';
  expiresAt: string;
  requestedFields: string[];
}

export const getIdentityVerificationRequest = (requestId: string) =>
  apiFetch<{ success: boolean } & IdentityVerificationRequest>(`/api/v1/identity/verification/${encodeURIComponent(requestId)}`);

export const approveIdentityVerification = (requestId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/identity/verification/${encodeURIComponent(requestId)}/approve`, { method: 'POST' });

export const declineIdentityVerification = (requestId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/identity/verification/${encodeURIComponent(requestId)}/decline`, { method: 'POST' });
