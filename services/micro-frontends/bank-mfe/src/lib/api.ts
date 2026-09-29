// Real fetch against the canonical Kotlin backend (services/backend) -- closes a real
// gap found live 2026-07-17: this app was previously a fully mocked prototype shell
// (fake setTimeout-based fetchBalance/fetchTransactions, no real API calls at all,
// confirmed by direct inspection), unlike every other micro-frontend in this repo.
// Same lib/api.ts convention as ops-mfe/merchant-mfe.

export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4001';

const TOKEN_KEY = 'itunda_bank_access_token';
const REFRESH_KEY = 'itunda_bank_refresh_token';
const USER_KEY = 'itunda_bank_user';
// Real Toss-sourced passwordless-login rollout (2026-08-24) -- a phone number isn't a
// secret (see AuthService.checkPhoneExists's own doc comment on this exact real
// trade-off), so remembering it locally to attempt tryPasswordlessLogin on the next
// app load is safe. Deliberately NOT cleared by logout() below -- "remember which
// number to try passwordless login with" is expected UX even after an explicit
// logout, the same way a real phone's own lock screen still shows whose device it is.
const REMEMBERED_PHONE_KEY = 'itunda_bank_remembered_phone';

export interface AuthedUser {
  id: string;
  phoneNumber: string;
  firstName: string;
  lastName: string;
  // Real Toss-sourced passwordless-login rollout (2026-08-24) -- false means this
  // account predates the real 6-digit-PIN scheme (see backend User.pinSet's own doc
  // comment). RegisterPage/LoginPage's own callers use this to show a real,
  // non-blocking "set your new PIN" upgrade prompt rather than silently ignoring it.
  pinSet: boolean;
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
  // Real push unregister-on-logout (item 232) -- see device.ts's own doc comment on
  // unregisterDeviceToken. Best-effort/fire-and-forget, same discipline as login's own
  // registerDeviceToken call. The token is captured *before* it's cleared below and
  // passed through explicitly -- by the time this dynamic import resolves and actually
  // fires the request, the synchronous removeItem calls below have already run.
  const tokenBeforeClear = getToken();
  import('./device').then(({ unregisterDeviceToken }) => unregisterDeviceToken(tokenBeforeClear)).catch(() => {});
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

export function getRememberedPhoneNumber(): string | null {
  return localStorage.getItem(REMEMBERED_PHONE_KEY);
}

function persistSession(body: { accessToken: string; refreshToken: string; user: AuthedUser }): AuthedUser {
  localStorage.setItem(TOKEN_KEY, body.accessToken);
  localStorage.setItem(REFRESH_KEY, body.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(body.user));
  localStorage.setItem(REMEMBERED_PHONE_KEY, body.user.phoneNumber);
  // Real push device-token registration (item 119) -- see device.ts's own doc comment
  // on registerDeviceToken. Best-effort and fire-and-forget: a registration failure
  // must never block a real, otherwise-successful login.
  import('./device').then(({ registerDeviceToken }) => registerDeviceToken()).catch(() => {});
  return body.user;
}

// `pin` is the real 6-digit credential (see backend AuthService's own doc comment on
// the sourced Toss "6자리 비밀번호" flow) -- still sent as the wire-format `password`
// field for backward compatibility with existing rows whose credential predates the
// PIN scheme (any shape, still verified the same way server-side), matching this
// session's own "server field name unchanged, client-facing name matches what it
// actually is" convention.
export async function login(phoneNumber: string, pin: string): Promise<AuthedUser> {
  // Real device binding (2026-07-20) -- see lib/device.ts's own doc comment. Imported
  // lazily inline (not at module top) to avoid a circular import, since device.ts's own
  // fetchMyDevices/verifyDevice/revokeDevice call back into apiFetch from this same file.
  const { getOrCreateDeviceId, getDeviceName } = await import('./device');
  const { getDevicePublicKeyBase64 } = await import('./deviceKey');
  const response = await fetch(`${BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      phoneNumber, password: pin, deviceId: getOrCreateDeviceId(), deviceName: getDeviceName(),
      devicePublicKey: await getDevicePublicKeyBase64(),
    }),
  });

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  return persistSession(await response.json());
}

// Real Toss-sourced passwordless LOGIN (2026-08-24) -- see backend DeviceService.
// issueLoginChallenge/verifyLoginSignature's own doc comments. The real "no PIN
// needed on a recognized device" outcome: a valid signature issues a fresh session
// exactly like login() does, no credential typed at all. Returns null (not a thrown
// error) on any failure -- this is always a background attempt on app load, falling
// through to the real PIN-pad login screen, never itself the thing that blocks a user.
export async function tryPasswordlessLogin(phoneNumber: string): Promise<AuthedUser | null> {
  const { hasDeviceKey, signChallenge } = await import('./deviceKey');
  const { getOrCreateDeviceId } = await import('./device');
  if (!(await hasDeviceKey())) return null;
  const deviceId = getOrCreateDeviceId();
  try {
    const challengeResponse = await fetch(`${BASE_URL}/api/v1/auth/login/device/challenge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber, deviceId }),
    });
    if (!challengeResponse.ok) return null;
    const { challenge } = await challengeResponse.json();
    const signature = await signChallenge(challenge);
    if (!signature) return null;
    const verifyResponse = await fetch(`${BASE_URL}/api/v1/auth/login/device/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber, deviceId, signature }),
    });
    if (!verifyResponse.ok) return null;
    return persistSession(await verifyResponse.json());
  } catch {
    return null;
  }
}

// Real Toss-sourced "set your 6-digit PIN" flow (2026-08-24) -- see backend
// AuthService.setPin's own doc comment. Used both for a pre-PIN-era user's real
// upgrade prompt (currentCredential = their existing, any-shape password) and for a
// real "forgot PIN" reset once phone re-verification re-establishes a fresh
// credential -- distinct flows, same underlying call.
export async function setPin(currentCredential: string, newPin: string): Promise<AuthedUser> {
  const body = await apiFetch<{ success: boolean; user: AuthedUser }>('/api/v1/auth/pin', {
    method: 'PUT',
    body: JSON.stringify({ currentCredential, newPin }),
  });
  localStorage.setItem(USER_KEY, JSON.stringify(body.user));
  return body.user;
}

export interface TermsDocument {
  id: string;
  title: string;
  version: string;
  required: boolean;
  summary: string;
}

// Real Toss/Korean-fintech-style 약관 동의 (terms consent) catalog (2026-08-18) -- see
// backend TermsCatalog's own doc comment for the full sourced account. Public, no
// token needed -- RegisterPage calls this before the user has any credential at all,
// the same "fetch what to render before the user can act" shape login/register
// themselves are the entry point for.
export async function getTerms(): Promise<TermsDocument[]> {
  const response = await fetch(`${BASE_URL}/api/v1/auth/terms`);
  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }
  const body = await response.json();
  return body.terms as TermsDocument[];
}

export interface LegalDocument {
  id: string;
  title: string;
  version: string;
  bodyMarkdown: string;
}

// Real itunda-branded legal document bodies (2026-08-30) -- see backend
// LegalDocumentCatalog's own doc comment for the full sourced account. Public, same
// reasoning as getTerms above -- reference material, not a consent gate.
export async function getLegalDocuments(): Promise<LegalDocument[]> {
  const response = await fetch(`${BASE_URL}/api/v1/auth/legal-documents`);
  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }
  const body = await response.json();
  return body.documents as LegalDocument[];
}

// Real sign-up (2026-08-04) -- closes docs/DESIGN_REFERENCES.md Section 8 recommendation
// #7: bank-mfe had no registration page at all, unlike Android/iOS's real 3-step
// phone -> name -> password flow (LoginScreen.kt/.swift). Mirrors login's own real device
// binding (a device that registers proves password ownership in the same request, so
// it's auto-trusted server-side, same reasoning LoginScreen.kt's own doc comment gives).
//
// `acceptedTermsIds` added 2026-08-18 -- see getTerms's own doc comment. RegisterPage
// gates its own submit button on every real required id being present in this list
// before calling register at all, so a missing required id here would mean that
// client-side gate already failed -- this is real defense in depth against a modified
// client, not the primary enforcement (that's AuthService.register's own real
// RequiredTermsNotAcceptedException check, which fires regardless of what any client does).
// `pin` is the real 6-digit credential -- see login's own doc comment on why it's
// still sent as the wire-format `password` field.
export async function register(
  phoneNumber: string,
  pin: string,
  firstName: string,
  lastName: string,
  acceptedTermsIds: string[],
  referralCode?: string,
): Promise<AuthedUser> {
  const { getOrCreateDeviceId, getDeviceName } = await import('./device');
  const { getDevicePublicKeyBase64 } = await import('./deviceKey');
  const response = await fetch(`${BASE_URL}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      phoneNumber,
      email: null,
      firstName,
      lastName,
      password: pin,
      referralCode: referralCode?.trim() || null,
      deviceId: getOrCreateDeviceId(),
      deviceName: getDeviceName(),
      acceptedTermsIds,
      devicePublicKey: await getDevicePublicKeyBase64(),
    }),
  });

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  return persistSession(await response.json());
}

