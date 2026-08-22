// itundaface: itunda's own reaction glyph set (2026-08-22, direct user request
// following a deep-research pass on Toss's real TossFace: "let's create our own
// itundaface"). Real research (docs/DESIGN_REFERENCES.md /
// project_itunda_own_icons_graphics.md): TossFace (github.com/toss/tossface) is
// real, open, 3,600 glyphs covering the full Unicode v14 emoji set, distributed as
// a 50MB+ compiled color-emoji font, under a Toss-branded license that bars
// embedding it as another product's own asset. Its real, stated design rules
// (toss.im/tossface): simplest form from basic shapes, minimal detail for small-size
// clarity; every glyph the same visual size/weight; ONE color palette that reads on
// both light and dark backgrounds; directional glyphs consistently face right;
// perspective objects at a standardized 45deg; consistent 3D viewpoint height. Also
// real: Toss's own early misstep with this project was changing what specific
// emoji actually MEANT (culturally-specific substitutions), which real users pushed
// back on and Toss reverted within weeks -- the lesson applied here is redesign the
// STYLE, never the semantic meaning a glyph already carries (a heart still means
// "love," a thumbs-up still means "approve").
//
// itundaface v1 is NOT 3,600 glyphs -- a real, honest scope grounded in itunda's
// own actual current emoji usage (audited across web/Android/iOS before starting,
// not guessed): the 5 real chat quick-reactions. A full ~35-40-glyph roadmap (map
// POI category markers, gift types, like/heart toggles, split-bill icons) is
// tracked in project_itunda_own_icons_graphics.md as real, scoped next slices --
// building all of it in one pass wasn't attempted, matching this session's
// established "small well-verified slice, not everything at once" discipline.
//
// Construction, adapted from TossFace's own real rules above (not its exact
// artwork): flat fill (no gradient -- legible at the ~14-18px this actually
// renders at in a reaction badge), one shared face base (a single yellow circle,
// keeping the universally-expected "face" color rather than reinventing it, per
// the meaning-preservation lesson above), consistent stroke weight on linework,
// verified against both a dark and a light chat-bubble background before shipping.
//
// Real follow-up (2026-08-22, direct user correction: "toss face uses 3d
// graphics"): verified directly (toss.tech/article/22205, the real "making of
// TossFace" article) rather than assumed -- the correction is real, but with an
// important nuance the article states explicitly: the base 3,600-glyph set is
// genuinely 2D/flat ("플랫/아이코닉," built for small inline use, "형태에 대한 고민이
// 폰트화 과정에서 이미 끝났기 때문" -- form decisions finalized during the 2D phase). 3D
// is a SEPARATE, additional variant modeled in Cinema 4D from those same 2D
// outlines, used specifically for "key visuals" needing "volume and material
// expression" -- the article's own real example is Toss Bank's group-account
// launch key visual, several 3D emoji composed together for a promotional/hero
// graphic, not everyday inline UI. So: the flat glyphs above are correct as they
// are for the tiny ~14-18px reaction badge (matches TossFace's own real choice at
// that same scale); a *3D* variant belongs at a genuinely prominent size, which
// here is the quick-react picker itself (the moment of choosing, shown at ~32px,
// not a passing glance) -- mirrors the small=flat/prominent=3D split Toss's own
// real practice draws, applied to the one place in this feature where it actually
// fits, not forced onto the small inline badges too.
//
// The 3D look itself is achieved the same way itunda's own app-icon mark already
// does it (project_itunda_brand_identity.md) -- a radial gradient per shape
// (light top-left highlight through a mid-tone to a darker shaded edge, faking a
// rounded volume), a soft white specular highlight ellipse, and a drop-shadow
// filter for lift -- not an actual 3D render pipeline (no Cinema 4D equivalent is
// available here), but the same real, established technique this codebase already
// uses and has already visually verified once today.

import type { SVGProps } from 'react';

type FaceIconProps = SVGProps<SVGSVGElement> & { size?: number };

function FaceBase({ size = 24, children, ...rest }: FaceIconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 80 80" {...rest}>
      {children}
    </svg>
  );
}

