import {
  isSaroniteMessage,
  type SaroniteMessage,
} from '@itunda/saronite-protocol';

export type SaroniteWebTransport = {
  send(message: SaroniteMessage): Promise<void>;
  close(): Promise<void>;
  onMessage(handler: (message: SaroniteMessage) => void): () => void;
};

export type SaroniteWindowTransportOptions = {
  targetWindow?: Window;
  targetOrigin: string;
  receiveWindow?: Window;
  receiveOrigin?: string;
  sourceWindow?: Window;
};

export function createSaroniteWindowTransport(
  options: SaroniteWindowTransportOptions,
): SaroniteWebTransport {
  const receiveWindow = options.receiveWindow ?? window;
  const targetWindow = options.targetWindow ?? receiveWindow.parent;
  const sourceWindow = options.sourceWindow ?? receiveWindow;

  return {
    async send(message) {
      targetWindow.postMessage(message, options.targetOrigin);
    },
    async close() {},
    onMessage(handler) {
      const listener = (event: MessageEvent<unknown>) => {
        if (options.receiveOrigin && event.origin !== options.receiveOrigin) return;
        if (event.source && event.source !== sourceWindow) return;
        if (!isSaroniteMessage(event.data)) return;
        handler(event.data);
      };
      receiveWindow.addEventListener('message', listener);
      return () => receiveWindow.removeEventListener('message', listener);
    },
  };
}

export function createSaroniteMessagePortTransport(
  port: MessagePort,
): SaroniteWebTransport {
  return {
    async send(message) { port.postMessage(message); },
    async close() { port.close(); },
    onMessage(handler) {
      const listener = (event: MessageEvent<unknown>) => {
        if (isSaroniteMessage(event.data)) handler(event.data);
      };
      port.addEventListener('message', listener);
      port.start();
      return () => port.removeEventListener('message', listener);
    },
  };
}
