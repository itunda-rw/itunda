import assert from 'node:assert/strict';
import { createSaroniteWebTransport, SaroniteWebError } from './index.ts';
import {
  SARONITE_PROTOCOL_VERSION,
  type SaroniteEvent,
  type SaroniteRequest,
} from '@itunda/saronite-protocol';

type Listener = (event: MessageEvent) => void;

const listeners = new Set<Listener>();
let posted: SaroniteRequest | undefined;

const windowMock = {
  addEventListener(_type: 'message', listener: Listener) {
    listeners.add(listener);
  },
  removeEventListener(_type: 'message', listener: Listener) {
    listeners.delete(listener);
  },
  postMessage(message: unknown, _origin?: string) {
    posted = message as SaroniteRequest;
  },
} as unknown as Window;

function receive(
  data: unknown,
  source: object = windowMock,
  origin = 'https://itunda.test',
) {
  const event = { data, origin, source } as MessageEvent;
  for (const listener of listeners) listener(event);
}

function response(
  id: string,
  result?: unknown,
  error?: { code: string; message: string },
) {
  return {
    protocolVersion: SARONITE_PROTOCOL_VERSION,
    kind: 'response' as const,
    id,
    ok: error === undefined,
    ...(error ? { error } : { result }),
  };
}

const transport = createSaroniteWebTransport({
  target: windowMock,
  sourceWindow: windowMock,
  targetOrigin: 'https://itunda.test',
  timeoutMs: 100,
});

// Successful request/response correlation.
const successPromise = transport.request<undefined, { userId: string }>(
  'identity',
  'getCurrentIdentity',
);
assert.ok(posted);
const successId = posted.id;
receive(response(successId, { userId: 'demo-user' }));
assert.deepEqual(await successPromise, { userId: 'demo-user' });

// Error response is converted to a typed SaroniteWebError.
const errorPromise = transport.request('payments', 'charge');
assert.ok(posted);
const errorId = posted.id;
receive(response(errorId, undefined, {
  code: 'PERMISSION_DENIED',
  message: 'Permission denied',
}));
await assert.rejects(errorPromise, (error: unknown) =>
  error instanceof SaroniteWebError
  && error.code === 'PERMISSION_DENIED'
  && error.message === 'Permission denied',
);

// Event subscription receives valid events.
const receivedEvents: SaroniteEvent[] = [];
const unsubscribe = transport.subscribe((event) => receivedEvents.push(event));
receive({
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'event',
  id: 'evt-test',
  event: 'lifecycle.changed',
  lifecycle: 'visible',
});
assert.equal(receivedEvents.length, 1);
assert.equal(receivedEvents[0]?.event, 'lifecycle.changed');
unsubscribe();
receive({
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'event',
  id: 'evt-ignored',
  event: 'lifecycle.changed',
  lifecycle: 'hidden',
});
assert.equal(receivedEvents.length, 1);

// Origin filtering rejects messages from another origin.
const filteredPromise = transport.request('identity', 'getCurrentIdentity');
assert.ok(posted);
receive(response(posted.id, { userId: 'attacker' }), windowMock, 'https://attacker.test');
await assert.rejects(filteredPromise, (error: unknown) =>
  error instanceof SaroniteWebError && error.code === 'TIMEOUT',
);

// Source filtering rejects messages from an unexpected window.
const foreignWindow = {} as Window;
const sourceFilteredPromise = transport.request('identity', 'getCurrentIdentity');
assert.ok(posted);
receive(response(posted.id, { userId: 'foreign' }), foreignWindow);
await assert.rejects(sourceFilteredPromise, (error: unknown) =>
  error instanceof SaroniteWebError && error.code === 'TIMEOUT',
);

// Invalid protocol payloads are ignored.
assert.doesNotThrow(() => receive({ hello: 'world' }));

// close() rejects pending work and detaches the listener.
const closePromise = transport.request('identity', 'getCurrentIdentity');
transport.close();
await assert.rejects(closePromise, (error: unknown) =>
  error instanceof SaroniteWebError && error.code === 'INVALID_STATE',
);
assert.equal(listeners.size, 0);

console.log('Saronite web transport contract passed.');
