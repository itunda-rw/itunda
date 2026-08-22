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
//
// Refined 2026-08-22, direct user follow-up ("don't make it half-assed, take it
// seriously, make premium graphics like toss that will help itunda interactions
// as well"): a second, more considered art pass -- an upper-left linear-gradient
// rim-light along each silhouette's edge (not just the specular blob), a soft
// ground shadow ellipse beneath each 3D glyph for real lift/presence, refined
// hand/face proportions, secondary shading (a faint inner mouth-line, a small
// gloss highlight on the tear drop). "Help interactions," grounded in Toss's own
// real, published motion strategy (toss.im/tossfeed/article/why-motion-in-finance,
// fetched directly): "symbolic animated icons" is a named real pattern there, and
// their own Tossface homepage uses "mouse-over and click-triggered emoji
// animations." Applied here via BankDashboard.tsx's MessageReactions -- real
// framer-motion (already itunda's established motion library, see useCountUp)
// spring-in/out on reaction badges appearing/disappearing, whileHover/whileTap
// scale feedback on every tappable glyph -- not decoration, each one confirms an
// action registered, the same real purpose Toss's own motion strategy names.

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
    <radialGradient id="itdf-skin" cx="32%" cy="22%" r="85%">
      <stop offset="0%" stopColor="#ffe4b3" />
      <stop offset="50%" stopColor="#ffcf87" />
      <stop offset="100%" stopColor="#cf8f3e" />
    </radialGradient>
    <radialGradient id="itdf-cuff" cx="32%" cy="20%" r="90%">
      <stop offset="0%" stopColor="#f6c988" />
      <stop offset="55%" stopColor="#e0a655" />
      <stop offset="100%" stopColor="#9c6423" />
    </radialGradient>
    <radialGradient id="itdf-heart" cx="32%" cy="20%" r="90%">
      <stop offset="0%" stopColor="#ff9fb0" />
      <stop offset="42%" stopColor="#ef4a63" />
      <stop offset="100%" stopColor="#a91733" />
    </radialGradient>
    <radialGradient id="itdf-face" cx="32%" cy="20%" r="90%">
      <stop offset="0%" stopColor="#ffe89e" />
      <stop offset="50%" stopColor="#ffcc4d" />
      <stop offset="100%" stopColor="#d98e0a" />
    </radialGradient>
    <radialGradient id="itdf-tear" cx="32%" cy="16%" r="90%">
      <stop offset="0%" stopColor="#d8f4ff" />
      <stop offset="55%" stopColor="#7cd9ff" />
      <stop offset="100%" stopColor="#1c8dc4" />
    </radialGradient>
    <radialGradient id="itdf-hl" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stopColor="#ffffff" stopOpacity="0.6" />
      <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
    </radialGradient>
    <linearGradient id="itdf-rim" x1="20%" y1="0%" x2="70%" y2="60%">
      <stop offset="0%" stopColor="#ffffff" stopOpacity="0.85" />
      <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
    </linearGradient>
    <radialGradient id="itdf-floor" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stopColor="#000000" stopOpacity="0.28" />
      <stop offset="100%" stopColor="#000000" stopOpacity="0" />
    </radialGradient>
    <filter id="itdf-ds" x="-60%" y="-60%" width="220%" height="220%">
      <feDropShadow dx="0" dy="3.5" stdDeviation="3.4" floodColor="#000000" floodOpacity="0.4" />
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
      <ellipse cx="40" cy="76" rx="26" ry="5" fill="url(#itdf-floor)" />
      <g filter="url(#itdf-ds)">
        <path d="M30,33 V72 C30,74.4 28.1,76.4 25.7,76.4 H16.3 C13.9,76.4 12,74.4 12,72 V38 C12,35.6 13.9,33.6 16.3,33.6 H25.7 Z" fill="url(#itdf-cuff)" />
        <path d="M34,33.6 H55 C59.4,33.6 63,37.2 63,41.6 C63,42.9 62.7,44.1 62.1,45.2 C64.6,46.4 66.4,48.9 66.4,51.8 C66.4,54.1 65.3,56.1 63.6,57.4 C64.7,58.8 65.4,60.6 65.4,62.5 C65.4,65.3 63.9,67.7 61.7,68.9 C61.9,69.7 62.1,70.5 62.1,71.4 C62.1,75.3 58.9,78.4 55,78.4 H37 C34.6,78.4 32.7,76.4 32.7,74 V34.9 Z" fill="url(#itdf-skin)" />
        <path d="M34,33.6 L39,16.5 C40,13.3 43,11.1 46.3,11.1 C47.6,11.1 48.6,12.3 48.4,13.6 L46.2,29.4" stroke="url(#itdf-skin)" strokeWidth={6.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        <ellipse cx="31" cy="22" rx="9" ry="6" fill="url(#itdf-hl)" />
      </g>
    </FaceBase>
  );
}

