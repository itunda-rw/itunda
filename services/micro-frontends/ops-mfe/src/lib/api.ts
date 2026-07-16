// Real fetch against the canonical Kotlin backend (services/backend), not mocked --
// bank-mfe/kyc-mfe never made a real authenticated call before this (see CORS history
// in SecurityConfig.kt). All five queues live under /api/v1/system/**, gated by the
// ADMIN role claim on the JWT.

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4001';

const TOKEN_KEY = 'itunda_ops_access_token';
const REFRESH_KEY = 'itunda_ops_refresh_token';
const USER_KEY = 'itunda_ops_user';

export interface OpsUser {
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

export function getStoredUser(): OpsUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as OpsUser;
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export const SESSION_EXPIRED_EVENT = 'itunda-ops-session-expired';

export function logout(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

async function parseErrorBody(response: Response): Promise<{ code: string; message: string }> {
  try {
    const body = await response.json();
    return { code: body.code ?? 'UNKNOWN_ERROR', message: body.message ?? response.statusText };
  } catch {
    return { code: 'UNKNOWN_ERROR', message: response.statusText };
  }
}

export async function login(phoneNumber: string, password: string): Promise<OpsUser> {
  const response = await fetch(`${BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber, password }),
  });

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  const body = await response.json();
  localStorage.setItem(TOKEN_KEY, body.accessToken);
  localStorage.setItem(REFRESH_KEY, body.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(body.user));
  return body.user as OpsUser;
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

  if (response.status === 401 || response.status === 403) {
    // ADMIN-gated route rejected the token (expired, revoked, or a non-admin account) --
    // drop the stale session and tell App.tsx to fall back to the login screen, rather
    // than leaving the caller sitting on a queue view that will never load again.
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
