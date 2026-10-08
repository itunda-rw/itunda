import assert from 'node:assert/strict';
import test from 'node:test';
import {
  clearSaroniteDebugTransport,
  createSaroniteDebugTransport,
  installSaroniteDebugTransport,
  isSaroniteDebugMessage,
} from '../src/index.ts';

test('accepts valid hello and log messages', () => {
  assert.equal(isSaroniteDebugMessage({ version: 1, type: 'hello', sessionId: 's1', platform: 'android' }), true);
  assert.equal(isSaroniteDebugMessage({
    version: 1, type: 'log', id: '1', timestamp: Date.now(),
    direction: 'request', capability: 'payment', method: 'pay',
  }), true);
});

test('rejects malformed and future-version messages', () => {
  assert.equal(isSaroniteDebugMessage({ version: 2, type: 'hello', sessionId: 's1', platform: 'android' }), false);
  assert.equal(isSaroniteDebugMessage({ version: 1, type: 'log', id: '1' }), false);
  assert.equal(isSaroniteDebugMessage(null), false);
});

test('installs and clears a transport on the shared global', () => {
  const received = [];
  const transport = createSaroniteDebugTransport(message => received.push(message));
  installSaroniteDebugTransport(transport);
  globalThis.__saroniteDebugTransport.send({
    version: 1, type: 'hello', sessionId: 's1', platform: 'web',
  });
  assert.equal(received.length, 1);
  clearSaroniteDebugTransport();
  assert.equal('__saroniteDebugTransport' in globalThis, false);
});
