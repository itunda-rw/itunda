import type {
  SaroniteDebuggerSession,
  SaroniteDebuggerState,
} from '@itunda/saronite-debugger';
import type {
  SaroniteEvent,
  SaroniteResponse,
} from '@itunda/saronite-protocol';

export type SaroniteInspector = {
  getSnapshot(): SaroniteDebuggerState;
  subscribe(handler: (state: SaroniteDebuggerState) => void): () => void;
  send<TPayload, TResult>(
    capability: string,
    method: string,
    payload?: TPayload,
    timeoutMs?: number,
  ): Promise<SaroniteResponse<TResult>>;
  close(): Promise<void>;
};

export function createSaroniteInspector(
  session: SaroniteDebuggerSession,
): SaroniteInspector {
  const subscribers = new Set<(state: SaroniteDebuggerState) => void>();

  const emit = () => {
    const snapshot = session.getState();
    subscribers.forEach((subscriber) => subscriber(snapshot));
  };

  const unsubscribe = session.subscribe((_event: SaroniteEvent) => emit());

  return {
    getSnapshot: session.getState,
    subscribe(handler) {
      subscribers.add(handler);
      handler(session.getState());
      return () => subscribers.delete(handler);
    },
    async send(capability, method, payload, timeoutMs) {
      const response = await session.sendRequest({
        capability,
        method,
        payload,
        ...(timeoutMs === undefined ? {} : { timeoutMs }),
      });
      emit();
      return response as SaroniteResponse<TResult>;
    },
    async close() {
      unsubscribe();
      subscribers.clear();
      await session.close();
    },
  };
}
