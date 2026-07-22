import { apiFetch } from './api';

// Real personal KYC identity submission (rw.itunda.identity) -- found 2026-07-22 fully
// built on the backend with zero client UI anywhere. merchant-mfe already has KYB
// (BUSINESS_TIN) submission and ops-mfe the review queue, but this ordinary personal
// NATIONAL_ID/PASSPORT submission had zero UI on any client -- kyc-mfe, checked
// directly, is an unwired mock shell with no real API calls at all. documentReference
// is a real, honest demo-mode stand-in for an uploaded ID scan/selfie (this backend
// has no file-storage layer) -- a free-text reference, not an actual image upload; see
// KycSubmission.kt's own doc comment.

export type IdentityDocumentType = 'NATIONAL_ID' | 'PASSPORT';

export interface KycSubmission {
  id: string;
  userId: string;
  documentType: string;
  documentNumber: string;
  documentReference: string;
  status: string;
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  decisionReason: string | null;
  autoVerificationStatus: string | null;
  autoVerificationDetail: string | null;
}

export const submitIdentity = (documentType: IdentityDocumentType, documentNumber: string, documentReference: string) =>
  apiFetch<{ success: boolean; submission: KycSubmission }>('/api/v1/identity/submit', {
    method: 'POST',
    body: JSON.stringify({ documentType, documentNumber, documentReference }),
  }).then((r) => r.submission);

export const fetchIdentityStatus = () =>
  apiFetch<{ success: boolean; submissions: KycSubmission[] }>('/api/v1/identity/status').then((r) => r.submissions);
