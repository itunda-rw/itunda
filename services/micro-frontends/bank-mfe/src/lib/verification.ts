import { apiFetch } from './api';

// Real email/phone verification (item 169, found via the entity-cross-reference
// discovery sweep) -- see AuthService.requestEmailVerification/requestPhoneVerification's
// own doc comments. Both deliver a real code via a real in-app Notification + real push
// (never echoed back in this API response -- itunda has no real SMS/email gateway to
// send an actual text/email through, so the code is delivered the same honest way every
// other in-app-only notification in this codebase is). Phone verification already had
// zero client anywhere on any platform; email verification had one only via the Saronite
// reward-tasks mini-app -- bank-mfe (the actual banking app) had neither.

export const requestEmailVerification = () =>
  apiFetch<{ success: boolean }>('/api/v1/auth/profile/verify-email', { method: 'POST' });

export const confirmEmailVerification = (token: string) =>
  apiFetch<{ success: boolean; user: { emailVerified: boolean } }>('/api/v1/auth/profile/verify-email/confirm', {
    method: 'POST',
    body: JSON.stringify({ token }),
  }).then((r) => r.user);

export const requestPhoneVerification = () =>
  apiFetch<{ success: boolean }>('/api/v1/auth/profile/verify-phone', { method: 'POST' });

export const confirmPhoneVerification = (code: string) =>
  apiFetch<{ success: boolean; user: { phoneVerified: boolean } }>('/api/v1/auth/profile/verify-phone/confirm', {
    method: 'POST',
    body: JSON.stringify({ code }),
  }).then((r) => r.user);
