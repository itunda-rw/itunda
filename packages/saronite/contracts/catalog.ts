export type ItundaCatalogStatus =
  | 'draft'
  | 'submitted'
  | 'in_review'
  | 'approved'
  | 'rejected'
  | 'published'
  | 'suspended';

export type ItundaCatalogEntry = {
  id: string;
  version: string;
  name: string;
  category: string;
  description: string;
  bundleUrl: string;
  iconUrl: string;
  permissions: string[];
  status: ItundaCatalogStatus;
  submittedAt?: string;
  reviewedAt?: string;
  publishedAt?: string;
};

export type ItundaCatalogTransition =
  | { from: 'draft'; to: 'submitted' }
  | { from: 'submitted'; to: 'in_review' }
  | { from: 'in_review'; to: 'approved' | 'rejected' }
  | { from: 'approved'; to: 'published' }
  | { from: 'published'; to: 'suspended' }
  | { from: 'suspended'; to: 'published' };

export const ITUNDA_CATALOG_TRANSITIONS: readonly ItundaCatalogTransition[] = [
  { from: 'draft', to: 'submitted' },
  { from: 'submitted', to: 'in_review' },
  { from: 'in_review', to: 'approved' },
  { from: 'in_review', to: 'rejected' },
  { from: 'approved', to: 'published' },
  { from: 'published', to: 'suspended' },
  { from: 'suspended', to: 'published' },
];

export function canTransitionCatalogStatus(
  from: ItundaCatalogStatus,
  to: ItundaCatalogStatus,
): boolean {
  return ITUNDA_CATALOG_TRANSITIONS.some(
    (transition) => transition.from === from && transition.to === to,
  );
}
