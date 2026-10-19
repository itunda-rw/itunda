import assert from 'node:assert/strict';
import { createSaroniteScenarioHost } from './scenario.ts';

const host = createSaroniteScenarioHost({
  id: 'checkout-offline',
  permissions: { 'wallet:read': true },
  unsupported: ['camera.openCamera'],
  failures: { 'wallet.getBalance': 'simulated upstream timeout' },
  lifecycle: 'visible',
});

const balance = host.request('wallet', 'getBalance', () => 1250, { permission: 'wallet:read' });
assert.equal(balance, undefined, 'injected failure must not return a success value');
assert.equal(host.state.calls[0]?.status, 'error');
assert.equal(host.state.calls[0]?.detail, 'simulated upstream timeout');
assert.equal(host.state.calls[0]?.correlationId, 'checkout-offline-1');

const camera = host.request('camera', 'openCamera', () => 'opened');
assert.equal(camera, undefined);
assert.equal(host.state.calls[1]?.status, 'unsupported');

const allowed = host.request('wallet', 'getProfile', () => 'Eric', { permission: 'wallet:read' });
assert.equal(allowed, 'Eric');
assert.equal(host.state.calls[2]?.status, 'ok');
assert.equal(host.state.calls[2]?.correlationId, 'checkout-offline-3');

const denied = host.request('location', 'getCurrentLocation', () => 'should not run', { permission: 'location:read' });
assert.equal(denied, undefined);
assert.equal(host.state.calls[3]?.status, 'denied');

for (const id of ['', '   ', 'Uppercase', 'has space', '../escape', 'x', `a${'x'.repeat(63)}`]) {
  assert.throws(
    () => createSaroniteScenarioHost({ id }),
    /Scenario id must be 2–63 lowercase letters, digits, or hyphens and start with a letter/,
    `expected invalid scenario ID to be rejected: ${JSON.stringify(id)}`,
  );
}
assert.doesNotThrow(() => createSaroniteScenarioHost({ id: 'ab' }));
assert.doesNotThrow(() => createSaroniteScenarioHost({ id: `a${'x'.repeat(62)}` }));
console.log('Saronite deterministic scenario tests passed');
