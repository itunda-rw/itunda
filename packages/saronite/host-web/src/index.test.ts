import assert from 'node:assert/strict';
import { createSaroniteWebTransport } from './index.ts';

type Listener = (event: MessageEvent) => void;

function createWindowPair() {
  const listenersA = new Set<Listener>();
  const listenersB = new Set<Listener>();

  const make = (
    listeners: Set<Listener>,
    peer: Set<Listener>,
    self: object,
  ) => ({
    addEventListener(_type: 'message', listener: Listener) {
      listeners.add(listener);
    },
    removeEventListener(_type: 'message', listener: Listener) {
      listeners.delete(listener);
    },
    postMessage(data: unknown) {
      const event = { data, origin: 'https://itunda.test', source: self };
      for (const listener of peer) listener(event as MessageEvent);
    },
  });

  const a = make(listenersA, listenersB, {});
  const b = make(listenersB, listenersA, {});
  return { a, b };
}

const pair = createWindowPair();
const sourceWindow = pair.a as unknown as Window;
const targetWindow = pair.b as unknown as Window;

const transport = createSaroniteWebTransport({
  target: targetWindow,
  sourceWindow,
  targetOrigin: 'https://itunda.test',
  timeoutMs: 1000,
});

const response = transport.request('identity', 'getCurrentIdentity');
await assert.rejects(response, (error: Error) => error.message.includes('timed out'));

const unsubscribe = transport.subscribe(() => {});
unsubscribe();
transport.close();

assert.ok(response instanceof Promise);
console.log('Saronite web transport contract passed.');
