export const SARONITE_CAPABILITIES = {
  identity: {
    read: 'identity:read',
  },
  location: {
    read: 'location:read',
  },
  navigation: {
    open: 'navigation:open',
    close: 'navigation:close',
  },
} as const;

export type SaroniteCapabilityName = keyof typeof SARONITE_CAPABILITIES;
export type SaronitePermission = 'identity:read' | 'location:read';

/**
 * Canonical host method registry. Both the browser mock and contract tests
 * consume this map so a method cannot silently drift between the SDK and sandbox.
 */
export const SARONITE_METHODS: Record<SaroniteCapabilityName, readonly string[]> = {
  identity: ['getCurrentIdentity'],
  location: ['getCurrentLocation'],
  navigation: ['open', 'close'],
};

export function permissionFor(
  capability: SaroniteCapabilityName,
): SaronitePermission | undefined {
  switch (capability) {
    case 'identity':
      return 'identity:read';
    case 'location':
      return 'location:read';
    case 'navigation':
      return undefined;
  }
}
