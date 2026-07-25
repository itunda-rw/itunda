import { apiFetch } from './api';

// Real personal KYC identity submission (rw.itunda.identity) -- see KycDashboard.tsx's
// own doc comment. Mirrors bank-mfe/src/lib/identity.ts field-for-field (same backend,
// same shape) since kyc-mfe is a separately-deployed remote and can't import across
// module-federation boundaries. documentReference is a real, honest demo-mode stand-in
// for an uploaded ID scan/selfie (this backend has no file-storage layer) -- a
// free-text reference, not an actual image upload; see KycSubmission.kt's own doc
// comment.

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
