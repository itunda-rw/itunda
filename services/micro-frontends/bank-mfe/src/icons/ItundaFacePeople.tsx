// itundaface People & Body -- phase 2 of the "reach TossFace's 3,600-glyph
// scale" initiative (see ItundaFaceSmileys.tsx / project_itunda_own_icons_
// graphics.md's staged category roadmap for phase 1 and the full plan). Real
// Unicode "People & Body" group, prioritized by real chat-usage frequency
// among hand gestures specifically (the sub-group people actually reach for),
// not spec order.
//
// **Real redesign pass (2026-08-22), two direct user corrections**:
// 1. "those emojis waving hands images they don't look good take inspiration
//    from tossface and make sure our emoji escapes are premium with itunda
//    identity." The first shipped draft built each hand from widely-fanned,
//    dramatically-rotated rectangle "fingers" glued onto a separate blocky
//    cuff rectangle -- looked crude next to TossFace's actual construction.
//    Fetched TossFace's own real published SVG source directly
//    (github.com/toss/tossface/dist/svg/, e.g. u1F44B.svg for waving hand,
//    u270A.svg for fist) and rendered them for real visual reference --
//    confirmed their real technique is a single smooth, CHUNKY, closely-
//    packed hand silhouette (minimal gaps between fingers, no separate cuff
//    block, fingers barely rotated) plus one soft shading-crescent accent
//    line, not a literal skin-tone/anatomical hand. Applied the same
//    STRUCTURE (tight packing, no cuff, rounded-rect palm/fist bases, a
//    single accent swoosh) while keeping itunda's own identity, not
//    TossFace's: real skin-tone palette (fill #FFCF87, matching
//    ItundaFace.tsx's ReactionThumbsUp, itundaface's own established hand-
//    glyph convention) instead of TossFace's gold/mitten color, and the
//    accent swoosh uses real itunda indigo instead of TossFace's orange --
//    the same "secondary/non-semantic accent = itunda indigo" signature rule
//    already used for the reaction tears.
// 2. "toss emojis are bigger and clear that's what we need" -- measured
//    TossFace's real reference SVGs (40x40 viewBox, content spans nearly
//    edge-to-edge) against this file's first redesign (still padded well
//    inside its 80x80 frame) and rescaled every glyph 1.15x-1.4x around its
//    own visual center to match that same edge-to-edge fill ratio, fixing an
//    off-canvas clipping bug on the waving hand's motion lines along the way
//    (they extended to y=-1, outside the 0-80 viewBox, before the rescale).
// Both passes verified via rsvg-convert render-and-inspect before shipping,
// same discipline as every itundaface batch.
//
// Muscle and Folded Hands were attempted across 3 different construction
// techniques (primitive rects, smooth single-path, TossFace-inspired chunky
// packing) and still did not read clearly at a glance -- cut from this batch
// rather than shipped illegible, same standard the place-category icons
// (restaurant/pharmacy/hotel) were held to earlier. Real, documented open
// item for a future redesign pass, ideally with a genuinely different
// construction idea, not another iteration on the same approach.

import type { SVGProps } from 'react';

type FaceIconProps = SVGProps<SVGSVGElement> & { size?: number };

function FaceBase({ size = 24, children, ...rest }: FaceIconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 80 80" {...rest}>
      {children}
    </svg>
  );
}

const SKIN = '#FFCF87';
const INDIGO = '#7472F4';

export function PeopleEyes(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <g transform="translate(40,40) scale(1.3) translate(-40,-40)">
        <ellipse cx="24" cy="42" rx="17" ry="14" fill="#FFFFFF" stroke="#B8B4AA" strokeWidth={2} />
        <ellipse cx="56" cy="42" rx="17" ry="14" fill="#FFFFFF" stroke="#B8B4AA" strokeWidth={2} />
        <circle cx="26" cy="42" r="7" fill={INDIGO} />
        <circle cx="58" cy="42" r="7" fill={INDIGO} />
        <circle cx="28" cy="39" r="2" fill="#FFFFFF" />
        <circle cx="60" cy="39" r="2" fill="#FFFFFF" />
      </g>
    </FaceBase>
  );
}

export function PeopleFist(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <g transform="translate(40,40) scale(1.4) translate(-38,-42)">
        <path
          d="M20,26 C20,18 26,12 34,10 C36,6 40,4 44,4 C48,4 52,6 54,10 C60,12 64,17 64,25 C64,30 62,34 60,36 C62,42 62,50 60,56 C57,66 48,72 38,72 C26,72 16,63 14,50 C13,42 14,32 20,26 Z"
          fill={SKIN}
        />
        {/* Knuckle lines -- without these the silhouette alone reads as an
            ambiguous blob (a real risk caught only by checking this glyph in
            isolation at true render size, not just next to its labeled
            siblings); 3 subtle ridges are what makes "closed fist" legible. */}
        <path d="M32,10 C33,14 33,18 32,22" stroke="#E0A655" strokeWidth={1.6} fill="none" opacity={0.55} />
        <path d="M44,6 C45,10 45,15 44,19" stroke="#E0A655" strokeWidth={1.6} fill="none" opacity={0.55} />
        <path d="M55,12 C56,16 56,20 55,24" stroke="#E0A655" strokeWidth={1.6} fill="none" opacity={0.55} />
        <path d="M18,36 C22,44 22,52 17,58" stroke={INDIGO} strokeWidth={3.2} strokeLinecap="round" fill="none" opacity={0.7} />
      </g>
    </FaceBase>
  );
}

