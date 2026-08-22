// itundaface: like/wishlist heart-toggle glyphs (2026-08-22, direct user
// follow-up: "keep them high quality and premium like tossface but don't
// forget itundaface identity and keep going until we have complete itundaface
// like tossface"). Continues the same real itundaface set (ItundaFace.tsx for
// chat reactions, ItundaFacePlaces.tsx for map categories, ItundaFaceGifts.tsx
// for gift themes) -- this is the real ♡/♥/❤️/🤍 heart-toggle group found in
// the repo-wide emoji audit: one shared WishlistButton component (Marketplace/
// Jobs/RealEstate/Eats-favorites/Commerce, 7 real call sites) plus a standalone
// Hood post-like button and 4 read-only "favorite count" displays.
//
// Deliberately NOT a new heart design -- this is the SAME real shared-seam
// heart silhouette as ItundaFace.tsx's ReactionHeart (identical path data,
// identical #ef4a63/#c72e4c two-facet palette), because "itundaface's own
// identity" means the SAME heart shows up everywhere a heart means the same
// thing in itunda, not a redrawn one per feature. The outline (unfavorited)
// state traces the exact same silhouette as a stroke, so toggling
// favorited/unfavorited reads as one glyph filling in, not two unrelated
// icons swapping.

import type { SVGProps } from 'react';

type HeartIconProps = SVGProps<SVGSVGElement> & { size?: number };

function HeartBase({ size = 24, children, ...rest }: HeartIconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 80 80" {...rest}>
      {children}
    </svg>
  );
}

/** Filled heart -- identical construction to ItundaFace.tsx's ReactionHeart
 * (shared-seam two-facet: lit right / shaded left). Used for the "favorited" /
 * "liked" state, and for read-only favorite-count displays. */
export function HeartFilled(props: HeartIconProps) {
  return (
    <HeartBase {...props}>
      <path d="M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 L40,18.4 Z" fill="#ef4a63" />
      <path d="M40,18.4 C38,14.4 33.6,10 26.6,10 C17.1,10 8,17.7 8,29.6 C8,52.6 40,74 40,74 L40,18.4 Z" fill="#c72e4c" />
    </HeartBase>
  );
}

/** Outline heart -- the exact same silhouette as HeartFilled, traced as a
 * single stroke with no internal seam and no fill. Used for the
 * "unfavorited" / "not liked" state. */
export function HeartOutline(props: HeartIconProps) {
  return (
    <HeartBase {...props}>
      <path
        d="M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 C40,74 8,52.6 8,29.6 C8,17.7 17.1,10 26.6,10 C33.6,10 38,14.4 40,18.4 Z"
        fill="none"
        stroke="#9099a8"
        strokeWidth={5}
        strokeLinejoin="round"
      />
    </HeartBase>
  );
}

/** Renders itundaface's own heart toggle for a given favorited state --
 * drop-in swap for a plain '♥'/'♡' or '❤️'/'🤍' pair. */
export function WishlistHeart({ favorited, size = 18 }: { favorited: boolean; size?: number }) {
  return favorited ? <HeartFilled size={size} /> : <HeartOutline size={size} />;
}
