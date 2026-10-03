export type ItundaCatalogStatus =
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'suspended';

export type ItundaCatalogEntry = {
  id: string;
  appId: string;
  version: string;
  manifestVersion: 1;
  name: string;
  category: string;
  description: string;
  bundleUrl: string;
  iconUrl?: string;
  permissions: string[];
  status: ItundaCatalogStatus;
  createdAt?: string;
  reviewedAt?: string;
  reviewedBy?: string;
  decisionReason?: string;
};

export type ItundaCatalogTransition =
  | { from: 'pending'; to: 'approved' | 'rejected' }
  | { from: 'approved'; to: 'suspended' };

export const ITUNDA_CATALOG_TRANSITIONS: readonly ItundaCatalogTransition[] = [
  { from: 'pending', to: 'approved' },
  { from: 'pending', to: 'rejected' },
  { from: 'approved', to: 'suspended' },
];

export function canTransitionCatalogStatus(
  from: ItundaCatalogStatus,
  to: ItundaCatalogStatus,
): boolean {
  return ITUNDA_CATALOG_TRANSITIONS.some(
    (transition) => transition.from === from && transition.to === to,
  );
}
