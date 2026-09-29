import { apiFetch } from './api';


// Real merchant moderation queue -- `MerchantModerationAdminController` had zero client
// anywhere despite being real and working (found via an uncalled-endpoint sweep): every
// other real admin queue under /api/v1/system (fraud, compliance, escrow, support,
// partners, property-verification, agent-reconciliation, incidents) has one, this was
// the one sibling left out. "ACTIVE with no category" is the real signal a merchant
// needs admin attention (see backend MerchantRepository.findByStatusAndCategoryIsNull's
// own doc comment) -- suspend/reactivate are the only two actions this queue offers,
// matching the backend's own real, minimal scope.
export interface UncategorizedMerchant {
  merchantId: string;
  businessName: string;
  kybVerified: boolean;
  createdAt: string;
}

export const fetchUncategorizedMerchants = (page = 0) =>
  apiFetch<{ success: boolean; merchants: UncategorizedMerchant[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/merchants/uncategorized?page=${page}`,
  ).then((response) => ({ items: response.merchants, totalElements: response.totalElements, hasMore: response.page + 1 < response.totalPages }));

export const suspendMerchant = (merchantId: string) =>
  apiFetch<{ success: boolean; status: string }>(`/api/v1/system/merchants/${merchantId}/suspend`, { method: 'POST' });

export const reactivateMerchant = (merchantId: string) =>
  apiFetch<{ success: boolean; status: string }>(`/api/v1/system/merchants/${merchantId}/reactivate`, { method: 'POST' });

// Real fee-waiver revocation review (Merchant product-completeness pass) -- see
// backend MerchantFeeWaiverService.getRevocationCandidates's own doc comment: a
// granted waiver stayed in effect forever with no admin surface to catch a merchant
// who outgrew the small-merchant threshold. Naturally small-cardinality (only
// merchants with an active waiver, further filtered to ones who've outgrown it) --
// a plain list, not paginated, matching the backend's own non-paged response.
export interface FeeWaiverCandidate {
  merchantId: string;
  businessName: string;
  recentVolume: number;
}

export const fetchFeeWaiverCandidates = () =>
  apiFetch<{ success: boolean; candidates: FeeWaiverCandidate[] }>('/api/v1/system/merchants/fee-waiver-candidates').then((r) => r.candidates);

export const revokeFeeWaiver = (merchantId: string) =>
  apiFetch<{ success: boolean; merchantId: string }>(`/api/v1/system/merchants/${merchantId}/revoke-fee-waiver`, { method: 'POST' });

// Real ops visibility (Merchant product-completeness pass) -- see backend
// WebhookDeliveryService.getExhaustedQueue's own doc comment: EXHAUSTED deliveries
// had zero admin surface across merchants, useless for spotting a systemic
// delivery problem (e.g. a shared downstream outage) shared across several.
export interface ExhaustedWebhookDelivery {
  id: string;
  merchantId: string;
  eventType: string | null;
  attemptCount: number;
  createdAt: string;
  lastError: string | null;
}

export const fetchExhaustedWebhookDeliveries = (page = 0) =>
  apiFetch<{ success: boolean; deliveries: ExhaustedWebhookDelivery[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/merchant-webhooks/exhausted?page=${page}`,
  ).then((response) => ({ items: response.deliveries, totalElements: response.totalElements, hasMore: response.page + 1 < response.totalPages }));

export const replayExhaustedWebhookDelivery = (deliveryId: string) =>
  apiFetch<{ success: boolean; replay: Record<string, unknown> }>(`/api/v1/system/merchant-webhooks/${deliveryId}/replay`, { method: 'POST' });
