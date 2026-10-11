import { createSaroniteMockHost } from './mock';

const host = createSaroniteMockHost();

let handlerExecuted = false;
if (host.request('location', 'getCurrentLocation', () => {
  handlerExecuted = true;
  return { latitude: -1.94, longitude: 30.06 };
}) !== undefined) {
  throw new Error('a capability without its manifest permission must be denied');
}
if (handlerExecuted) throw new Error('denied capability handler must not execute');

host.setPermission('location:read', true);

const location = host.request(
  'location',
  'getCurrentLocation',
  () => ({ latitude: -1.94, longitude: 30.06 }),
);

if (!location || location.latitude !== -1.94) throw new Error('granted mock capability did not return its value');

host.request('navigation', 'open', () => undefined, { supported: false });

let unknownCapabilityRejected = false;
try {
  host.request('camera', 'openCamera', () => undefined);
} catch (error) {
  unknownCapabilityRejected = error instanceof Error && error.message.includes('Unknown Saronite capability');
}
if (!unknownCapabilityRejected) throw new Error('unknown capabilities must not execute as if supported');

let unknownMethodRejected = false;
try {
  host.request('identity', 'readAnything', () => ({ id: 'demo-user' }));
} catch (error) {
  unknownMethodRejected = error instanceof Error && error.message.includes('Unknown Saronite method');
}
if (!unknownMethodRejected) throw new Error('unknown methods must not execute as if supported');

host.setLifecycle('hidden');

if (host.state.calls.length !== 3) throw new Error('expected denied, ok, and unsupported call records');
if (host.state.calls[0]?.status !== 'denied') throw new Error('permission denial was not recorded');
if (host.state.calls[1]?.status !== 'ok') throw new Error('successful capability was not recorded');
if (host.state.calls[2]?.status !== 'unsupported') throw new Error('unsupported capability was not recorded');
if (host.state.lifecycle !== 'hidden') throw new Error('lifecycle transition was not recorded');

// A caller-provided permission must never weaken the SDK's canonical requirement.
let downgradeHandlerExecuted = false;
const downgradeResult = host.request(
  'location',
  'getCurrentLocation',
  () => {
    downgradeHandlerExecuted = true;
    return { latitude: 0, longitude: 0 };
  },
  { permission: 'identity:read' },
);
if (downgradeResult !== undefined || downgradeHandlerExecuted) {
  throw new Error('a caller must not replace the canonical location permission');
}
const downgradeCall = host.state.calls.at(-1);
if (downgradeCall?.status !== 'denied' || downgradeCall.detail !== 'location:read') {
  throw new Error('permission downgrade attempt must be logged against the canonical permission');
}

// Revoking a previously granted permission must affect the very next call.
host.setPermission('location:read', false);
let revokedHandlerExecuted = false;
const revokedResult = host.request('location', 'getCurrentLocation', () => {
  revokedHandlerExecuted = true;
  return { latitude: -1.94, longitude: 30.06 };
});
if (revokedResult !== undefined || revokedHandlerExecuted) {
  throw new Error('revoked permission must block subsequent handler execution');
}

// Exceptions in mocked native handlers are recorded and fail closed.
host.setPermission('identity:read', true);
const failedResult = host.request('identity', 'getCurrentIdentity', () => {
  throw new Error('simulated native bridge failure');
});
if (failedResult !== undefined) throw new Error('failed mock calls must not return a value');
const failedCall = host.state.calls.at(-1);
if (failedCall?.status !== 'error' || failedCall.detail !== 'simulated native bridge failure') {
  throw new Error('mock handler exceptions must be recorded with a useful error detail');
}

host.clearCalls();
const remainingCallCount: number = host.state.calls.length;
if (remainingCallCount !== 0) throw new Error('clearCalls must empty the call log');
