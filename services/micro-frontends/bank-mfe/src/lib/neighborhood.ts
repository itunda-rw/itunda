import { apiFetch } from './api';

// Real hyperlocal neighborhood (rw.itunda.core.domain.User.neighborhood, 2026-07-20) --
// first UI touchpoint for this backend feature across any client (a real, honest gap
// this row's own matrix text should have named and didn't -- found live while doing a
// UX-copy pass on this session's newest features). See AuthService.setNeighborhood's own
// doc comment for the full backend account: a real coordinate in, reverse-geocoded
// through itunda's own self-hosted Nominatim into a real neighborhood/sector name, never
// a self-declared free-text field.

export const fetchProfile = () =>
  apiFetch<{ success: boolean; user: { neighborhood: string | null; birthDate: string | null; email: string | null; emailVerified: boolean; phoneVerified: boolean } }>(
    '/api/v1/auth/profile',
  ).then((r) => r.user);

export const setNeighborhood = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; user: { neighborhood: string | null } }>('/api/v1/auth/profile/neighborhood', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.user);

// Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
// rw.itunda.auth.AuthService.setBirthDate's own doc comment. birthDate is an
// ISO-8601 date string ("YYYY-MM-DD").
export const setBirthDate = (birthDate: string) =>
  apiFetch<{ success: boolean; user: { birthDate: string | null } }>('/api/v1/auth/profile/birth-date', {
    method: 'POST',
    body: JSON.stringify({ birthDate }),
  }).then((r) => r.user);
