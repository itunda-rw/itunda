import { apiFetch } from './api';

// Real Naver 지식iN (Knowledge iN)-style open-topic community Q&A (item 225) -- see
// the backend's KnowledgeService doc comment for the full sourced account. A
// genuinely different shape from the trip/rental features (Rides, Bike, Parking,
// Bus): no account movement, no location -- a content + social-reputation mechanic.

export interface KnowledgeCategory {
  id: string;
  label: string;
}

export interface KnowledgeQuestion {
  id: string;
  askerId: string;
  category: string;
  title: string;
  body: string;
  adoptedAnswerId: string | null;
  createdAt: string;
}

export interface KnowledgeAnswer {
  id: string;
  questionId: string;
  answererId: string;
  body: string;
  isAdopted: boolean;
  createdAt: string;
}

export const fetchKnowledgeCategories = () =>
  apiFetch<{ success: boolean; categories: KnowledgeCategory[] }>('/api/v1/knowledge/categories').then((r) => r.categories);

export const postKnowledgeQuestion = (category: string, title: string, body: string) =>
  apiFetch<{ success: boolean; question: KnowledgeQuestion }>('/api/v1/knowledge/questions', {
    method: 'POST',
    body: JSON.stringify({ category, title, body }),
  }).then((r) => r.question);

// Real pagination-discard fix (systemic gap named in the Knowledge
// product-completeness pass, 2026-09-07 -- confirmed cross-cutting via
// lib/community.ts's identical shape, deferred at the time; picked up here as
// the first concrete instance). The backend's real Pageable/pageMeta
// convention was always there; this just stops throwing away `page`/
// `totalPages` so a real "Load more" can page past the first 20 questions,
// matching lib/card.ts's own fetchCardTransactions(page, size) shape.
export const fetchKnowledgeQuestions = (category?: string, page = 0, size = 20) =>
  apiFetch<{ success: boolean; questions: KnowledgeQuestion[]; page: number; totalPages: number }>(
    `/api/v1/knowledge/questions?page=${page}&size=${size}${category ? `&category=${encodeURIComponent(category)}` : ''}`,
  );

export const fetchMyKnowledgeQuestions = (page = 0, size = 20) =>
  apiFetch<{ success: boolean; questions: KnowledgeQuestion[]; page: number; totalPages: number }>(
    `/api/v1/knowledge/questions/my-questions?page=${page}&size=${size}`,
  );

export const fetchMyKnowledgeAnswers = () =>
  apiFetch<{ success: boolean; answers: KnowledgeAnswer[] }>('/api/v1/knowledge/answers/my-answers').then((r) => r.answers);

export const fetchMyKnowledgeReputation = () =>
  apiFetch<{ success: boolean; adoptedAnswerCount: number }>('/api/v1/knowledge/reputation/me').then((r) => r.adoptedAnswerCount);

export const fetchKnowledgeQuestion = (questionId: string) =>
  apiFetch<{ success: boolean; question: KnowledgeQuestion }>(`/api/v1/knowledge/questions/${questionId}`).then((r) => r.question);

export const fetchKnowledgeAnswers = (questionId: string) =>
  apiFetch<{ success: boolean; answers: KnowledgeAnswer[] }>(`/api/v1/knowledge/questions/${questionId}/answers`).then((r) => r.answers);

export const postKnowledgeAnswer = (questionId: string, body: string) =>
  apiFetch<{ success: boolean; answer: KnowledgeAnswer }>(`/api/v1/knowledge/questions/${questionId}/answers`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  }).then((r) => r.answer);

export const adoptKnowledgeAnswer = (questionId: string, answerId: string) =>
  apiFetch<{ success: boolean; answer: KnowledgeAnswer }>(`/api/v1/knowledge/questions/${questionId}/answers/${answerId}/adopt`, {
    method: 'POST',
  }).then((r) => r.answer);
