import { apiFetch } from './api';
import { toPagedQueue } from './queues';

// Extracted from queues.ts (Bank product-completeness pass, cycle 2, 2026-09-08)
// once that file crossed the 500-line file-size-lint guideline -- these two loan
// review queues are a cohesive, self-contained domain slice (see
// docs/ARCHITECTURE_GUIDELINES.md §2, "code that changes together lives together").

// Real Bank product-completeness pass (2026-09-06) -- Bank's first ops-mfe review
// queue (see VupLoanAdminController's own doc comment for why it's scoped to VUP
// loans only). Same real human-review-queue shape as PropertyOwnershipSubmission
// (queues.ts): a flagged row, decide with an optional note.
export interface VupLoanReview {
  id: string;
  userId: string;
  principalAmount: number;
  outstandingPrincipal: number;
  interestRate: number;
  status: string;
  dueDate: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
}

export const fetchVupLoanReviewQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: VupLoanReview[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/loans/vup-overdue?page=${page}`,
  ).then(toPagedQueue);

export const decideVupLoanReview = (loanId: string, writeOff: boolean, note?: string) =>
  apiFetch(`/api/v1/system/loans/vup-overdue/${loanId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ writeOff, note }),
  });

// Real overdue detection + admin review queue (Bank product-completeness pass,
// cycle 2, 2026-09-08) -- HarvestAdvance.repaymentDueDate had been stored on
// every row since the feature shipped but was never checked against anything.
// Same shape as VupLoanReview above; HarvestAdvance has no direct userId field
// (only membershipId, an honest v1 -- no join built to resolve the real farmer's
// name/userId here, matching the entity's own real shape).
export interface HarvestAdvanceReview {
  id: string;
  membershipId: string;
  principalAmount: number;
  status: string;
  repaymentDueDate: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
}

export const fetchHarvestAdvanceReviewQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: HarvestAdvanceReview[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/loans/harvest-overdue?page=${page}`,
  ).then(toPagedQueue);

export const decideHarvestAdvanceReview = (advanceId: string, writeOff: boolean, note?: string) =>
  apiFetch(`/api/v1/system/loans/harvest-overdue/${advanceId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ writeOff, note }),
  });
