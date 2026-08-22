// itundaface: security/trust glyphs (2026-08-22, direct user follow-up: "keep
// them high quality and premium like tossface but don't forget itundaface
// identity and keep going until we have complete itundaface like tossface").
// Continues the same real itundaface set -- this is the 🔒 lock group found in
// the repo-wide emoji audit: device step-up verification (i18n, 3 locales),
// escrow payment-held messaging, "Pay via itunda" CTA, and card-frozen status.
//
// Unlike the reaction/place/gift glyphs (which sit inside a colored circular
// badge), this renders inline in running text of varying context (a CTA
// button, a status line, a heading) with no badge -- so it stays a single
// bold silhouette, no background shape, matching how HeartFilled/HeartOutline
// already render inline elsewhere in itundaface. Color is itunda's own real
// indigo brand family (matching PlaceBank/PlaceAtm/PlaceItundaAgent's same
// choice for itunda's own core financial-trust domain), fixed hex per
// itundaface's "one palette, both themes" rule -- not currentColor, so the
// glyph stays itundaface's own identity rather than blending into whatever
// text color surrounds it.

import type { SVGProps } from 'react';

type SecurityIconProps = SVGProps<SVGSVGElement> & { size?: number };

export function LockGlyph({ size = 16, ...rest }: SecurityIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M8,10 V7.5 C8,4.5 9.8,2.5 12,2.5 C14.2,2.5 16,4.5 16,7.5 V10" fill="none" stroke="#483eb6" strokeWidth={2.4} strokeLinecap="round" />
      <rect x="5.5" y="10" width="13" height="11.5" rx="3" fill="#483eb6" />
      <circle cx="12" cy="14.8" r="1.6" fill="#c0c6ff" />
      <rect x="11.1" y="15.6" width="1.8" height="3" rx="0.9" fill="#c0c6ff" />
    </svg>
  );
}