export function ReactionHeart3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <ellipse cx="40" cy="76" rx="26" ry="5" fill="url(#itdf-floor)" />
      <g filter="url(#itdf-ds)">
        <path d="M40,74.4 C40,74.4 7.4,52.4 7.4,28.8 C7.4,16.4 16.9,8.4 26.8,8.4 C34.1,8.4 38.7,13 40,17.2 C41.3,13 45.9,8.4 53.2,8.4 C63.1,8.4 72.6,16.4 72.6,28.8 C72.6,52.4 40,74.4 40,74.4 Z" fill="url(#itdf-heart)" />
        <path d="M24,13 C29,10.5 35,12.5 38,17" stroke="url(#itdf-rim)" strokeWidth={3} strokeLinecap="round" fill="none" />
        <ellipse cx="26" cy="24" rx="11" ry="7.5" fill="url(#itdf-hl)" />
      </g>
    </FaceBase>
  );
}

export function ReactionLaughing3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <ellipse cx="40" cy="76" rx="26" ry="5" fill="url(#itdf-floor)" />
      <g filter="url(#itdf-ds)">
        <circle cx="40" cy="40" r="34.5" fill="url(#itdf-face)" />
        <path d="M11,26 C16,15 30,12 38,18" stroke="url(#itdf-rim)" strokeWidth={3.4} strokeLinecap="round" fill="none" opacity={0.7} />
        <ellipse cx="27" cy="23" rx="13" ry="8.5" fill="url(#itdf-hl)" />
        <path d="M17.5,31.5 C20.7,25.3 27,25.3 30.2,31.5" stroke="#7a4d00" strokeWidth={4.6} strokeLinecap="round" fill="none" />
        <path d="M49.8,31.5 C53,25.3 59.3,25.3 62.5,31.5" stroke="#7a4d00" strokeWidth={4.6} strokeLinecap="round" fill="none" />
        <path d="M15,48 C15,48 23.5,67 40,67 C56.5,67 65,48 65,48 C65,48 56.5,54.5 40,54.5 C23.5,54.5 15,48 15,48 Z" fill="#5c3d15" />
        <path d="M18,50 C18,50 25,60 40,60 C55,60 62,50 62,50" stroke="#3d2a10" strokeWidth={1.6} fill="none" opacity={0.4} />
        <path d="M22.5,53 C22.5,53 25.5,61 24.3,67.5" stroke="url(#itdf-tear)" strokeWidth={4.6} strokeLinecap="round" fill="none" />
        <ellipse cx="23.5" cy="60" rx="2.6" ry="3.4" fill="#ffffff" opacity={0.55} />
      </g>
    </FaceBase>
  );
}

