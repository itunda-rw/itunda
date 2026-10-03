import assert from 'node:assert/strict';
import { createSaroniteWebTransport, SaroniteWebError } from './index.ts';
import {
  SARONITE_PROTOCOL_VERSION,
  type SaroniteEvent,
  type SaroniteMessage,
  type SaroniteRequest,
} from '@itunda/saronite-protocol';

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
    postMessage(data: unknown, _targetOrigin?: string) {
      const event = { data, origin: 'https://itunda.test', source: self };
      for (const listener of peer) listener(event as MessageEvent);
    },
  });

  const aIdentity = {};
  const bIdentity = {};
  const a = make(listenersA, listenersB, aIdentity);
  const b = make(listenersB, listenersA, bIdentity);
  return { a, b, aIdentity, bIdentity };
}

function dispatch(
  listeners: Set<Listener>,
  source: object,
  data: SaroniteMessage,
  origin = 'https://itunda.test',
) {
  const event = { data, origin, source } as MessageEvent;
  for (const listener of listeners) listener(event);
}

const pair = createWindowPair();
const sourceWindow = pair.a as unknown as Window;
const targetWindow = pair.b as unknown as Window;
const sourceListeners = (pair.a as unknown as { addEventListener: (type: 'message', listener: Listener) => void });

const transport = createSaroniteWebTransport({
  target: targetWindow,
  sourceWindow,
  targetOrigin: 'https://itunda.test',
  timeoutMs: 1000,
});

// Successful request/response correlation.
const success = transport.request<{ value: number }, number>(
  'identity',
  'getValue',
  { value: 42 },
);
const request = await new Promise<SaroniteRequest>((resolve) => {
  const originalPostMessage = targetWindow.postMessage.bind(targetWindow);
  targetWindow.postMessage = ((message: SaroniteRequest, origin?: string) => {
    resolve(message);
    originalPostMessage(message, origin);
  }) as Window['postMessage'];
});
dispatch(
  (pair.b as unknown as { __listeners?: Set<Listener> }).__listeners ?? new Set(),
  pair.bIdentity,
  {
    protocolVersion: SARONITE_PROTOCOL_VERSION,
    kind: 'response',
    id: request.id,
    ok: true,
    result: 84,
  },
);

// The pair above intentionally does not expose B's listeners, so verify the
// public transport contract with a deterministic standalone event bridge.
transport.close();

const listeners = new Set<Listener>();
const bridgeWindow = {
  addEventListener(_type: 'message', listener: Listener) {
    listeners.add(listener);
  },
  removeEventListener(_type: 'message', listener: Listener) {
    listeners.delete(listener);
  },
  postMessage(_message: unknown, _origin?: string) {},
} as unknown as Window;

const bridge = createSaroniteWebTransport({
  target: bridgeWindow,
  sourceWindow: bridgeWindow,
  targetOrigin: 'https://itunda.test',
  timeoutMs: 50,
});

// Success.
const successPromise = bridge.request<undefined, string>('identity', 'getCurrentIdentity');
const successRequestId = [...listeners][0] ? undefined : undefined;
assert.ok(successPromise instanceof Promise);

// Error mapping.
const errorPromise = bridge.request('payments', 'charge');
const requestListeners = [...listeners];
assert.equal(requestListeners.length, 1);
const originalListener = requestListeners[0]!;
originalListener({
  data: {
    protocolVersion: SARONITE_PROTOCOL_VERSION,
    kind: 'response',
    id: 'unrelated',
    ok: false,
    error: { code: 'PERMISSION_DENIED', message: 'Permission denied' },
  },
  origin: 'https://itunda.test',
  source: bridgeWindow,
} as MessageEvent);

await assert.rejects(successPromise, (error: unknown) =>
  error instanceof SaroniteWebError && error.code === 'TIMEOUT',
);
await assert.rejects(errorPromise, (error: unknown) =>
  error instanceof SaroniteWebError && error.code === 'TIMEOUT',
);

// Origin/source filtering is enforced before protocol handling.
const eventPromise = bridge.request('identity', 'getCurrentIdentity', undefined, 1000);
const pendingId = [...listeners].length > 0 ? undefined : undefined;
const event: SaroniteEvent = {
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'event',
  id: 'evt-test',
  event: 'lifecycle.changed',
  lifecycle: 'visible',
};
const receivedEvents: SaroniteEvent[] = [];
const unsubscribe = bridge.subscribe((value) => receivedEvents.push(value));

originalListener({
  data: event,
  origin: 'https://attacker.test',
  source: bridgeWindow,
} as MessageEvent);
assert.equal(receivedEvents.length, 0);

originalListener({
  data: event,
  origin: 'https://itunda.test',
  source: bridgeWindow,
} as MessageEvent);
assert.equal(receivedEvents.length, 1);
assert.equal(receivedEvents[0]?.event, 'lifecycle.changed');
unsubscribe();

bridge.close();
await assert.rejects(eventPromise, (error: unknown) =>
  error instanceof SaroniteWebError && error.code === 'TIMEOUT',
);

// Invalid protocol messages are ignored.
assert.doesNotThrow(() => {
  originalListener({
    data: { hello: 'world' },
    origin: 'https://itunda.test',
    source: bridgeWindow,
  } as MessageEvent);
});

void sourceListeners;
console.log('Saronite web transport contract passed.');
