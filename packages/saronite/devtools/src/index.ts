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


export interface SaroniteCapabilityCall {
  capability: string;
  method: string;
  payload?: unknown;
}

export interface SaroniteCapabilityResult {
  result?: unknown;
  error?: string;
}

export type SaroniteCapabilityHandler = (
  call: SaroniteCapabilityCall,
) => SaroniteCapabilityResult | Promise<SaroniteCapabilityResult>;

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
  subscribe(listener: (state: Readonly<SaroniteMockState>) => void): () => void;
  getCapabilities(): string[];
  registerCapability(capability: string, handler: SaroniteCapabilityHandler): void;
  callCapability(call: SaroniteCapabilityCall): Promise<SaroniteCapabilityResult>;
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

  const capabilities = new Map<string, SaroniteCapabilityHandler>();
  const listeners = new Set<(state: Readonly<SaroniteMockState>) => void>();
  const notify = () => listeners.forEach((listener) => listener(clone(state)));

  const api: SaroniteDevTools = {
    getCapabilities() { return [...capabilities.keys()]; },
    registerCapability(capability, handler) { capabilities.set(capability, handler); },
    async callCapability(call) {
      const started = Date.now();
      api.record({ direction: 'request', capability: call.capability, method: call.method, payload: call.payload });
      if (state.network === 'offline') {
        const error = 'NETWORK_OFFLINE';
        api.record({ direction: 'response', capability: call.capability, method: call.method, durationMs: Date.now() - started, error });
        return { error };
      }
      if (state.latencyMs) await new Promise((resolve) => setTimeout(resolve, state.latencyMs));
      const handler = capabilities.get(call.capability);
      if (!handler) {
        const error = 'CAPABILITY_NOT_MOCKED';
        api.record({ direction: 'response', capability: call.capability, method: call.method, durationMs: Date.now() - started, error });
        return { error };
      }
      try {
        const result = await handler(call);
        api.record({ direction: 'response', capability: call.capability, method: call.method, payload: result.result, durationMs: Date.now() - started, error: result.error });
        return result;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        api.record({ direction: 'response', capability: call.capability, method: call.method, durationMs: Date.now() - started, error: message });
        return { error: message };
      }
    },
    get state() { return clone(state); },
    update(patch) { state = { ...state, ...patch }; notify(); },
    setPermission(permission, value) { state.permissions = { ...state.permissions, [permission]: value }; notify(); },
    setStorage(key, value) { state.storage = { ...state.storage, [key]: value }; notify(); },
    clearStorage() { state.storage = {}; notify(); },
    setLatency(ms) {
      if (!Number.isInteger(ms) || ms < 0 || ms > 10000) throw new RangeError('latency must be 0..10000ms');
      state.latencyMs = ms; notify();
    },
    record(log) {
      state.logs = [...state.logs, { ...log, id: `${Date.now()}-${state.logs.length + 1}`, timestamp: Date.now() }].slice(-500); notify();
    },
    clearLogs() { state.logs = []; notify(); },
    reset() { state = defaultState(); notify(); },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
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
