import assert from 'node:assert/strict';
import {
  SARONITE_PROTOCOL_NAME,
  SARONITE_PROTOCOL_VERSION,
  createEnvelope,
  createRequestId,
  isSaroniteProtocolMessage,
} from './protocol';

const requestId = createRequestId('test');
assert.match(requestId, /^test_/);

const request = createEnvelope('request', requestId);
assert.equal(request.protocol, SARONITE_PROTOCOL_NAME);
assert.equal(request.version, SARONITE_PROTOCOL_VERSION);
assert.equal(request.kind, 'request');
assert.equal(request.requestId, requestId);
assert.equal(typeof request.timestamp, 'number');

assert.equal(isSaroniteProtocolMessage(request), true);
assert.equal(
  isSaroniteProtocolMessage({
    protocol: SARONITE_PROTOCOL_NAME,
    version: SARONITE_PROTOCOL_VERSION,
    kind: 'event',
    requestId: 'evt_1',
    timestamp: Date.now(),
  }),
  true,
);
assert.equal(
  isSaroniteProtocolMessage({
    protocol: 'other',
    version: 1,
    kind: 'request',
    requestId: 'req_1',
    timestamp: Date.now(),
  }),
  false,
);
