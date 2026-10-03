import assert from 'node:assert/strict';
import {
  createDebuggerSession,
  type SaroniteDebuggerTransport,
} from './index';
import {
  SARONITE_PROTOCOL_VERSION,
  type SaroniteMessage,
  type SaroniteRequest,
} from '@itunda/saronite-protocol';

function createTransport() {
  let listener: ((message: SaroniteMessage) => void) | undefined;
  const sent: SaroniteMessage[] = [];
  let closed = false;

  const transport: SaroniteDebuggerTransport = {
    async send(message) {
      if (closed) throw new Error('transport closed');
      sent.push(message);
    },
    async close() {
      closed = true;
    },
    onMessage(handler) {
      listener = handler;
      return () => { listener = undefined; };
    },
  };

  return {
    transport,
    sent,
    receive(message: SaroniteMessage) {
      listener?.(message);
    },
  };
}

const first = createTransport();
const session = createDebuggerSession('android', first.transport);

assert.equal(session.target, 'android');
assert.equal(session.getState().target, 'android');

const events: string[] = [];
const unsubscribe = session.subscribe((event) => events.push(event.event));

const requestPromise = session.sendRequest<undefined, { userId: string }>({
  capability: 'identity',
  method: 'getCurrentIdentity',
});
assert.equal(first.sent.length, 1);
const request = first.sent[0] as SaroniteRequest;
assert.equal(request.kind, 'request');
assert.equal(request.protocolVersion, SARONITE_PROTOCOL_VERSION);
assert.equal(request.capability, 'identity');

first.receive({
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'event',
  id: 'evt-1',
  event: 'lifecycle.changed',
  lifecycle: 'visible',
});
first.receive({
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'event',
  id: 'evt-2',
  event: 'permission.changed',
  permission: { name: 'identity:read', state: 'granted' },
});
first.receive({
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'response',
  id: request.id,
  ok: true,
  result: { userId: 'demo-user' },
});

const response = await requestPromise;
assert.equal(response.ok, true);
assert.deepEqual(response.result, { userId: 'demo-user' });
assert.deepEqual(events, ['lifecycle.changed', 'permission.changed']);

const state = session.getState();
assert.equal(state.lifecycle, 'visible');
assert.equal(state.permissions['identity:read']?.state, 'granted');
assert.equal(state.requests[0]?.status, 'success');
assert.equal(state.requests[0]?.response?.id, request.id);
assert.equal(state.events.length, 2);

unsubscribe();

// Error responses are retained as diagnostics.
const errorPromise = session.sendRequest({
  capability: 'payments',
  method: 'request',
});
const errorRequest = first.sent[1] as SaroniteRequest;
first.receive({
  protocolVersion: SARONITE_PROTOCOL_VERSION,
  kind: 'response',
  id: errorRequest.id,
  ok: false,
  error: { code: 'PERMISSION_DENIED', message: 'Permission denied' },
});
const errorResponse = await errorPromise;
assert.equal(errorResponse.ok, false);
assert.equal(session.getState().requests.at(-1)?.status, 'error');

// Request timeout is recorded.
const timeoutPromise = session.sendRequest({
  capability: 'identity',
  method: 'slow',
  timeoutMs: 10,
});
await assert.rejects(timeoutPromise, /timed out/);
assert.equal(session.getState().requests.at(-1)?.status, 'timeout');

// Closing rejects pending requests and closes transport.
const pendingPromise = session.sendRequest({
  capability: 'identity',
  method: 'pending',
  timeoutMs: 5000,
});
await session.close();
await assert.rejects(pendingPromise, /session closed/);
assert.equal(session.getState().requests.at(-1)?.status, 'error');

console.log('Saronite debugger contract passed.');
