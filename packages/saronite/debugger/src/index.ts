import {
  SARONITE_PROTOCOL_VERSION,
  createCorrelationId,
  type SaroniteEvent,
  type SaroniteMessage,
  type SaroniteRequest,
  type SaroniteResponse,
} from '@itunda/saronite-protocol';

export type SaroniteDebuggerTarget = 'android' | 'ios' | 'web';

export type SaroniteDebuggerTransport = {
  send(message: SaroniteMessage): Promise<void>;
  close(): Promise<void>;
  onMessage(handler: (message: SaroniteMessage) => void): () => void;
};

export type SaroniteDebuggerRequestRecord = {
  id: string;
  capability: string;
  method: string;
  startedAt: number;
  completedAt?: number;
  status: 'pending' | 'success' | 'error' | 'timeout';
  response?: SaroniteResponse;
};

export type SaroniteDebuggerState = {
  target: SaroniteDebuggerTarget;
  connectedAt: number;
  lifecycle?: SaroniteEvent['lifecycle'];
  permissions: Record<string, SaroniteEvent['permission']>;
  requests: SaroniteDebuggerRequestRecord[];
  events: SaroniteEvent[];
};

export type SaroniteDebuggerSession = {
  target: SaroniteDebuggerTarget;
  connectedAt: number;
  getState(): SaroniteDebuggerState;
  sendRequest<TPayload, TResult>(
    request: Omit<SaroniteRequest<TPayload>, 'protocolVersion' | 'kind' | 'id'>,
  ): Promise<SaroniteResponse<TResult>>;
  subscribe(handler: (event: SaroniteEvent) => void): () => void;
  close(): Promise<void>;
};

export function createDebuggerSession(
  target: SaroniteDebuggerTarget,
  transport: SaroniteDebuggerTransport,
): SaroniteDebuggerSession {
  type Pending = {
    resolve: (response: SaroniteResponse) => void;
    reject: (error: Error) => void;
    record: SaroniteDebuggerRequestRecord;
    timer?: ReturnType<typeof setTimeout>;
  };

  const connectedAt = Date.now();
  const pending = new Map<string, Pending>();
  const subscribers = new Set<(event: SaroniteEvent) => void>();
  const requests: SaroniteDebuggerRequestRecord[] = [];
  const events: SaroniteEvent[] = [];
  const permissions: Record<string, SaroniteEvent['permission']> = {};
  let lifecycle: SaroniteEvent['lifecycle'];
  let closed = false;

  const state = (): SaroniteDebuggerState => ({
    target,
    connectedAt,
    lifecycle,
    permissions: { ...permissions },
    requests: requests.slice(-100),
    events: events.slice(-100),
  });

  const unsubscribeTransport = transport.onMessage((message) => {
    if (message.kind === 'response') {
      const entry = pending.get(message.id);
      if (!entry) return;
      pending.delete(message.id);
      if (entry.timer) clearTimeout(entry.timer);
      entry.record.completedAt = Date.now();
      entry.record.status = message.ok
        ? 'success'
        : message.error?.code === 'TIMEOUT' ? 'timeout' : 'error';
      entry.record.response = message;
      entry.resolve(message);
      return;
    }

    if (message.kind === 'event') {
      events.push(message);
      if (message.lifecycle) lifecycle = message.lifecycle;
      if (message.permission) permissions[message.permission.name] = message.permission;
      if (events.length > 100) events.shift();
      subscribers.forEach((subscriber) => subscriber(message));
    }
  });

  const sendRequest = async <TPayload, TResult>(
    request: Omit<SaroniteRequest<TPayload>, 'protocolVersion' | 'kind' | 'id'>,
  ): Promise<SaroniteResponse<TResult>> => {
    if (closed) throw new Error('Saronite debugger session is closed.');

    const id = createCorrelationId('dbg');
    const record: SaroniteDebuggerRequestRecord = {
      id,
      capability: request.capability,
      method: request.method,
      startedAt: Date.now(),
      status: 'pending',
    };
    requests.push(record);
    if (requests.length > 100) requests.shift();

    const message: SaroniteRequest<TPayload> = {
      ...request,
      protocolVersion: SARONITE_PROTOCOL_VERSION,
      kind: 'request',
      id,
    };

    const timeoutMs = request.timeoutMs && request.timeoutMs > 0 ? request.timeoutMs : 30_000;

    return new Promise<SaroniteResponse<TResult>>((resolve, reject) => {
      const timer = setTimeout(() => {
        const entry = pending.get(id);
        if (!entry) return;
        pending.delete(id);
        entry.record.completedAt = Date.now();
        entry.record.status = 'timeout';
        reject(new Error('Saronite debugger request timed out.'));
      }, timeoutMs);

      pending.set(id, {
        resolve: resolve as (response: SaroniteResponse) => void,
        reject,
        record,
        timer,
      });

      void transport.send(message).catch((error: unknown) => {
        const entry = pending.get(id);
        if (!entry) return;
        pending.delete(id);
        if (entry.timer) clearTimeout(entry.timer);
        entry.record.completedAt = Date.now();
        entry.record.status = 'error';
        reject(error instanceof Error ? error : new Error(String(error)));
      });
    });
  };

  return {
    target,
    connectedAt,
    getState: state,
    sendRequest,
    subscribe(handler) {
      subscribers.add(handler);
      return () => subscribers.delete(handler);
    },
    async close() {
      if (closed) return;
      closed = true;
      unsubscribeTransport();
      for (const [id, entry] of pending) {
        if (entry.timer) clearTimeout(entry.timer);
        entry.record.completedAt = Date.now();
        entry.record.status = 'error';
        entry.reject(new Error('Saronite debugger session closed.'));
        pending.delete(id);
      }
      subscribers.clear();
      await transport.close();
    },
  };
}