const FACE_3D_DEFS = (
  <defs>
    <radialGradient id="itdf-skin" cx="35%" cy="28%" r="75%">
      <stop offset="0%" stopColor="#ffe0ac" />
      <stop offset="55%" stopColor="#ffcf87" />
      <stop offset="100%" stopColor="#d99a45" />
    </radialGradient>
    <radialGradient id="itdf-cuff" cx="35%" cy="25%" r="80%">
      <stop offset="0%" stopColor="#f3c07e" />
      <stop offset="60%" stopColor="#e0a655" />
      <stop offset="100%" stopColor="#a86e2a" />
    </radialGradient>
    <radialGradient id="itdf-heart" cx="35%" cy="25%" r="80%">
      <stop offset="0%" stopColor="#ff8ba0" />
      <stop offset="45%" stopColor="#ef4a63" />
      <stop offset="100%" stopColor="#b81e3a" />
    </radialGradient>
    <radialGradient id="itdf-face" cx="35%" cy="25%" r="80%">
      <stop offset="0%" stopColor="#ffe28a" />
      <stop offset="55%" stopColor="#ffcc4d" />
      <stop offset="100%" stopColor="#e29c14" />
    </radialGradient>
    <radialGradient id="itdf-tear" cx="35%" cy="20%" r="80%">
      <stop offset="0%" stopColor="#c6f0ff" />
      <stop offset="60%" stopColor="#7cd9ff" />
      <stop offset="100%" stopColor="#2ba0d9" />
    </radialGradient>
    <radialGradient id="itdf-hl" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stopColor="#ffffff" stopOpacity="0.55" />
      <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
    </radialGradient>
    <filter id="itdf-ds" x="-60%" y="-60%" width="220%" height="220%">
      <feDropShadow dx="0" dy="3" stdDeviation="3.2" floodColor="#000000" floodOpacity="0.38" />
    </filter>
  </defs>
);

export function ReactionThumbsUp(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <path d="M28,32 V72 C28,74.2 26.2,76 24,76 H16 C13.8,76 12,74.2 12,72 V38 C12,35.8 13.8,34 16,34 H24 Z" fill="#e0a655" />
      <path d="M32,34 H56 C60,34 63,37.2 63,41.2 C63,42.6 62.6,44 61.9,45.1 C64.3,46.1 66,48.5 66,51.2 C66,53.4 64.9,55.3 63.2,56.6 C64.3,58 65,59.8 65,61.7 C65,64.5 63.4,66.9 61.1,68.1 C61.4,68.9 61.6,69.8 61.6,70.7 C61.6,74.7 58.3,78 54.3,78 H36 C33.8,78 32,76.2 32,74 V34 Z" fill="#ffcf87" />
      <path d="M32,34 L38,16 C39,12.6 42.1,10.3 45.6,10.3 C47,10.3 48,11.5 47.8,12.9 L45.6,28" stroke="#ffcf87" strokeWidth={6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </FaceBase>
  );
}

export function ReactionHeart(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <path d="M40,74 C40,74 8,52.6 8,29.6 C8,17.7 17.1,10 26.6,10 C33.6,10 38,14.4 40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 Z" fill="#ef4a63" />
    </FaceBase>
  );
}

export function ReactionLaughing(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <circle cx="40" cy="40" r="34" fill="#ffcc4d" />
      <path d="M18,32 C21,26 27,26 30,32" stroke="#664500" strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M50,32 C53,26 59,26 62,32" stroke="#664500" strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M16,48 C16,48 24,66 40,66 C56,66 64,48 64,48 C64,48 56,54 40,54 C24,54 16,48 16,48 Z" fill="#66471b" />
      <path d="M23,52 C23,52 26,60 25,66" stroke="#7cd9ff" strokeWidth={4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

export function ReactionWow(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <circle cx="40" cy="40" r="34" fill="#ffcc4d" />
      <circle cx="26" cy="34" r="5" fill="#664500" />
      <circle cx="54" cy="34" r="5" fill="#664500" />
      <ellipse cx="40" cy="56" rx="9" ry="11" fill="#66471b" />
    </FaceBase>
  );
}

export function ReactionSad(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <circle cx="40" cy="40" r="34" fill="#ffcc4d" />
      <path d="M20,32 C23,36 29,36 32,32" stroke="#664500" strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M48,32 C51,36 57,36 60,32" stroke="#664500" strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M26,62 C30,54 50,54 54,62" stroke="#664500" strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M48,40 C51,44 54,49 54,53.6 C54,57.6 51,60.6 48,60.6 C45,60.6 42,57.6 42,53.6 C42,49 45,44 48,40 Z" fill="#5ec2ea" />
    </FaceBase>
  );
}

export function ReactionThumbsUp3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <g filter="url(#itdf-ds)">
        <path d="M28,32 V72 C28,74.2 26.2,76 24,76 H16 C13.8,76 12,74.2 12,72 V38 C12,35.8 13.8,34 16,34 H24 Z" fill="url(#itdf-cuff)" />
        <path d="M32,34 H56 C60,34 63,37.2 63,41.2 C63,42.6 62.6,44 61.9,45.1 C64.3,46.1 66,48.5 66,51.2 C66,53.4 64.9,55.3 63.2,56.6 C64.3,58 65,59.8 65,61.7 C65,64.5 63.4,66.9 61.1,68.1 C61.4,68.9 61.6,69.8 61.6,70.7 C61.6,74.7 58.3,78 54.3,78 H36 C33.8,78 32,76.2 32,74 V34 Z" fill="url(#itdf-skin)" />
        <path d="M32,34 L38,16 C39,12.6 42.1,10.3 45.6,10.3 C47,10.3 48,11.5 47.8,12.9 L45.6,28" stroke="url(#itdf-skin)" strokeWidth={6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        <ellipse cx="30" cy="24" rx="10" ry="7" fill="url(#itdf-hl)" />
      </g>
    </FaceBase>
  );
}

