// Real fetch against the canonical Kotlin backend (services/backend), mirroring
// bank-mfe/src/lib/api.ts's own convention -- see KycDashboard.tsx's own doc comment
// for why this file exists at all (2026-07-26: this whole module was a fully mocked,
// unwired shell with zero real API calls, the exact "fully mocked prototype" class of
// gap bank-mfe's own lib/api.ts header already names having fixed there in 2026-07-17).
//
// kyc-mfe never logs a user in itself -- it's a lazy-loaded remote mounted alongside
// bank_mfe inside the same host-app page (see host-app/src/App.tsx), so it reads the
// exact same shared localStorage token bank-mfe's own login already wrote, rather than
// duplicating a second login flow.

export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4001';

const TOKEN_KEY = 'itunda_bank_access_token';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

async function parseErrorBody(response: Response): Promise<{ code: string; message: string }> {
  try {
    const body = await response.json();
    return { code: body.code ?? 'UNKNOWN_ERROR', message: body.message ?? response.statusText };
  } catch {
    return { code: 'UNKNOWN_ERROR', message: response.statusText };
  }
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

  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }

  return response.json() as Promise<T>;
}
