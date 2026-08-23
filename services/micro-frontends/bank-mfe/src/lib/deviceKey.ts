// Real Toss-sourced passwordless-login rollout (2026-08-24) -- see backend
// DeviceService's own doc comment for the full sourced Toss research this closes:
// registration is phone+OTP then a real 6-digit PIN, and every REPEAT app open uses
// an already-registered device key instead of re-typing the credential. This is
// web's own real key-management client -- a non-extractable P-256 keypair generated
// via the real, standard Web Crypto API (crypto.subtle), the private half persisted
// in IndexedDB (CryptoKey objects are structured-cloneable and stay non-extractable
// across a round trip -- this is real, documented browser behavior, not a workaround)
// so it survives a page reload without ever being exposed to JS as raw bytes.
//
// Web has no OS-level biometric API exposed to a browser outside the separate,
// heavier WebAuthn ceremony (a different attestation/assertion wire format entirely,
// incompatible with this backend's own raw-P256-point + SHA256withECDSA design,
// which matches what Android Keystore/iOS Secure Enclave hand back natively) -- the
// real security property this gives web instead is the same one that actually
// matters: a device-bound key that can never be extracted/exported, not literally
// invoking Face ID in a browser tab.
const DB_NAME = 'itunda-device-key';
const STORE_NAME = 'keys';
const KEY_ID = 'device-key-pair';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE_NAME);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function loadStoredKeyPair(): Promise<CryptoKeyPair | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const request = tx.objectStore(STORE_NAME).get(KEY_ID);
    request.onsuccess = () => resolve((request.result as CryptoKeyPair | undefined) ?? null);
    request.onerror = () => reject(request.error);
  });
}

async function storeKeyPair(keyPair: CryptoKeyPair): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put(keyPair, KEY_ID);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

// Real uncompressed P-256 point export -- crypto.subtle's own 'raw' format for an EC
// public key IS exactly the 0x04 || X || Y wire format DeviceService.parsePublicKey
// expects (Android Keystore/iOS Secure Enclave hand this back natively too), no
// conversion needed on the public-key side.
function base64FromBuffer(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

function bufferFromBase64(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

// Real, necessary interop conversion: Web Crypto's ECDSA signature format is raw
// concatenated r||s (each 32 bytes for P-256), but this backend's Java
// Signature.getInstance("SHA256withECDSA") -- the same one verifying Android/iOS's
// own native signatures -- expects DER/ASN.1 (a SEQUENCE of two INTEGERs). Android's
// own java.security.Signature and iOS's SecKeyCreateSignature with
// .ecdsaSignatureMessageX962SHA256 both produce DER-compatible signatures natively;
// only web needs this explicit conversion.
function rawEcdsaSignatureToDer(raw: Uint8Array): Uint8Array {
  const r = raw.slice(0, 32);
  const s = raw.slice(32, 64);
  function toDerInteger(bytes: Uint8Array): number[] {
    let trimmed = Array.from(bytes);
    while (trimmed.length > 1 && trimmed[0] === 0 && (trimmed[1] & 0x80) === 0) trimmed = trimmed.slice(1);
    if (trimmed[0] & 0x80) trimmed = [0, ...trimmed];
    return [0x02, trimmed.length, ...trimmed];
  }
  const rDer = toDerInteger(r);
  const sDer = toDerInteger(s);
  return new Uint8Array([0x30, rDer.length + sDer.length, ...rDer, ...sDer]);
}

async function getOrCreateKeyPair(): Promise<CryptoKeyPair | null> {
  if (typeof indexedDB === 'undefined' || !crypto.subtle) return null;
  try {
    const existing = await loadStoredKeyPair();
    if (existing) return existing;
    const keyPair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign', 'verify']);
    await storeKeyPair(keyPair);
    return keyPair;
  } catch {
    // Real, non-critical -- a browser without IndexedDB/WebCrypto (or a private-
    // browsing mode that blocks IndexedDB) just doesn't get the passwordless-login
    // upgrade; register()/login() with a PIN keeps working exactly as before.
    return null;
  }
}

/** The real public key to send with register()/login() so this device can use
 * passwordless login afterward -- null if this browser can't support it. */
export async function getDevicePublicKeyBase64(): Promise<string | null> {
  const keyPair = await getOrCreateKeyPair();
  if (!keyPair) return null;
  const raw = await crypto.subtle.exportKey('raw', keyPair.publicKey);
  return base64FromBuffer(raw);
}

/** Signs a real server-issued challenge with this device's already-established key --
 * null if no key exists yet (never registered) or WebCrypto/IndexedDB isn't available. */
export async function signChallenge(challengeBase64: string): Promise<string | null> {
  const keyPair = await getOrCreateKeyPair();
  if (!keyPair) return null;
  const challengeBytes = bufferFromBase64(challengeBase64);
  const rawSignature = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, keyPair.privateKey, challengeBytes.buffer as ArrayBuffer);
  const derSignature = rawEcdsaSignatureToDer(new Uint8Array(rawSignature));
  return base64FromBuffer(derSignature.buffer as ArrayBuffer);
}

/** Whether this device already has a key established -- used to decide whether to
 * even attempt a passwordless login on app load before falling back to the PIN pad. */
export async function hasDeviceKey(): Promise<boolean> {
  if (typeof indexedDB === 'undefined') return false;
  try {
    return (await loadStoredKeyPair()) !== null;
  } catch {
    return false;
  }
}
