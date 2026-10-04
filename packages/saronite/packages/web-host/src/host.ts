import {
  SARONITE_PROTOCOL_VERSION,
  createCorrelationId,
  type SaroniteEvent,
  type SaroniteLifecycle,
  type SaronitePermissionState,
  type SaroniteRequest,
  type SaroniteResponse,
} from '@itunda/saronite-protocol';
import type { SaroniteWebTransport } from './transport';

export type SaroniteWebCapabilityHandler = {
  permission?: string;
  invoke(payload: unknown): unknown | Promise<unknown>;
};

export type SaroniteWebHostOptions = {
  transport: SaroniteWebTransport;
  lifecycle?: SaroniteLifecycle;
  permissions?: Record<string, boolean>;
  capabilities?: Record<string, SaroniteWebCapabilityHandler>;
};

export type SaroniteWebHost = {
  start(): void;
  stop(): Promise<void>;
  registerCapability(name: string, handler: SaroniteWebCapabilityHandler): void;
  unregisterCapability(name: string): void;
  setPermission(name: string, granted: boolean): Promise<void>;
  setLifecycle(lifecycle: SaroniteLifecycle): Promise<void>;
  dispatch<T = unknown>(request: SaroniteRequest): Promise<SaroniteResponse<T>>;
};

export function createSaroniteWebHost(options: SaroniteWebHostOptions): SaroniteWebHost {
  const capabilities = new Map(Object.entries(options.capabilities ?? {}));
  const permissions = new Map(Object.entries(options.permissions ?? {}));
  let started = false;
  let unsubscribe: (() => void) | undefined;

  const makeResponse = <T>(request: SaroniteRequest, value: Omit<SaroniteResponse<T>, 'protocolVersion' | 'kind' | 'id'>): SaroniteResponse<T> => ({
    protocolVersion: SARONITE_PROTOCOL_VERSION,
    kind: 'response',
    id: request.id,
    ...value,
  });

  const emit = async (event: Omit<SaroniteEvent, 'protocolVersion' | 'kind' | 'id'>) => {
    await options.transport.send({
      protocolVersion: SARONITE_PROTOCOL_VERSION,
      kind: 'event',
      id: createCorrelationId('evt'),
      ...event,
    });
  };

  const dispatch = async <T = unknown>(request: SaroniteRequest): Promise<SaroniteResponse<T>> => {
    if (request.protocolVersion !== SARONITE_PROTOCOL_VERSION) {
      return makeResponse(request, {
        ok: false,
        error: { code: 'UNSUPPORTED_VERSION', message: 'Unsupported Saronite protocol version.' },
      });
    }

    const handler = capabilities.get(request.capability);
    if (!handler) {
      return makeResponse(request, {
        ok: false,
        error: { code: 'UNKNOWN_CAPABILITY', message: 'Unknown capability: ' + request.capability },
      });
    }

    if (handler.permission && permissions.get(handler.permission) !== true) {
      return makeResponse(request, {
        ok: false,
        error: {
          code: 'PERMISSION_DENIED',
          message: 'Permission denied: ' + handler.permission,
          detail: { permission: handler.permission },
        },
      });
    }

    try {
      const result = await Promise.race([
        Promise.resolve(handler.invoke(request.payload)),
        request.timeoutMs && request.timeoutMs > 0
          ? new Promise<never>((_, reject) =>
              setTimeout(() => reject(new Error('Saronite request timed out.')), request.timeoutMs),
            )
          : new Promise<never>(() => {}),
      ]);
      return makeResponse(request, { ok: true, result: result as T });
    } catch (error) {
      const timedOut = error instanceof Error && error.message === 'Saronite request timed out.';
      return makeResponse(request, {
        ok: false,
        error: {
          code: timedOut ? 'TIMEOUT' : 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : String(error),
        },
      });
    }
  };

  return {
    start() {
      if (started) return;
      started = true;
      unsubscribe = options.transport.onMessage((message) => {
        if (message.kind === 'request') void dispatch(message).then(options.transport.send);
      });
      void emit({ event: 'lifecycle.changed', lifecycle: 'visible' });
    },
    async stop() {
      if (!started) return;
      started = false;
      unsubscribe?.();
      unsubscribe = undefined;
      await emit({ event: 'lifecycle.changed', lifecycle: 'terminated' });
      await options.transport.close();
    },
    registerCapability(name, handler) { capabilities.set(name, handler); },
    unregisterCapability(name) { capabilities.delete(name); },
    async setPermission(name, granted) {
      permissions.set(name, granted);
      const state: SaronitePermissionState = granted ? 'granted' : 'denied';
      await emit({ event: 'permission.changed', permission: { name, state } });
    },
    async setLifecycle(lifecycle) {
      await emit({ event: 'lifecycle.changed', lifecycle });
    },
    dispatch,
  };
}
