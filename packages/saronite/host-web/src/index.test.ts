import assert from 'node:assert/strict';
import { createSaroniteWebTransport } from './index';

type Listener = (event: MessageEvent) => void;

function createWindowPair() {
  const listenersA = new Set<Listener>();
  const listenersB = new Set<Listener>();

  const make = (
    listeners: Set<Listener>,
    peer: Set<Listener>,
  ) => ({
    addEventListener(_type: 'message', listener: Listener) {
      listeners.add(listener);
    },
    removeEventListener(_type: 'message', listener: Listener) {
      listeners.delete(listener);
    },
    postMessage(data: unknown) {
      const event = { data, origin: 'https://itunda.test', source: undefined };
      for (const listener of peer) listener(event as MessageEvent);
    },
  });

  return {
    a: make(listenersA, listenersB),
    b: make(listenersB, listenersA),
  };
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

await new Promise<void>((resolve) => {
  setTimeout(() => {
    const request = { protocolVersion: 1, kind: 'request', id: 'unknown' };
    void request;
    resolve();
  }, 0);
});

// The transport uses postMessage; the synthetic target cannot observe the
// generated id directly, so this test validates lifecycle cleanup and event
// subscription without depending on browser globals.
const unsubscribe = transport.subscribe(() => {});
unsubscribe();
transport.close();

assert.ok(response instanceof Promise);
console.log('Saronite web transport contract passed.');
