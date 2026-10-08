import assert from 'node:assert/strict';
import { createSaroniteRelayServer } from './relay-server.js';

const relay = createSaroniteRelayServer({ port: 0 });
const address = await relay.listen();

try {
  const response = await fetch(`http://${address.host}:${address.port}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    ok: true,
    service: 'saronite-debug-relay',
    sessions: 0,
  });
} finally {
  await relay.close();
}
