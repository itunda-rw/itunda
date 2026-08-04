// Real fetch against the canonical Kotlin backend (services/backend) -- closes a real
// gap found live 2026-07-17: this app was previously a fully mocked prototype shell
// (fake setTimeout-based fetchBalance/fetchTransactions, no real API calls at all,
// confirmed by direct inspection), unlike every other micro-frontend in this repo.
// Same lib/api.ts convention as ops-mfe/merchant-mfe.

export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4001';

const TOKEN_KEY = 'itunda_bank_access_token';
const REFRESH_KEY = 'itunda_bank_refresh_token';
const USER_KEY = 'itunda_bank_user';

export interface AuthedUser {
  id: string;
  phoneNumber: string;
  firstName: string;
  lastName: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getStoredUser(): AuthedUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthedUser;
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export const SESSION_EXPIRED_EVENT = 'itunda-bank-session-expired';

export function logout(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

export async function parseErrorBody(response: Response): Promise<{ code: string; message: string }> {
  try {
    const body = await response.json();
    return { code: body.code ?? 'UNKNOWN_ERROR', message: body.message ?? response.statusText };
  } catch {
    return { code: 'UNKNOWN_ERROR', message: response.statusText };
  }
}

export async function login(phoneNumber: string, password: string): Promise<AuthedUser> {
  // Real device binding (2026-07-20) -- see lib/device.ts's own doc comment. Imported
  // lazily inline (not at module top) to avoid a circular import, since device.ts's own
  // fetchMyDevices/verifyDevice/revokeDevice call back into apiFetch from this same file.
  const { getOrCreateDeviceId, getDeviceName } = await import('./device');
  const response = await fetch(`${BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber, password, deviceId: getOrCreateDeviceId(), deviceName: getDeviceName() }),
  });

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  const body = await response.json();
  localStorage.setItem(TOKEN_KEY, body.accessToken);
  localStorage.setItem(REFRESH_KEY, body.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(body.user));
  // Real push device-token registration (item 119) -- see device.ts's own doc comment
  // on registerDeviceToken. Best-effort and fire-and-forget: a registration failure
  // must never block a real, otherwise-successful login.
  import('./device').then(({ registerDeviceToken }) => registerDeviceToken()).catch(() => {});
  return body.user as AuthedUser;
}

// Real sign-up (2026-08-04) -- closes docs/DESIGN_REFERENCES.md Section 8 recommendation
// #7: bank-mfe had no registration page at all, unlike Android/iOS's real 3-step
// phone -> name -> password flow (LoginScreen.kt/.swift). Mirrors login's own real device
// binding (a device that registers proves password ownership in the same request, so
// it's auto-trusted server-side, same reasoning LoginScreen.kt's own doc comment gives).
export async function register(
  phoneNumber: string,
  password: string,
  firstName: string,
  lastName: string,
  referralCode?: string,
): Promise<AuthedUser> {
  const { getOrCreateDeviceId, getDeviceName } = await import('./device');
  const response = await fetch(`${BASE_URL}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      phoneNumber,
      email: null,
      firstName,
      lastName,
      password,
      referralCode: referralCode?.trim() || null,
      deviceId: getOrCreateDeviceId(),
      deviceName: getDeviceName(),
    }),
  });

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  const body = await response.json();
  localStorage.setItem(TOKEN_KEY, body.accessToken);
  localStorage.setItem(REFRESH_KEY, body.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(body.user));
  import('./device').then(({ registerDeviceToken }) => registerDeviceToken()).catch(() => {});
  return body.user as AuthedUser;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (response.status === 401) {
    logout();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  return response.json() as Promise<T>;
}