export function ReactionWow3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <ellipse cx="40" cy="76" rx="26" ry="5" fill="url(#itdf-floor)" />
      <g filter="url(#itdf-ds)">
        <circle cx="40" cy="40" r="34.5" fill="url(#itdf-face)" />
        <path d="M11,26 C16,15 30,12 38,18" stroke="url(#itdf-rim)" strokeWidth={3.4} strokeLinecap="round" fill="none" opacity={0.7} />
        <ellipse cx="27" cy="23" rx="13" ry="8.5" fill="url(#itdf-hl)" />
        <circle cx="26" cy="33.5" r="5.2" fill="#5c3d15" />
        <circle cx="54" cy="33.5" r="5.2" fill="#5c3d15" />
        <circle cx="24.3" cy="31.8" r="1.4" fill="#ffffff" opacity={0.75} />
        <circle cx="52.3" cy="31.8" r="1.4" fill="#ffffff" opacity={0.75} />
        <ellipse cx="40" cy="56.5" rx="9.4" ry="11.5" fill="#5c3d15" />
        <ellipse cx="40" cy="59" rx="5.6" ry="6.5" fill="#3d2a10" opacity={0.5} />
      </g>
    </FaceBase>
  );
}

export function ReactionSad3D(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE_3D_DEFS}
      <ellipse cx="40" cy="76" rx="26" ry="5" fill="url(#itdf-floor)" />
      <g filter="url(#itdf-ds)">
        <circle cx="40" cy="40" r="34.5" fill="url(#itdf-face)" />
        <path d="M11,26 C16,15 30,12 38,18" stroke="url(#itdf-rim)" strokeWidth={3.4} strokeLinecap="round" fill="none" opacity={0.7} />
        <ellipse cx="27" cy="23" rx="13" ry="8.5" fill="url(#itdf-hl)" />
        <path d="M19.5,31.5 C22.7,36 29,36 32.2,31.5" stroke="#5c3d15" strokeWidth={4.6} strokeLinecap="round" fill="none" />
        <path d="M47.8,31.5 C51,36 57.3,36 60.5,31.5" stroke="#5c3d15" strokeWidth={4.6} strokeLinecap="round" fill="none" />
        <path d="M25.5,63 C30,54.5 50,54.5 54.5,63" stroke="#5c3d15" strokeWidth={4.6} strokeLinecap="round" fill="none" />
        <path d="M48,41 C51.5,45.5 55,50.5 55,55.5 C55,60 51.5,63.4 48,63.4 C44.5,63.4 41,60 41,55.5 C41,50.5 44.5,45.5 48,41 Z" fill="url(#itdf-tear)" />
        <ellipse cx="45.6" cy="49" rx="1.8" ry="2.6" fill="#ffffff" opacity={0.6} />
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

// Per-glyph one-shot hover animation class (see index.css's own itdf-anim-* rules
// and their doc comment for the real Toss/Kakao sourcing). Keyed by emoji, not
// shape name, so ReactionGlyph's caller never needs to know which glyph maps to
// which animation.
const ITUNDAFACE_ANIM_CLASS: Record<string, string> = {
  '👍': 'itdf-anim-thumbsup',
  '❤️': 'itdf-anim-heart',
  '😂': 'itdf-anim-laughing',
  '😮': 'itdf-anim-wow',
  '😢': 'itdf-anim-sad',
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
 * their small inline size, same as TossFace's own real base set does.
 *
 * `animated` plays the glyph's one-shot hover animation (real Toss/Kakao-sourced,
 * see index.css) -- the caller flips this true for the moment its own hover state
 * says so. The `key` swap (rest/anim) forces a real DOM remount so the CSS
 * animation restarts on every hover-in, not just the first -- a plain className
 * toggle back to the same value wouldn't replay a `forwards`-filled animation. */
export function ReactionGlyph({ emoji, size = 18, variant = 'flat', animated = false }: { emoji: string; size?: number; variant?: 'flat' | '3d'; animated?: boolean }) {
  const Icon = (variant === '3d' ? ITUNDAFACE_REACTIONS_3D : ITUNDAFACE_REACTIONS)[emoji];
  if (!Icon) return <span>{emoji}</span>;
  const animClass = animated ? ITUNDAFACE_ANIM_CLASS[emoji] : undefined;
  return <Icon key={animated ? 'anim' : 'rest'} size={size} className={animClass} />;
}
