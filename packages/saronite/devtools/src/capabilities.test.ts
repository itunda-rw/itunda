import assert from 'node:assert/strict';
import { permissionFor, SARONITE_CAPABILITIES } from './capabilities';

assert.equal(SARONITE_CAPABILITIES.identity.read, 'identity:read');
assert.equal(SARONITE_CAPABILITIES.location.read, 'location:read');
assert.equal(permissionFor('identity'), 'identity:read');
assert.equal(permissionFor('location'), 'location:read');
assert.equal(permissionFor('navigation'), undefined);

console.log('Saronite capability contract tests passed');
