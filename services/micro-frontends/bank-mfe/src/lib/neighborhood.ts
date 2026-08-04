import { apiFetch } from './api';

// Real hyperlocal neighborhood (rw.itunda.core.domain.User.neighborhood, 2026-07-20) --
// first UI touchpoint for this backend feature across any client (a real, honest gap
// this row's own matrix text should have named and didn't -- found live while doing a
// UX-copy pass on this session's newest features). See AuthService.setNeighborhood's own
// doc comment for the full backend account: a real coordinate in, reverse-geocoded
// through itunda's own self-hosted Nominatim into a real neighborhood/sector name, never
// a self-declared free-text field.

export const fetchProfile = () =>
  apiFetch<{
    success: boolean;
    user: {
      neighborhood: string | null;
      // Real second neighborhood (2026-08-04) -- see AuthService.setSecondNeighborhood's
      // own doc comment. Same real-coordinate, reverse-geocoded-server-side rule as
      // `neighborhood`.
      secondNeighborhood: string | null;
      neighborhoodVerificationCount: number | null;
      birthDate: string | null;
      email: string | null;
      emailVerified: boolean;
      phoneVerified: boolean;
      profilePhotoUrl: string | null;
    };
  }>('/api/v1/auth/profile').then((r) => r.user);

// Real profile photo (URL, not a binary upload -- see backend UpdateProfilePhotoRequest's
// own doc comment) -- also the real, buildable half of Rewards' task_profile. Found
// 2026-07-29 via a full-backend-endpoint sweep: real, working endpoint with zero client
// anywhere, and PublicUser.profilePhotoUrl wasn't even carried by any client's own User type.
export const updateProfilePhoto = (profilePhotoUrl: string) =>
  apiFetch<{ success: boolean; user: { profilePhotoUrl: string | null } }>('/api/v1/auth/profile/photo', {
    method: 'PUT',
    body: JSON.stringify({ profilePhotoUrl }),
  }).then((r) => r.user);

export const setNeighborhood = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; user: { neighborhood: string | null } }>('/api/v1/auth/profile/neighborhood', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.user);

// Real second neighborhood (2026-08-04) -- mirrors setNeighborhood exactly; separate
// add/remove endpoints since the second neighborhood is optional and independently
// clearable. See Android SuperAppTabs.kt's HoodTab and iOS HoodScreen.swift's
// NeighborhoodSwitcherOverlay, which this ports.
export const setSecondNeighborhood = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; user: { secondNeighborhood: string | null } }>('/api/v1/auth/profile/second-neighborhood', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.user);

export const clearSecondNeighborhood = () =>
  apiFetch<{ success: boolean; user: { secondNeighborhood: string | null } }>('/api/v1/auth/profile/second-neighborhood', {
    method: 'DELETE',
  }).then((r) => r.user);

// Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
// rw.itunda.auth.AuthService.setBirthDate's own doc comment. birthDate is an
// ISO-8601 date string ("YYYY-MM-DD").
export const setBirthDate = (birthDate: string) =>
  apiFetch<{ success: boolean; user: { birthDate: string | null } }>('/api/v1/auth/profile/birth-date', {
    method: 'POST',
    body: JSON.stringify({ birthDate }),
  }).then((r) => r.user);
