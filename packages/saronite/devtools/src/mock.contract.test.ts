import { createSaroniteMockHost } from './mock.ts';

const host = createSaroniteMockHost();

let unpermissionedLocationExecuted = false;
host.request('location', 'getCurrentLocation', () => {
  unpermissionedLocationExecuted = true;
  return { latitude: -1.94, longitude: 30.06 };
});
if (unpermissionedLocationExecuted) throw new Error('capability permissions must be enforced even when callers omit options');

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

host.request('location', 'getCurrentLocation', () => undefined, { supported: false });
host.request('location', 'getCurrentLocation', () => 'must never run', { permission: 'identity:read' });

let unknownCapabilityExecuted = false;
host.request('telepathy', 'readMind', () => {
  unknownCapabilityExecuted = true;
  return 'must never run';
});
if (unknownCapabilityExecuted) throw new Error('unknown capabilities must never execute handlers');

host.setLifecycle('hidden');

if (host.state.calls.length !== 6) throw new Error('expected implicit denial, explicit denial, ok, unsupported, permission mismatch, and unknown-capability records');
if (host.state.calls[0]?.status !== 'denied' || host.state.calls[0]?.detail !== 'location:read') throw new Error('implicit permission denial was not recorded');
if (host.state.calls[1]?.status !== 'denied') throw new Error('explicit permission denial was not recorded');
if (host.state.calls[2]?.status !== 'ok') throw new Error('successful capability was not recorded');
if (host.state.calls[3]?.status !== 'unsupported') throw new Error('unsupported capability was not recorded');
if (host.state.calls[4]?.status !== 'error' || !host.state.calls[4]?.detail?.includes('Permission contract mismatch')) throw new Error('permission mismatch must fail closed');
if (host.state.calls[5]?.status !== 'error' || !host.state.calls[5]?.detail?.includes('Unknown Saronite capability')) {
  throw new Error('unknown capability must be recorded as an explicit error');
}
if (host.state.lifecycle !== 'hidden') throw new Error('lifecycle transition was not recorded');