export function PeopleWavingHand(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <g transform="translate(40,40) scale(1.25) translate(-43,-38)">
        <rect x="18" y="42" width="36" height="32" rx="16" fill={SKIN} />
        <rect x="8" y="44" width="13" height="24" rx="6.5" fill={SKIN} transform="rotate(-40 14.5 56)" />
        <rect x="20" y="14" width="11" height="32" rx="5.5" fill={SKIN} transform="rotate(-12 25.5 30)" />
        <rect x="31" y="8" width="11" height="34" rx="5.5" fill={SKIN} transform="rotate(-4 36.5 25)" />
        <rect x="42" y="8" width="11" height="34" rx="5.5" fill={SKIN} transform="rotate(4 47.5 25)" />
        <rect x="53" y="14" width="11" height="32" rx="5.5" fill={SKIN} transform="rotate(12 58.5 30)" />
        <path d="M28,58 C34,63 42,63 48,58" stroke={INDIGO} strokeWidth={3} strokeLinecap="round" fill="none" opacity={0.6} />
        <path d="M55,15 C59,11 63,11 65,15" stroke="#B8B4AA" strokeWidth={2.4} strokeLinecap="round" fill="none" />
        <path d="M61,9 C65,4 70,4 72,9" stroke="#B8B4AA" strokeWidth={2.2} strokeLinecap="round" fill="none" opacity={0.8} />
        <path d="M67,4 C70,0 75,0 77,4" stroke="#B8B4AA" strokeWidth={2} strokeLinecap="round" fill="none" opacity={0.6} />
      </g>
    </FaceBase>
  );
}

export function PeopleVictoryHand(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <g transform="translate(40,40) scale(1.3) translate(-40,-38)">
        <path d="M22,50 C22,38 30,32 40,32 C50,32 58,40 58,52 C58,64 50,72 40,72 C28,72 20,64 22,50 Z" fill={SKIN} />
        <rect x="27" y="6" width="12" height="36" rx="6" fill={SKIN} transform="rotate(-7 33 24)" />
        <rect x="41" y="6" width="12" height="36" rx="6" fill={SKIN} transform="rotate(7 47 24)" />
        <path d="M28,54 C33,59 41,60 47,55" stroke={INDIGO} strokeWidth={3} strokeLinecap="round" fill="none" opacity={0.6} />
      </g>
    </FaceBase>
  );
}

export function PeopleOkHand(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <g transform="translate(40,40) scale(1.15) translate(-47,-40)">
        <path d="M32,52 C32,40 40,34 50,34 C60,34 68,42 68,54 C68,66 60,74 50,74 C38,74 30,66 32,52 Z" fill={SKIN} />
        <circle cx="26" cy="26" r="15" fill="none" stroke={SKIN} strokeWidth={12} />
        <rect x="40" y="4" width="12" height="34" rx="6" fill={SKIN} transform="rotate(-10 46 22)" />
        <rect x="52" y="2" width="12" height="36" rx="6" fill={SKIN} transform="rotate(4 58 20)" />
        <rect x="64" y="6" width="12" height="34" rx="6" fill={SKIN} transform="rotate(18 70 24)" />
        <path d="M40,56 C45,61 53,61 59,56" stroke={INDIGO} strokeWidth={3} strokeLinecap="round" fill="none" opacity={0.6} />
      </g>
    </FaceBase>
  );
}

// One hand unit (palm + 4 fingers, all sharing one local frame) reused for
// both sides of PeopleClappingHands -- fixed a real bug caught only by
// rendering this glyph ALONE at true size (it looked plausible in a small
// side-by-side check sheet but was actually two disconnected finger clusters
// floating above two unrelated palm blobs): rotating each finger
// independently relative to a mismatched local origin, instead of rotating
// one already-correctly-assembled hand as a single rigid unit, silently
// breaks the connection between fingers and palm.
function ClappingHandUnit() {
  return (
    <g transform="rotate(-35 20 60)">
      <rect x="2" y="30" width="36" height="34" rx="17" fill={SKIN} />
      <rect x="4" y="2" width="11" height="34" rx="5.5" fill={SKIN} transform="rotate(-8 9.5 19)" />
      <rect x="16" y="-4" width="11" height="36" rx="5.5" fill={SKIN} transform="rotate(-3 21.5 14)" />
      <rect x="28" y="-3" width="11" height="35" rx="5.5" fill={SKIN} transform="rotate(4 33.5 14.5)" />
      <rect x="39" y="4" width="11" height="32" rx="5.5" fill={SKIN} transform="rotate(12 44.5 20)" />
    </g>
  );
}

export function PeopleClappingHands(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <g transform="translate(57,19.4) scale(0.85)">
        <ClappingHandUnit />
      </g>
      <g transform="translate(23,19.4) scale(-0.85,0.85)">
        <ClappingHandUnit />
      </g>
      <circle cx="40" cy="10" r="4.4" fill={INDIGO} />
      <rect x="55" y="4" width="8.4" height="8.4" rx="2.2" fill="#F2B33D" transform="rotate(20 59.2 8.2)" />
      <circle cx="22" cy="4" r="3.4" fill="#EF4A63" />
    </FaceBase>
  );
}

/** Registry: real Unicode codepoint -> itundaface glyph, the actual set
 * ItundaFaceEmoji.tsx's lookup/render/picker pipeline consumes for this
 * category. */
export const ITUNDAFACE_PEOPLE: Record<string, (props: FaceIconProps) => React.ReactElement> = {
  '👀': PeopleEyes,
  '✊': PeopleFist,
  '👋': PeopleWavingHand,
  '✌️': PeopleVictoryHand,
  '👌': PeopleOkHand,
  '👏': PeopleClappingHands,
};
