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
}

export const fetchDiscoverItems = () =>
  apiFetch<{ success: boolean; items: DiscoverItem[] }>('/api/v1/discover').then((r) => r.items);
