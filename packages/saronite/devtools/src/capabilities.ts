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
