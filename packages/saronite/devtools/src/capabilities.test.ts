import { permissionFor, SARONITE_CAPABILITIES } from './capabilities';

function assertEqual(actual: unknown, expected: unknown, message: string): void {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
}

assertEqual(SARONITE_CAPABILITIES.identity.read, 'identity:read', 'identity permission');
assertEqual(SARONITE_CAPABILITIES.location.read, 'location:read', 'location permission');
assertEqual(permissionFor('identity'), 'identity:read', 'identity capability mapping');
assertEqual(permissionFor('location'), 'location:read', 'location capability mapping');
assertEqual(permissionFor('navigation'), undefined, 'navigation should not require a data permission');

console.log('Saronite capability contract tests passed');
