import { apiFetch } from './api';

// Real digital identity/signing certificate (Toss Certificate parity) -- see
// CertificateService.kt's own doc comment. First real UI touchpoint for this endpoint,
// added 2026-07-17 alongside making this app real.

export interface Certificate {
  id: string;
  userId: string;
  serialNumber: string;
  publicKeyBase64: string;
  algorithm: string;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  issuedAt: string;
  expiresAt: string;
  revokedAt: string | null;
}

export interface IssueCertificateResult {
  certificate: Certificate;
  privateKey: string;
}

export const issueCertificate = () =>
  apiFetch<{ success: boolean } & IssueCertificateResult>('/api/v1/certificate/issue', { method: 'POST' });

// Real-200s with `certificate: null` when there's no active certificate -- unlike
// lib/merchant.ts's getMyMerchant (which real-404s), CertificateService.getMyCertificate
// returns a nullable value directly, so there's no error case to translate here.
export const getMyCertificate = () =>
  apiFetch<{ success: boolean; certificate: Certificate | null }>('/api/v1/certificate/me').then((r) => r.certificate);

export const revokeCertificate = () =>
  apiFetch<{ success: boolean; certificate: Certificate }>('/api/v1/certificate/revoke', { method: 'POST' }).then((r) => r.certificate);
