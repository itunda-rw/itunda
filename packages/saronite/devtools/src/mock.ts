export type MockStatus = 'ok' | 'denied' | 'unsupported' | 'error';

export type MockCall = {
  requestId: string;
  correlationId?: string;
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

export type MockRequestOptions = {
  permission?: string;
  supported?: boolean;
  /** Correlate a mocked SDK call with a user action or scenario. */
  correlationId?: string;
};

export type SaroniteMockHost = {
  state: MockHostState;
  request: <T>(
    capability: string,
    method: string,
    handler: () => T,
    options?: MockRequestOptions,
  ) => T | undefined;
  setPermission: (permission: string, granted: boolean) => void;
  setLifecycle: (lifecycle: MockHostState['lifecycle']) => void;
  clearCalls: () => void;
  subscribe: (listener: () => void) => () => void;
};

export function createSaroniteMockHost(
  initial: Partial<MockHostState> = {},
): SaroniteMockHost {
  const state: MockHostState = {
    calls: [...(initial.calls ?? [])],
    permissions: { ...(initial.permissions ?? {}) },
    lifecycle: initial.lifecycle ?? 'visible',
  };
  const listeners = new Set<() => void>();
  let requestSequence = state.calls.reduce((max, call) => {
    const match = /^mock-(\d+)$/.exec(call.requestId);
    return match ? Math.max(max, Number(match[1])) : max;
  }, 0);

  const notify = () => {
    for (const listener of listeners) listener();
  };

  const record = (
    capability: string,
    method: string,
    status: MockStatus,
    options: MockRequestOptions,
    detail?: string,
  ) => {
    requestSequence += 1;
    state.calls.push({
      requestId: `mock-${requestSequence}`,
      ...(options.correlationId ? { correlationId: options.correlationId } : {}),
      capability,
      method,
      status,
      timestamp: Date.now(),
      ...(detail ? { detail } : {}),
    });
    notify();
  };

  return {
    state,
    request(capability, method, handler, options = {}) {
      if (options.supported === false) {
        record(capability, method, 'unsupported', options, 'Capability is not supported by this mock host');
        return undefined;
      }
      if (options.permission && state.permissions[options.permission] !== true) {
        record(capability, method, 'denied', options, `Missing permission: ${options.permission}`);
        return undefined;
      }
      try {
        const result = handler();
        record(capability, method, 'ok', options);
        return result;
      } catch (error) {
        record(capability, method, 'error', options, error instanceof Error ? error.message : String(error));
        return undefined;
      }
    },
    setPermission(permission, granted) {
      state.permissions[permission] = granted;
      notify();
    },
    setLifecycle(lifecycle) {
      state.lifecycle = lifecycle;
      notify();
    },
    clearCalls() {
      state.calls.length = 0;
      notify();
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}
