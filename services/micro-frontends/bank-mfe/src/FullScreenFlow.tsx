import type { ReactNode } from 'react';

// Real fix (2026-08-26): extracted out of BankDashboard.tsx so other files
// (ForeignCurrencyView.tsx and beyond) can reuse it without a circular import back
// into BankDashboard.tsx, which itself imports FROM those files.
//
// Real Toss FixedBottomCTA pattern -- see tossmini-docs.toss.im/tds-mobile/components/
// BottomCTA/fixed-bottom-cta's own real doc: a CTA button pinned to the screen bottom
// while the rest of the screen scrolls underneath, supporting a single button or a
// real two-button (Cancel/Confirm) layout. This genuinely only makes sense for a
// dedicated full-screen flow (TDS's own real pattern assumes one) -- itunda's prior
// convention for TransferFlow/CreateGoalForm/etc. was an inline card that stayed
// embedded in a longer scrolling page, which is exactly what a fixed-bottom button
// would fight against -- it would float over unrelated real content below the active
// card. So this wrapper also takes the flow full-screen when active, replacing that
// prior convention specifically for genuinely multi-step processes (2026-08-19, direct
// user request to build this after the "One Thing per One Page" pass) -- itunda's
// simpler single-panel toggles elsewhere are a different, legitimate pattern and are
// NOT being changed by this.
//
// The pin is content-length-independent, not just a long-form affordance (2026-08-26,
// direct user follow-up against real Toss Bank product-intro screenshots): "toss
// always keep those confirm buttons in bottom in many cases regardless of contents, so
// when contents are few it keep designs look good and when contents are many users
// needs to scroll to see other contents they still see that buttons to click anytime
// they make up their mind." See docs/UI_UX_GUIDELINES.md rule 1.
export function FullScreenFlow({ children, bottomCTA }: { children: ReactNode; bottomCTA?: ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto', padding: '20px' }}>{children}</div>
      {bottomCTA && (
        <div style={{ padding: '12px 20px', paddingBottom: 'max(12px, env(safe-area-inset-bottom))', borderTop: '1px solid var(--itunda-grey-200)', backgroundColor: 'var(--itunda-white)' }}>
          {bottomCTA}
        </div>
      )}
    </div>
  );
}
