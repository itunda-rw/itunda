import { apiFetch } from './api';

// Real 이력서 (Karrot 당근알바-style résumé) builder (itunda Hood redesign, 2026-08-28)
// -- see backend Resume.kt's own doc comment. First real web client for these
// endpoints (mirrors Android's own HoodApi.kt/ResumeBuilderScreen.kt).

export interface ResumeStrength {
  id: string;
  label: string;
}

export interface Resume {
  id: string;
  userId: string;
  selfIntro?: string | null;
  // Raw comma-delimited string, same convention as Merchant.closedWeekdays -- split on
  // ',' to render as chips, never re-parsed as anything richer.
  strengths?: string | null;
  additionalInfo?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ResumeExperience {
  id: string;
  resumeId: string;
  company: string;
  role: string;
  period: string;
  description?: string | null;
  createdAt: string;
}

export interface ResumeEducation {
  id: string;
  resumeId: string;
  school: string;
  degree?: string | null;
  major?: string | null;
  createdAt: string;
}

export interface ResumeCertification {
  id: string;
  resumeId: string;
  name: string;
  issuedDate?: string | null;
  createdAt: string;
}

export interface ResumeDetail {
  resume: Resume | null;
  experiences: ResumeExperience[];
  educations: ResumeEducation[];
  certifications: ResumeCertification[];
  completionPercent: number;
}

export const fetchResumeStrengths = () =>
  apiFetch<{ success: boolean; strengths: ResumeStrength[] }>('/api/v1/jobs/resume/strengths').then((r) => r.strengths);

export const fetchMyResume = () =>
  apiFetch<{ success: boolean } & ResumeDetail>('/api/v1/jobs/resume').then((r) => ({
    resume: r.resume,
    experiences: r.experiences,
    educations: r.educations,
    certifications: r.certifications,
    completionPercent: r.completionPercent,
  }));

export const updateResumeProfile = (selfIntro: string | undefined, strengths: string[], additionalInfo: string | undefined) =>
  apiFetch<{ success: boolean; resume: Resume }>('/api/v1/jobs/resume', {
    method: 'PUT',
    body: JSON.stringify({ selfIntro, strengths, additionalInfo }),
  }).then((r) => r.resume);

export const addResumeExperience = (company: string, role: string, period: string, description?: string) =>
  apiFetch<{ success: boolean; experience: ResumeExperience }>('/api/v1/jobs/resume/experience', {
    method: 'POST',
    body: JSON.stringify({ company, role, period, description }),
  }).then((r) => r.experience);

export const removeResumeExperience = (experienceId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/jobs/resume/experience/${experienceId}`, { method: 'DELETE' });

export const addResumeEducation = (school: string, degree?: string, major?: string) =>
  apiFetch<{ success: boolean; education: ResumeEducation }>('/api/v1/jobs/resume/education', {
    method: 'POST',
    body: JSON.stringify({ school, degree, major }),
  }).then((r) => r.education);

export const removeResumeEducation = (educationId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/jobs/resume/education/${educationId}`, { method: 'DELETE' });

export const addResumeCertification = (name: string, issuedDate?: string) =>
  apiFetch<{ success: boolean; certification: ResumeCertification }>('/api/v1/jobs/resume/certification', {
    method: 'POST',
    body: JSON.stringify({ name, issuedDate }),
  }).then((r) => r.certification);

export const removeResumeCertification = (certificationId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/jobs/resume/certification/${certificationId}`, { method: 'DELETE' });
