export type SaronitePermission = 'location' | 'camera' | 'contacts' | 'notifications' | 'clipboard';
export type SaronitePlatform = 'android' | 'ios' | 'web';
export type SaroniteNetwork = 'online' | 'offline';

export interface SaroniteLog {
  id: string;
  timestamp: number;
  direction: 'request' | 'response' | 'event';
  capability: string;
  method: string;
  payload?: unknown;
  durationMs?: number;
  error?: string;
}

export interface SaroniteMockState {
  platform: SaronitePlatform;
  network: SaroniteNetwork;
  locale: string;
  authenticated: boolean;
  permissions: Record<SaronitePermission, 'granted' | 'denied' | 'prompt'>;
  storage: Record<string, string>;
  logs: SaroniteLog[];
  latencyMs: number;
}

export interface SaroniteDevTools {
  readonly state: Readonly<SaroniteMockState>;
  update(patch: Partial<Omit<SaroniteMockState, 'permissions' | 'storage' | 'logs'>>): void;
  setPermission(permission: SaronitePermission, value: SaroniteMockState['permissions'][SaronitePermission]): void;
  setStorage(key: string, value: string): void;
  clearStorage(): void;
  setLatency(ms: number): void;
  record(log: Omit<SaroniteLog, 'id' | 'timestamp'>): void;
  clearLogs(): void;
  reset(): void;
}

const defaultState = (): SaroniteMockState => ({
  platform: 'web',
  network: 'online',
  locale: 'rw-RW',
  authenticated: true,
  permissions: {
    location: 'prompt',
    camera: 'prompt',
    contacts: 'prompt',
    notifications: 'prompt',
    clipboard: 'granted',
  },
  storage: {},
  logs: [],
  latencyMs: 0,
});

const clone = <T>(value: T): T => structuredClone(value);

export function createSaroniteDevTools(initial?: Partial<SaroniteMockState>): SaroniteDevTools {
  let state = { ...defaultState(), ...initial };
  state.permissions = { ...defaultState().permissions, ...(initial?.permissions ?? {}) };
  state.storage = { ...(initial?.storage ?? {}) };
  state.logs = [...(initial?.logs ?? [])];

  const api: SaroniteDevTools = {
    get state() { return clone(state); },
    update(patch) { state = { ...state, ...patch }; },
    setPermission(permission, value) { state.permissions = { ...state.permissions, [permission]: value }; },
    setStorage(key, value) { state.storage = { ...state.storage, [key]: value }; },
    clearStorage() { state.storage = {}; },
    setLatency(ms) {
      if (!Number.isInteger(ms) || ms < 0 || ms > 10000) throw new RangeError('latency must be 0..10000ms');
      state.latencyMs = ms;
    },
    record(log) {
      state.logs = [...state.logs, { ...log, id: `${Date.now()}-${state.logs.length + 1}`, timestamp: Date.now() }].slice(-500);
    },
    clearLogs() { state.logs = []; },
    reset() { state = defaultState(); },
  };
  return api;
}

export function installSaroniteBrowserMock(
  target: Window & typeof globalThis = window,
  initial?: Partial<SaroniteMockState>,
): SaroniteDevTools {
  // The caller must only invoke this module from a development build.
  // Keeping the guard at the integration boundary avoids coupling the package
  // to Vite/webpack-specific environment globals.
  const devtools = createSaroniteDevTools(initial);
  Object.defineProperty(target, '__saronite', {
    configurable: true,
    enumerable: false,
    value: devtools,
    writable: false,
  });
  return devtools;
}
