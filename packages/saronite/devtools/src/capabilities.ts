/**
 * Canonical Saronite capability names and method allow-list.
 *
 * Keep this contract host-agnostic: native hosts and the browser-only
 * DevTools mock can validate a capability without importing native code.
 */
export const SARONITE_CAPABILITIES = {
  identity: { read: 'identity:read' },
  location: { read: 'location:read' },
} as const;

export const SARONITE_METHODS = {
  identity: ['getCurrentIdentity'],
  location: ['getCurrentLocation'],
  navigation: ['open', 'close'],
} as const;

export type SaroniteCapabilityName = keyof typeof SARONITE_METHODS;
export type SaroniteMethodName = (typeof SARONITE_METHODS)[SaroniteCapabilityName][number];

const CAPABILITY_PERMISSIONS: Partial<Record<SaroniteCapabilityName, string>> = {
  identity: SARONITE_CAPABILITIES.identity.read,
  location: SARONITE_CAPABILITIES.location.read,
};

/** Returns the permission required to invoke a capability, if any. */
export function permissionFor(capability: string): string | undefined {
  if (!Object.prototype.hasOwnProperty.call(CAPABILITY_PERMISSIONS, capability)) {
    return undefined;
  }
  return CAPABILITY_PERMISSIONS[capability as SaroniteCapabilityName];
}

/** Checks a method against the canonical capability contract. */
export function supportsMethod(capability: string, method: string): boolean {
  if (!Object.prototype.hasOwnProperty.call(SARONITE_METHODS, capability)) return false;
  return (SARONITE_METHODS[capability as SaroniteCapabilityName] as readonly string[]).includes(method);
}
