// Real device binding (2026-07-20) -- see the backend's TrustedDevice.kt doc comment
// for the full account of what this closes and why (modeled on Toss's own real,
// published Gateway/Passport architecture). A real, stable, per-install identifier
// persisted in localStorage -- never a derived hardware fingerprint, this app has no
// interest in silently fingerprinting a user's browser, only in recognizing "the same
// browser that logged in before."

import { apiFetch } from './api';

const DEVICE_ID_KEY = 'itunda_bank_device_id';

export function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = crypto.randomUUID();
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
