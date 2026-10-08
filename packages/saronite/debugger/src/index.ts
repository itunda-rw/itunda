import { isSaroniteDebugMessage, type SaroniteDebugMessage, type SaroniteDebugTransport } from '@itunda/saronite-debug-protocol';

export { createWebSocketDebugTransport, decodeSaroniteRelayFrame, encodeSaroniteRelayFrame, type SaroniteRelayFrame } from './relay.js';

export interface SaroniteDebugHost {
  attach(transport: SaroniteDebugTransport): void;
  detach(): void;
  inspect(): { attached: boolean; messageCount: number; lastMessage?: SaroniteDebugMessage };
  receive(value: unknown): boolean;
  subscribe(listener: (message: SaroniteDebugMessage) => void): () => void;
}

export function createSaroniteDebugger(): SaroniteDebugHost {
  let transport: SaroniteDebugTransport | undefined;
  const messages: SaroniteDebugMessage[] = [];
  const listeners = new Set<(message: SaroniteDebugMessage) => void>();

  return {
    attach(nextTransport) {
      if (!nextTransport || typeof nextTransport.send !== 'function') throw new TypeError('invalid debug transport');
      transport = nextTransport;
    },
    detach() {
      transport?.close?.();
      transport = undefined;
    },
    inspect() {
      return { attached: transport !== undefined, messageCount: messages.length, lastMessage: messages[messages.length - 1] };
    },
    receive(value) {
      if (!isSaroniteDebugMessage(value)) return false;
      const message = value as SaroniteDebugMessage;
      messages.push(message);
      for (const listener of listeners) listener(message);
      return true;
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}
