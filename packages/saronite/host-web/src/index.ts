import {
  createCorrelationId,
  isSaroniteMessage,
  SARONITE_PROTOCOL_VERSION,
  type SaroniteEvent,
  type SaroniteRequest,
  type SaroniteResponse,
} from '@itunda/saronite-protocol';

export type SaroniteWebTarget = Window;

export type SaroniteWebTransportOptions = {
  target: SaroniteWebTarget;
  targetOrigin?: string;
  sourceWindow?: Window;
  timeoutMs?: number;
};

export type SaroniteWebTransport = {
  request<TPayload = unknown, TResult = unknown>(
    capability: string,
    method: string,
    payload?: TPayload,
    timeoutMs?: number,
  ): Promise<TResult>;
  subscribe(listener: (event: SaroniteEvent) => void): () => void;
  close(): void;
};

type Pending = {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

export class SaroniteWebError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'SaroniteWebError';
    this.code = code;
  }
}

export function createSaroniteWebTransport(
  options: SaroniteWebTransportOptions,
): SaroniteWebTransport {
  const sourceWindow = options.sourceWindow ?? window;
  const targetOrigin = options.targetOrigin ?? '*';
  const defaultTimeoutMs = options.timeoutMs ?? 10_000;
  const pending = new Map<string, Pending>();
  const listeners = new Set<(event: SaroniteEvent) => void>();

  const onMessage = (event: MessageEvent<unknown>) => {
    if (event.source !== options.target && event.source !== sourceWindow) return;
    if (targetOrigin !== '*' && event.origin !== targetOrigin) return;
    if (!isSaroniteMessage(event.data)) return;

    if (event.data.kind === 'response') {
      const response = event.data as SaroniteResponse;
      const entry = pending.get(response.id);
      if (!entry) return;
      pending.delete(response.id);
      clearTimeout(entry.timer);

      if (response.ok) entry.resolve(response.result);
      else entry.reject(new SaroniteWebError(
        response.error?.code ?? 'INTERNAL_ERROR',
        response.error?.message ?? 'Saronite request failed',
      ));
      return;
    }

    if (event.data.kind === 'event') {
      for (const listener of listeners) listener(event.data);
    }
  };

  sourceWindow.addEventListener('message', onMessage);

  return {
    request<TPayload = unknown, TResult = unknown>(
      capability: string,
      method: string,
      payload?: TPayload,
      timeoutMs = defaultTimeoutMs,
    ): Promise<TResult> {
      const id = createCorrelationId('web');
      const request: SaroniteRequest<TPayload> = {
        protocolVersion: SARONITE_PROTOCOL_VERSION,
        kind: 'request',
        id,
        capability,
        method,
        ...(payload === undefined ? {} : { payload }),
        timeoutMs,
      };

      return new Promise<TResult>((resolve, reject) => {
        const timer = setTimeout(() => {
          pending.delete(id);
          reject(new SaroniteWebError('TIMEOUT', `Saronite request timed out: ${capability}.${method}`));
        }, timeoutMs);

        pending.set(id, { resolve, reject, timer });
        options.target.postMessage(request, targetOrigin);
      });
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    close() {
      sourceWindow.removeEventListener('message', onMessage);
      for (const entry of pending.values()) {
        clearTimeout(entry.timer);
        entry.reject(new SaroniteWebError('INVALID_STATE', 'Saronite web transport closed'));
      }
      pending.clear();
      listeners.clear();
    },
  };
}
