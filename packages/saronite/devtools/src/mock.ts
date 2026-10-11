import { SARONITE_CAPABILITIES, permissionFor, type SaroniteCapabilityName } from './capabilities';

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

const SUPPORTED_METHODS: Record<SaroniteCapabilityName, readonly string[]> = {
  identity: ['getCurrentIdentity'],
  location: ['getCurrentLocation'],
  navigation: ['open', 'close'],
};

function isCapabilityName(value: string): value is SaroniteCapabilityName {
  return Object.prototype.hasOwnProperty.call(SARONITE_CAPABILITIES, value);
}

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
      if (!isCapabilityName(capability)) {
        throw new Error(`Unknown Saronite capability: ${capability}`);
      }
      if (!SUPPORTED_METHODS[capability].includes(method)) {
        throw new Error(`Unknown Saronite method: ${capability}.${method}`);
      }
      if (options.supported === false) {
        record(capability, method, 'unsupported');
        return undefined;
      }

      // The mock must enforce the same manifest permission even when callers omit
      // options.permission; an explicit option can only select a stricter scope.
      const capabilityPermission = permissionFor(capability);
      const requiredPermission = options.permission ?? capabilityPermission;
      if (
        capabilityPermission &&
        requiredPermission !== capabilityPermission
      ) {
        record(capability, method, 'denied', capabilityPermission);
        return undefined;
      }
      if (requiredPermission && state.permissions[requiredPermission] !== true) {
        record(capability, method, 'denied', requiredPermission);
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
