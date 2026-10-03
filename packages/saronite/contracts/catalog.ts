export type ItundaCatalogStatus =
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'suspended';

/**
 * Published/registered mini-app metadata shared by the developer platform and
 * Saronite hosts. Release integrity fields are first-class so a host can pin
 * the exact approved artifact instead of treating bundleUrl as mutable content.
 */
export type ItundaCatalogEntry = {
  id: string;
  appId: string;
  version: string;
  manifestVersion: 1;
  releaseId: string;
  manifestSha256: string;
  bundleSha256?: string;
  bundleSizeBytes?: number;
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
  publishedAt?: string;
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
