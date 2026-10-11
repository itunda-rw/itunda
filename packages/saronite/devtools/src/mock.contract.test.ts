import { createSaroniteMockHost } from './mock';

const host = createSaroniteMockHost();

if (host.request('location', 'getCurrentLocation', () => ({ latitude: -1.94, longitude: 30.06 }), { permission: 'location:read' }) !== undefined) {
  throw new Error('denied permissions must not execute a capability');
}

host.setPermission('location:read', true);

const location = host.request(
  'location',
  'getCurrentLocation',
  () => ({ latitude: -1.94, longitude: 30.06 }),
  { permission: 'location:read' },
);

if (!location || location.latitude !== -1.94) throw new Error('granted mock capability did not return its value');

host.request('camera', 'openCamera', () => undefined, { supported: false });

let unknownCapabilityRejected = false;
try {
  host.request('camera', 'openCamera', () => undefined);
} catch (error) {
  unknownCapabilityRejected = error instanceof Error && error.message.includes('Unknown Saronite capability');
}
if (!unknownCapabilityRejected) throw new Error('unknown capabilities must not execute as if supported');

host.setLifecycle('hidden');

if (host.state.calls.length !== 3) throw new Error('expected denied, ok, and unsupported call records');
if (host.state.calls[0]?.status !== 'denied') throw new Error('permission denial was not recorded');
if (host.state.calls[1]?.status !== 'ok') throw new Error('successful capability was not recorded');
if (host.state.calls[2]?.status !== 'unsupported') throw new Error('unsupported capability was not recorded');
if (host.state.lifecycle !== 'hidden') throw new Error('lifecycle transition was not recorded');
