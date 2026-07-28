import { apiFetch } from './api';

// Real Karrot-Score-style numeric trust/reputation badge (item 152) -- see
// TrustScoreService's own doc comment for the full account (why a plain 0-1000 score
// starting at 30, not a manner-temperature metaphor -- Karrot's own real UK/Canada
// localization research found that framing confusing for non-Korean users). Every
// Hood browse/nearby/my-listings endpoint already batch-reads a cached
// User.trustScore for the seller/poster/lister badge on each card (see
// lib/marketplace.ts's own TrustScores type) -- this is the separate, real
// GET /api/v1/trust-score endpoint that lets a user see THEIR OWN full factor
// breakdown, mirroring CreditScoreController's shape exactly. Found with zero client
// UI anywhere despite that.
export interface TrustScoreFactor {
  name: string;
  points: number;
  description: string;
}

export interface TrustScoreResult {
  score: number;
  factors: TrustScoreFactor[];
  computedAt: string;
}

export const fetchTrustScore = () =>
  apiFetch<{ success: boolean } & TrustScoreResult>('/api/v1/trust-score').then((r) => r);
