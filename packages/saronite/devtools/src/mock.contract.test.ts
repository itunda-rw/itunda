import { createSaroniteMockHost } from './mock.ts';

const host = createSaroniteMockHost();

if (host.request('location', 'getCurrentLocation', () => ({ latitude: -1.94, longitude: 30.06 }), { permission: 'location' }) !== undefined) {
  throw new Error('denied permissions must not execute a capability');
}

host.setPermission('location', true);

const location = host.request(
  'location',
  'getCurrentLocation',
  () => ({ latitude: -1.94, longitude: 30.06 }),
  { permission: 'location' },
);

if (!location || location.latitude !== -1.94) throw new Error('granted mock capability did not return its value');

host.request('camera', 'openCamera', () => undefined, { supported: false });

let unknownCapabilityExecuted = false;
host.request('telepathy', 'readMind', () => {
  unknownCapabilityExecuted = true;
  return 'must never run';
});
if (unknownCapabilityExecuted) throw new Error('unknown capabilities must never execute handlers');

host.setLifecycle('hidden');

if (host.state.calls.length !== 4) throw new Error('expected denied, ok, unsupported, and unknown-capability records');
if (host.state.calls[0]?.status !== 'denied') throw new Error('permission denial was not recorded');
if (host.state.calls[1]?.status !== 'ok') throw new Error('successful capability was not recorded');
if (host.state.calls[2]?.status !== 'unsupported') throw new Error('unsupported capability was not recorded');
if (host.state.calls[3]?.status !== 'error' || !host.state.calls[3]?.detail?.includes('Unknown Saronite capability')) {
  throw new Error('unknown capability must be recorded as an explicit error');
}
if (host.state.lifecycle !== 'hidden') throw new Error('lifecycle transition was not recorded');
