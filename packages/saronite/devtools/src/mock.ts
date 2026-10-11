export type MockStatus = 'ok' | 'denied' | 'unsupported' | 'error';

export type MockCall = {
  capability: string;
  method: string;
  status: MockStatus;
  timestamp: number;
  detail?: string;
};

export type MockHostState = {
  calls: MockCall[];
  permissions: Record<string, boolean>;
  lifecycle: 'visible' | 'hidden';
};

export type SaroniteMockHost = {
  state: MockHostState;
  request: <T>(
    capability: string,
    method: string,
    handler: () => T,
    options?: { permission?: string; supported?: boolean },
  ) => T | undefined;
  setPermission: (permission: string, granted: boolean) => void;
  setLifecycle: (lifecycle: MockHostState['lifecycle']) => void;
  clearCalls: () => void;
};

export function createSaroniteMockHost(
  initial: Partial<MockHostState> = {},
): SaroniteMockHost {
  const state: MockHostState = {
    ...initial,
    calls: [...(initial.calls ?? [])],
    permissions: { ...(initial.permissions ?? {}) },
    lifecycle: initial.lifecycle ?? 'visible',
  };

  const record = (
    capability: string,
    method: string,
    status: MockStatus,
    detail?: string,
  ) => {
    state.calls.push({ capability, method, status, timestamp: Date.now(), detail });
  };

  return {
    state,
    request(capability, method, handler, options = {}) {
      if (options.supported === false) {
        record(capability, method, 'unsupported');
        return undefined;
      }
      if (options.permission && state.permissions[options.permission] !== true) {
        record(capability, method, 'denied', options.permission);
        return undefined;
      }
      try {
        const result = handler();
        record(capability, method, 'ok');
        return result;
      } catch (error) {
        record(capability, method, 'error', error instanceof Error ? error.message : String(error));
        return undefined;
      }
    },
    setPermission(permission, granted) {
      state.permissions[permission] = granted;
    },
    setLifecycle(lifecycle) {
      state.lifecycle = lifecycle;
    },
    clearCalls() {
      state.calls.length = 0;
    },
  };
}
