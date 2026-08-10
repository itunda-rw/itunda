import { apiFetch } from './api';

// Real, minimal product-analytics client (2026-08-10) -- see the "itunda: the wedge,
// not the mirror" strategy memo's recommendation (ii) and AnalyticsEvent.kt's own
// backend doc comment for the full account. Deliberately fire-and-forget: a failed
// analytics call must never surface to the user or block the real UI action it's
// attached to, same reasoning DiscoverSection's own best-effort fetch already
// establishes. Event names match the backend's own closed vocabulary
// (AnalyticsController.KNOWN_EVENTS) -- adding a new one needs a matching backend
// change, on purpose.
export function recordEvent(eventName: 'home_view' | 'coop_rail_tap', metadata?: string) {
  apiFetch('/api/v1/analytics/events', {
    method: 'POST',
    body: JSON.stringify({ eventName, platform: 'web', metadata }),
  }).catch(() => {
    // Best-effort, see doc comment above.
  });
}
