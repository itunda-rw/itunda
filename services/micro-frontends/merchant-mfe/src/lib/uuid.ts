// crypto.randomUUID() only exists in a "secure context" (https:// or http://localhost)
// per the Web Crypto spec -- found live 2026-07-20 testing the private-cloud-hosted
// backend over a plain http://<LAN IP> address: it silently threw, and every caller
// (device IDs, Idempotency-Key headers) wrapped it in a try/catch that swallowed the
// error into a generic "Can't connect right now," making the real cause invisible.
// These are idempotency/device-identity values, not security secrets, so a
// Math.random()-based UUID v4 is a safe, honest fallback -- not a security downgrade.
export function randomUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
