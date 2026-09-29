import { apiFetch } from './api';

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) -- see
// the backend's AffiliateService doc comment for the full sourced account. Any itunda
// user generates a real trackable link for any real Shop product and earns a real 3%
// commission (Coupang's own published regular-partner rate) on any resulting order,
// funded from itunda's own FEE_REVENUE house account. This file is the first client
// for an already-real backend that previously had zero UI anywhere.

export interface AffiliateLink {
  id: string;
  userId: string;
  productId: string;
  code: string;
  clickCount: number;
  createdAt: string;
}

export interface AffiliateCommission {
  id: string;
  linkId: string;
  referrerId: string;
  orderId: string;
  buyerId: string;
  commissionAmount: number;
  payoutTransactionId: string;
  createdAt: string;
}

export const createAffiliateLink = (productId: string) =>
  apiFetch<{ success: boolean; link: AffiliateLink }>('/api/v1/affiliate/links', {
    method: 'POST',
    body: JSON.stringify({ productId }),
  }).then((r) => r.link);

export const fetchMyAffiliateLinks = () =>
  apiFetch<{ success: boolean; links: AffiliateLink[] }>('/api/v1/affiliate/links/my-links').then((r) => r.links);

export const fetchMyAffiliateCommissions = () =>
  apiFetch<{ success: boolean; commissions: AffiliateCommission[] }>('/api/v1/affiliate/commissions/my-commissions').then((r) => r.commissions);

export const resolveAffiliateLink = (code: string) =>
  apiFetch<{ success: boolean; link: AffiliateLink }>(`/api/v1/affiliate/links/${code}/resolve`, { method: 'POST' }).then((r) => r.link);

// Real client-side referral capture -- a shared link is `?ref=CODE` appended to any
// real page URL (the same convention the earlier referral-signup flow already uses,
// see AuthService.referralCode). Captured once on load and persisted so it survives
// through browsing into checkout, matching how a real affiliate cookie/session
// persists across the click-to-purchase gap.
const REFERRAL_STORAGE_KEY = 'itunda_affiliate_ref';

export const captureReferralCodeFromUrl = () => {
  const params = new URLSearchParams(window.location.search);
  const code = params.get('ref');
  if (code) {
    try {
      window.localStorage.setItem(REFERRAL_STORAGE_KEY, code);
    } catch {
      // Non-critical -- storage may be unavailable (private browsing, etc.).
    }
    resolveAffiliateLink(code).catch(() => {});
  }
};

export const getStoredReferralCode = (): string | undefined => {
  try {
    return window.localStorage.getItem(REFERRAL_STORAGE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
};
