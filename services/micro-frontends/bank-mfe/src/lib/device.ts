// Real device binding (2026-07-20) -- see the backend's TrustedDevice.kt doc comment
// for the full account of what this closes and why (modeled on Toss's own real,
// published Gateway/Passport architecture). A real, stable, per-install identifier
// persisted in localStorage -- never a derived hardware fingerprint, this app has no
// interest in silently fingerprinting a user's browser, only in recognizing "the same
// browser that logged in before."

import { apiFetch } from './api';
import { randomUUID } from './uuid';

const DEVICE_ID_KEY = 'itunda_bank_device_id';

export function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

// A real, honest, minimal browser/OS label parsed from the user agent -- not a fake
// hardcoded value, but also deliberately not a precise fingerprint (just enough for a
// user to recognize "oh, that's my laptop" on their own Devices screen).
export function getDeviceName(): string {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  const os = /Mac OS X/.test(ua) ? 'Mac' : /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Linux/.test(ua) ? 'Linux' : 'device';
  return `${browser} on ${os}`;
}

export interface TrustedDevice {
  id: string;
  userId: string;
  deviceId: string;
  deviceName: string | null;
  trusted: boolean;
  firstSeenAt: string;
  lastSeenAt: string;
  verifiedAt: string | null;
}

export const fetchMyDevices = () =>
  apiFetch<{ success: boolean; devices: TrustedDevice[] }>('/api/v1/auth/devices').then((r) => r.devices);

export const verifyDevice = (password: string) =>
  apiFetch<{ success: boolean; device: TrustedDevice }>('/api/v1/auth/devices/verify', {
    method: 'POST',
    body: JSON.stringify({ password }),
  }).then((r) => r.device);

export const revokeDevice = (deviceId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/auth/devices/${encodeURIComponent(deviceId)}`, { method: 'DELETE' });

// Real push device-token registration (item 119) -- see backend DeviceToken.kt's own
// doc comment: a mature, real push pipeline (PushNotificationService.sendToUser, wired
// into rideshare/keyword-alert/merchant-booking/fraud-alert/P2P-received/merchant-follow
// pushes this session) silently no-ops for every real user because `sendToUser` reads
// `deviceTokenRepository.findByUserId(userId)` and bails if empty -- a real repo-wide
// sweep found zero client anywhere ever calls `POST /api/v1/notifications/device-tokens`
// to put a row there in the first place. This app has no real FCM/APNs/Web Push SDK
// integrated, so there's no real push token to send -- reuses the same real, stable,
// per-install device id already established for trusted-device binding (see this file's
// own header) as this demo's client-generated token, exactly the same honest
// simplification `PushSender`'s own doc comment already applies on the sending side.
export const registerDeviceToken = () =>
  apiFetch<{ success: boolean }>('/api/v1/notifications/device-tokens', {
    method: 'POST',
    body: JSON.stringify({ platform: 'WEB', token: getOrCreateDeviceId() }),
  });

// Real push unregister-on-logout (item 232, found via a defined-but-uncalled-endpoint
// sweep): DeviceTokenController.unregister is real (and even had a real @Transactional
// bug fixed live during this feature's own original verification, per its own doc
// comment) but had zero client callers anywhere -- a device stayed registered forever
// after logout, so whoever logs in next on the same browser would keep receiving push
// meant for the previous account until they happened to re-register. Fire-and-forget,
// same discipline as registerDeviceToken above: a failed unregister shouldn't block
// logout itself.
// accessToken is captured and passed explicitly by logout() (api.ts) rather than read
// automatically by apiFetch, since by the time this fire-and-forget dynamic import
// resolves, logout() has already synchronously cleared the stored token.
export const unregisterDeviceToken = (accessToken: string | null) =>
  apiFetch<{ success: boolean }>(`/api/v1/notifications/device-tokens/${encodeURIComponent(getOrCreateDeviceId())}`, {
    method: 'DELETE',
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  }).catch(() => {});
