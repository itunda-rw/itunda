import { createSaroniteMockHost } from './mock.ts';

const host = createSaroniteMockHost();
let notifications = 0;
const unsubscribe = host.subscribe(() => { notifications += 1; });

if (host.request(
  'location',
  'getCurrentLocation',
  () => ({ latitude: -1.94, longitude: 30.06 }),
  { permission: 'location', correlationId: 'map-search-001' },
) !== undefined) {
  throw new Error('denied permissions must not execute a capability');
}

host.setPermission('location', true);

const location = host.request(
  'location',
  'getCurrentLocation',
  () => ({ latitude: -1.94, longitude: 30.06 }),
  { permission: 'location', correlationId: 'map-search-001' },
);

if (!location || location.latitude !== -1.94) throw new Error('granted mock capability did not return its value');

host.request('camera', 'openCamera', () => undefined, { supported: false });
host.request('test', 'throws', () => { throw new Error('simulated failure'); }, { correlationId: 'failure-001' });
host.setLifecycle('hidden');

if (host.state.calls.length !== 4) throw new Error('expected denied, ok, unsupported, and error call records');
if (host.state.calls[0]?.status !== 'denied') throw new Error('permission denial was not recorded');
if (host.state.calls[0]?.correlationId !== 'map-search-001') throw new Error('correlation ID was not recorded on denied call');
if (host.state.calls[0]?.requestId !== 'mock-1') throw new Error('request ID was not generated');
if (host.state.calls[1]?.status !== 'ok') throw new Error('successful capability was not recorded');
if (host.state.calls[2]?.status !== 'unsupported') throw new Error('unsupported capability was not recorded');
if (host.state.calls[3]?.status !== 'error' || host.state.calls[3]?.detail !== 'simulated failure') {
  throw new Error('thrown handler error was not recorded safely');
}
if (host.state.lifecycle !== 'hidden') throw new Error('lifecycle transition was not recorded');
if (notifications < 6) throw new Error('host state changes did not notify DevTools subscribers');

const callsBeforeClear = host.state.calls.length;
host.clearCalls();
if (callsBeforeClear !== 4 || host.state.calls.length !== 0) throw new Error('clearCalls did not clear request history');
if (notifications < 7) throw new Error('clearing calls did not notify subscribers');
host.request('test', 'after-clear', () => true);
if (host.state.calls[0]?.requestId !== 'mock-5') {
  throw new Error('request IDs must remain monotonic after request history is cleared');
}

unsubscribe();
const notificationsBeforeChange = notifications;
host.setLifecycle('visible');
if (notifications !== notificationsBeforeChange) throw new Error('unsubscribe did not remove listener');

const seeded = createSaroniteMockHost({
  calls: [{
    requestId: 'mock-8',
    capability: 'seed',
    method: 'existing',
    status: 'ok',
    timestamp: 1,
  }],
});
seeded.request('test', 'next', () => true);
if (seeded.state.calls[1]?.requestId !== 'mock-9') {
  throw new Error('request IDs must remain unique when the host starts with existing history');
}
