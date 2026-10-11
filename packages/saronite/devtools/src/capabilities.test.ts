import assert from 'node:assert/strict';
import { permissionFor, SARONITE_CAPABILITIES, SARONITE_METHODS } from './capabilities.ts';

assert.equal(SARONITE_CAPABILITIES.identity.read, 'identity:read');
assert.equal(SARONITE_CAPABILITIES.location.read, 'location:read');
assert.equal(permissionFor('identity'), 'identity:read');
assert.equal(permissionFor('location'), 'location:read');
assert.equal(permissionFor('navigation'), undefined);

assert.deepEqual(SARONITE_METHODS.identity, ['getCurrentIdentity']);
assert.deepEqual(SARONITE_METHODS.location, ['getCurrentLocation']);
assert.deepEqual(SARONITE_METHODS.navigation, ['open', 'close']);

for (const [capability, methods] of Object.entries(SARONITE_METHODS)) {
  assert.ok(methods.length > 0, `${capability} must declare at least one supported method`);
  assert.equal(new Set(methods).size, methods.length, `${capability} methods must be unique`);
}

console.log('Saronite capability contract tests passed');
