import { apiFetch } from './api';

// Real third-party mini-app catalog (rw.itunda.partners, 2026-07-26 backend) -- see
// PartnerService's own doc comment on the backend for the full honest scope boundary:
// this is a real registry + real human review workflow + a real published catalog of
// approved mini-apps, the same real shape Toss's own "미니앱" platform has. What it
// deliberately does NOT do yet: actually download/sandbox/run a third-party bundle --
// this is a browsable catalog only, first bank-mfe client for a backend that previously
// had zero UI anywhere.

// Real Toss/Kakao mini-app-store reference (2026-09-11, 7 real Kakao 미니앱
// screenshots) -- a small, deliberately generic taxonomy (no real submitted
// partner apps yet to justify more granularity), matching the backend's own
// PartnerMiniAppCategory enum exactly (each label upper-cases to its real
// enum name, so SearchAndCategoryChips -- which uses the same string as both
// the displayed label and the selected value -- can be reused as-is).
export const MINI_APP_CATEGORIES = ['Finance', 'Shopping', 'Productivity', 'Lifestyle', 'Other'];

export interface PartnerMiniApp {
  id: string;
  partnerId: string;
  name: string;
  description: string;
  iconUrl: string | null;
  permissions: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  category: string;
  createdAt: string;
}

// Real Mini-Apps hub pass (2026-09-11) -- was page-based (`?page=`), replaced
// with a single size=100 "browse all (optionally filtered)" fetch, matching
// fetchShoppingCatalog's own precedent: the catalog is genuinely tiny today
// (no seed data, no real onboarded partners), so real pagination is building
// ahead of real need. Search over the result is client-side (MiniAppsHubScreen).
export const fetchMiniAppCatalog = (category?: string | null) => {
  const params = new URLSearchParams();
  if (category) params.set('category', category.toUpperCase());
  params.set('size', '100');
  return apiFetch<{ success: boolean; miniApps: PartnerMiniApp[]; page: number; totalPages: number }>(
    `/api/v1/mini-apps/catalog?${params.toString()}`,
  ).then((r) => r.miniApps);
};

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
