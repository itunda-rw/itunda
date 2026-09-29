import { apiFetch } from './api';

// Real curated promo rail (a CMS-style catalog, not user-specific data) -- see the
// backend's DiscoverController.kt. Purely informational/display, no click-through
// action or money movement, same honest scope Android's own DiscoverSection already
// establishes (found real on backend + Android with zero bank-mfe/iOS client).
export interface DiscoverItem {
  id: string;
  category: string;
  title: string;
  subtitle: string;
  description: string;
  color: string;
  isNew: boolean;
  badge: string | null;
  // Real server-side ranking (2026-08-11) -- see backend DiscoverService's own doc
  // comment (Toss Intelligence-banner research, toss.tech/article/intelligence_banner):
  // the backend now computes real per-user eligibility and priority (KYC prompts
  // outrank a static promo, etc.) instead of returning the same static list to every
  // caller. Higher shows first.
  priority: number;
}

export const fetchDiscoverItems = () =>
  apiFetch<{ success: boolean; items: DiscoverItem[] }>('/api/v1/discover').then((r) => r.items);
