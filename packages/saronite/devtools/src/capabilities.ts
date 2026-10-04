export const SARONITE_CAPABILITIES = {
  identity: {
    read: 'identity:read',
  },
  auth: {
    signIn: 'auth:sign-in',
    signOut: 'auth:sign-out',
  },
  navigation: {
    open: 'navigation:open',
    close: 'navigation:close',
  },
  environment: {
    get: 'environment:get',
  },
  permissions: {
    request: 'permissions:request',
    status: 'permissions:status',
  },
  storage: {
    get: 'storage:get',
    set: 'storage:set',
    remove: 'storage:remove',
  },
  location: {
    read: 'location:read',
  },
  camera: {
    capture: 'camera:capture',
  },
  contacts: {
    read: 'contacts:read',
  },
  clipboard: {
    read: 'clipboard:read',
    write: 'clipboard:write',
  },
  haptic: {
    impact: 'haptic:impact',
  },
  share: {
    open: 'share:open',
  },
  notifications: {
    schedule: 'notifications:schedule',
  },
  payments: {
    request: 'payments:request',
  },
  analytics: {
    track: 'analytics:track',
  },
  events: {
    emit: 'events:emit',
    subscribe: 'events:subscribe',
  },
  deepLinks: {
    open: 'deep-links:open',
  },
} as const;

export type SaroniteCapabilityName = keyof typeof SARONITE_CAPABILITIES;

export type SaronitePermission =
  | 'identity:read'
  | 'location:read'
  | 'camera:capture'
  | 'contacts:read'
  | 'clipboard:read'
  | 'clipboard:write'
  | 'notifications:schedule'
  | 'payments:request';

export function permissionFor(
  capability: SaroniteCapabilityName,
): SaronitePermission | undefined {
  switch (capability) {
    case 'identity':
      return 'identity:read';
    case 'location':
      return 'location:read';
    case 'camera':
      return 'camera:capture';
    case 'contacts':
      return 'contacts:read';
    case 'clipboard':
      return 'clipboard:read';
    case 'notifications':
      return 'notifications:schedule';
    case 'payments':
      return 'payments:request';
    default:
      return undefined;
  }
}

export const SARONITE_CAPABILITY_DOMAINS = Object.freeze(
  Object.keys(SARONITE_CAPABILITIES) as SaroniteCapabilityName[],
);
