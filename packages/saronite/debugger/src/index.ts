import {
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

export type SaroniteDebuggerSession = {
  target: SaroniteDebuggerTarget;
  connectedAt: number;
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
  const pending = new Map<string, {
    resolve: (response: SaroniteResponse<unknown>) => void;
  }>();
  const subscribers = new Set<(event: SaroniteEvent) => void>();

  const unsubscribeTransport = transport.onMessage((message) => {
    if (message.kind === 'response') {
      pending.get(message.id)?.resolve(message);
      pending.delete(message.id);
    } else if (message.kind === 'event') {
      subscribers.forEach((subscriber) => subscriber(message));
    }
  });

  return {
    target,
    connectedAt: Date.now(),
    async sendRequest(request) {
      const id = `dbg_${Date.now()}_${Math.random().toString(36).slice(2)}`;
      const message: SaroniteRequest = {
        ...request,
        protocolVersion: 1,
        kind: 'request',
        id,
      };
      return new Promise((resolve) => {
        pending.set(id, { resolve: resolve as (response: SaroniteResponse<unknown>) => void });
        void transport.send(message);
      }) as Promise<SaroniteResponse<unknown>>;
    },
    subscribe(handler) {
      subscribers.add(handler);
      return () => subscribers.delete(handler);
    },
    async close() {
      unsubscribeTransport();
      pending.clear();
      subscribers.clear();
      await transport.close();
    },
  };
}