export function ReactionHeart3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <g filter="url(#itdf-ds)">
        <path d="M40,74 C40,74 8,52.6 8,29.6 C8,17.7 17.1,10 26.6,10 C33.6,10 38,14.4 40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 Z" fill="url(#itdf-heart)" />
        <ellipse cx="27" cy="26" rx="12" ry="8" fill="url(#itdf-hl)" />
      </g>
    </FaceBase>
  );
}

export function ReactionLaughing3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <g filter="url(#itdf-ds)">
        <circle cx="40" cy="40" r="34" fill="url(#itdf-face)" />
        <ellipse cx="28" cy="24" rx="14" ry="9" fill="url(#itdf-hl)" />
        <path d="M18,32 C21,26 27,26 30,32" stroke="#7a4d00" strokeWidth={4.4} strokeLinecap="round" fill="none" />
        <path d="M50,32 C53,26 59,26 62,32" stroke="#7a4d00" strokeWidth={4.4} strokeLinecap="round" fill="none" />
        <path d="M16,48 C16,48 24,66 40,66 C56,66 64,48 64,48 C64,48 56,54 40,54 C24,54 16,48 16,48 Z" fill="#5c3d15" />
        <path d="M23,52 C23,52 26,60 25,66" stroke="url(#itdf-tear)" strokeWidth={4.4} strokeLinecap="round" fill="none" />
      </g>
    </FaceBase>
  );
}

export function ReactionWow3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <g filter="url(#itdf-ds)">
        <circle cx="40" cy="40" r="34" fill="url(#itdf-face)" />
        <ellipse cx="28" cy="24" rx="14" ry="9" fill="url(#itdf-hl)" />
        <circle cx="26" cy="34" r="5" fill="#5c3d15" />
        <circle cx="54" cy="34" r="5" fill="#5c3d15" />
        <ellipse cx="40" cy="56" rx="9" ry="11" fill="#5c3d15" />
      </g>
    </FaceBase>
  );
}

export function ReactionSad3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <g filter="url(#itdf-ds)">
        <circle cx="40" cy="40" r="34" fill="url(#itdf-face)" />
        <ellipse cx="28" cy="24" rx="14" ry="9" fill="url(#itdf-hl)" />
        <path d="M20,32 C23,36 29,36 32,32" stroke="#5c3d15" strokeWidth={4.4} strokeLinecap="round" fill="none" />
        <path d="M48,32 C51,36 57,36 60,32" stroke="#5c3d15" strokeWidth={4.4} strokeLinecap="round" fill="none" />
        <path d="M26,62 C30,54 50,54 54,62" stroke="#5c3d15" strokeWidth={4.4} strokeLinecap="round" fill="none" />
        <path d="M48,40 C51,44 54,49 54,53.6 C54,57.6 51,60.6 48,60.6 C45,60.6 42,57.6 42,53.6 C42,49 45,44 48,40 Z" fill="url(#itdf-tear)" />
      </g>
    </FaceBase>
  );
}

// Maps the same real Unicode identifiers already persisted server-side
// (ReactionGroup.emoji, ChatMessage reaction rows) to itundaface's own glyph --
// display-layer only, the underlying stored/toggled value is unchanged so existing
// reaction data and the toggle API contract keep working exactly as before.
const ITUNDAFACE_REACTIONS: Record<string, (props: FaceIconProps) => React.ReactElement> = {
  '👍': ReactionThumbsUp,
  '❤️': ReactionHeart,
  '😂': ReactionLaughing,
  '😮': ReactionWow,
  '😢': ReactionSad,
};

const ITUNDAFACE_REACTIONS_3D: Record<string, (props: FaceIconProps) => React.ReactElement> = {
  '👍': ReactionThumbsUp3D,
  '❤️': ReactionHeart3D,
  '😂': ReactionLaughing3D,
  '😮': ReactionWow3D,
  '😢': ReactionSad3D,
};

/** Renders itundaface's own glyph for a known reaction identifier, falling back to
 * the raw Unicode character for anything outside the 5 real quick-reactions (data
 * defensiveness only -- QUICK_REACTIONS is the only real source of new reactions
 * today, so this fallback path isn't expected to render in practice).
 *
 * `variant="3d"` renders the volumetric treatment -- reserved for a genuinely
 * prominent placement (the quick-react picker, ~32px, the moment of deliberately
 * choosing a reaction), matching TossFace's own real small=flat/prominent=3D split
 * (see this file's header comment). Message-bubble reaction badges stay flat at
 * their small inline size, same as TossFace's own real base set does. */
export function ReactionGlyph({ emoji, size = 18, variant = 'flat' }: { emoji: string; size?: number; variant?: 'flat' | '3d' }) {
  const Icon = (variant === '3d' ? ITUNDAFACE_REACTIONS_3D : ITUNDAFACE_REACTIONS)[emoji];
  return Icon ? <Icon size={size} /> : <span>{emoji}</span>;
}