// Real silent session-refresh (2026-08-15) -- matches Android's refreshAuthenticator
// and iOS's dataWithRefresh exactly (same session, same root cause: a dead-endpoint
// sweep found /auth/refresh had ZERO real callers on ANY platform, so a merely-expired
// 24h access token forced a full re-login on every platform instead of a silent
// refresh). Web's own apiFetch already had a real fallback the other two platforms
// didn't (a hard logout + SESSION_EXPIRED_EVENT, not a raw dead-end 401) -- this
// upgrades that fallback to try a silent refresh first, only falling through to the
// existing forced-logout path if the refresh token itself is invalid/expired.
// Single-flight via a shared in-flight Promise -- same race this codebase's Android
// twin guards against with an actual lock: two concurrent 401s must not both burn the
// single-use rotating refresh token.
let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (!refreshToken) return null;
    try {
      const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) return null;
      const body = await response.json();
      localStorage.setItem(TOKEN_KEY, body.accessToken);
      localStorage.setItem(REFRESH_KEY, body.refreshToken);
      return body.accessToken as string;
    } catch {
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}, isRetry = false): Promise<T> {
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
    // Only ever attempt a refresh-and-retry once per call, and never for the refresh
    // endpoint itself, to avoid a hard loop.
    if (!isRetry && token && path !== '/api/v1/auth/refresh') {
      const newToken = await refreshAccessToken();
      if (newToken) return apiFetch<T>(path, options, true);
    }
    // Refresh token itself is invalid/expired (7-day expiry, or no refresh token was
    // ever attempted) -- a real logout, not a dead end: the existing forced re-login
    // flow below is correct here, not a bug.
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
