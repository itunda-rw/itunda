import { SARONITE_CAPABILITIES, SARONITE_METHODS, permissionFor, type SaroniteCapabilityName } from './capabilities';

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
      if (!SARONITE_METHODS[capability].includes(method)) {
        throw new Error(`Unknown Saronite method: ${capability}.${method}`);
      }
      if (options.supported === false) {
        record(capability, method, 'unsupported');
        return undefined;
      }

      const capabilityPermission = permissionFor(capability);
      const requestedPermission = options.permission;
      // A caller cannot downgrade or replace the permission defined by the SDK.
      if (capabilityPermission && requestedPermission && requestedPermission !== capabilityPermission) {
        record(capability, method, 'denied', capabilityPermission);
        return undefined;
      }
      const requiredPermission = capabilityPermission ?? requestedPermission;
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
