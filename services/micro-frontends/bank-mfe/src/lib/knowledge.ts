import { apiFetch } from './api';

// Real Naver 지식iN (Knowledge iN)-style open-topic community Q&A (item 225) -- see
// the backend's KnowledgeService doc comment for the full sourced account. A
// genuinely different shape from the trip/rental features (Rides, Bike, Parking,
// Bus): no wallet movement, no location -- a content + social-reputation mechanic.

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

export const fetchKnowledgeQuestions = (category?: string) =>
  apiFetch<{ success: boolean; questions: KnowledgeQuestion[] }>(
    `/api/v1/knowledge/questions${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => r.questions);

export const fetchMyKnowledgeQuestions = () =>
  apiFetch<{ success: boolean; questions: KnowledgeQuestion[] }>('/api/v1/knowledge/questions/my-questions').then((r) => r.questions);

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
