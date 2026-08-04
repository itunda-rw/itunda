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

// Real public certificate status/verify (2026-08-04) -- found via a fresh "defined
// but uncalled" endpoint sweep: real, working, deliberately unauthenticated endpoints
// (see CertificateController's own doc comment on why /status and /verify are
// permitAll, unlike /issue/-me/-revoke above) with zero client anywhere, including
// this app which already wires the other three. Ports the same fix already shipped
// on Android.
export interface VerifyCertificateSignatureResult {
  signatureValid: boolean;
  certificateStatus: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  userId: string;
  serialNumber: string;
}

export const getCertificateStatus = (serialNumber: string) =>
  apiFetch<{ success: boolean; certificate: Certificate }>(`/api/v1/certificate/status/${encodeURIComponent(serialNumber)}`).then((r) => r.certificate);

export const verifyCertificateSignature = (serialNumber: string, payload: string, signature: string) =>
  apiFetch<{ success: boolean } & VerifyCertificateSignatureResult>('/api/v1/certificate/verify', {
    method: 'POST',
    body: JSON.stringify({ serialNumber, payload, signature }),
  });
