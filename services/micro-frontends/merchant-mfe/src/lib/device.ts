// Real device binding (2026-07-28 port) -- same real mechanism bank-mfe already
// shipped 2026-07-20 (see the backend's TrustedDevice.kt doc comment for the full
// account, modeled on Toss's own real, published Gateway/Passport architecture). A
// real, stable, per-install identifier persisted in localStorage -- never a derived
// hardware fingerprint, just enough to recognize "the same browser that logged in
// before." merchant-mfe never calls account registration (a merchant registers their
// business after already being an itunda user, usually logged in first via bank-mfe or
// a mobile app), so this app's own deviceId will genuinely be untrusted the first time
// -- exactly the correct behavior for a device the backend has never seen before, not
// a bug.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

const DEVICE_ID_KEY = 'itunda_merchant_device_id';

export function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

// A real, honest, minimal browser/OS label parsed from the user agent -- same
// deliberately-not-a-fingerprint scope as bank-mfe's own getDeviceName().
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
