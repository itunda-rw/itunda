// itundaface People & Body -- phase 2 of the "reach TossFace's 3,600-glyph
// scale" initiative (see ItundaFaceSmileys.tsx / project_itunda_own_icons_
// graphics.md's staged category roadmap for phase 1 and the full plan). Real
// Unicode "People & Body" group, prioritized by real chat-usage frequency
// among hand gestures specifically (the sub-group people actually reach for),
// not spec order.
//
// Harder to construct correctly than Smileys' round faces -- hand/gesture
// shapes have no forgiving symmetric base to fall back on. Built and verified
// via 2 real rsvg-convert render-and-inspect passes (not shipped on the first
// attempt): the initial 8-glyph draft included Muscle and Folded Hands, both
// of which did not read clearly at a glance even after a redesign iteration
// (Muscle looked like a comma with a hat; Folded Hands looked like a single
// peanut) -- CUT from this batch rather than shipped broken, same standard
// the place-category icons (restaurant/pharmacy/hotel) were held to. Only the
// 6 glyphs that read clearly ship here; Muscle/Folded Hands are a real,
// documented open item for a future redesign pass.
//
// Shared skin-tone palette (fill #FFCF87, cuff/shade #E0A655) matches
// ItundaFace.tsx's existing ReactionThumbsUp exactly -- itundaface's own
// established hand-glyph convention, not a new palette. Eyes' iris uses real
// itunda indigo (a non-semantic accent, same signature rule as the tear
// accents elsewhere in itundaface).

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
const CUFF = '#E0A655';

export function PeopleEyes(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <ellipse cx="24" cy="42" rx="17" ry="14" fill="#FFFFFF" stroke="#B8B4AA" strokeWidth={2} />
      <ellipse cx="56" cy="42" rx="17" ry="14" fill="#FFFFFF" stroke="#B8B4AA" strokeWidth={2} />
      <circle cx="26" cy="42" r="7" fill="#7472F4" />
      <circle cx="58" cy="42" r="7" fill="#7472F4" />
      <circle cx="28" cy="39" r="2" fill="#FFFFFF" />
      <circle cx="60" cy="39" r="2" fill="#FFFFFF" />
    </FaceBase>
  );
}

export function PeopleFist(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <path d="M28,32 V72 C28,74.2 26.2,76 24,76 H16 C13.8,76 12,74.2 12,72 V38 C12,35.8 13.8,34 16,34 H24 Z" fill={CUFF} />
      <path
        d="M32,34 H56 C60,34 63,37.2 63,41.2 C63,42.6 62.6,44 61.9,45.1 C64.3,46.1 66,48.5 66,51.2 C66,53.4 64.9,55.3 63.2,56.6 C64.3,58 65,59.8 65,61.7 C65,64.5 63.4,66.9 61.1,68.1 C61.4,68.9 61.6,69.8 61.6,70.7 C61.6,74.7 58.3,78 54.3,78 H36 C33.8,78 32,76.2 32,74 V34 Z"
        fill={SKIN}
      />
      <path d="M32,40 C27,38 23,42 24,47 C25,51 31,53 35,50 C38,47 37,42 32,40 Z" fill={SKIN} />
    </FaceBase>
  );
}

export function PeopleWavingHand(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <rect x="26" y="64" width="28" height="16" rx="8" fill={CUFF} />
      <ellipse cx="40" cy="50" rx="17" ry="20" fill={SKIN} />
      <rect x="10" y="42" width="10" height="22" rx="5" fill={SKIN} transform="rotate(-55 15 53)" />
      <rect x="18" y="14" width="9" height="26" rx="4.5" fill={SKIN} transform="rotate(-22 22.5 27)" />
      <rect x="28" y="8" width="9" height="30" rx="4.5" fill={SKIN} transform="rotate(-8 32.5 23)" />
      <rect x="39" y="6" width="9" height="32" rx="4.5" fill={SKIN} transform="rotate(6 43.5 22)" />
      <rect x="50" y="10" width="9" height="28" rx="4.5" fill={SKIN} transform="rotate(20 54.5 24)" />
    </FaceBase>
  );
}

export function PeopleVictoryHand(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <rect x="24" y="66" width="28" height="14" rx="7" fill={CUFF} />
      <ellipse cx="38" cy="54" rx="19" ry="17" fill={SKIN} />
      <ellipse cx="22" cy="56" rx="7" ry="10" fill={SKIN} />
      <rect x="26" y="10" width="11" height="36" rx="5.5" fill={SKIN} transform="rotate(-8 31.5 28)" />
      <rect x="40" y="10" width="11" height="36" rx="5.5" fill={SKIN} transform="rotate(8 45.5 28)" />
    </FaceBase>
  );
}

export function PeopleOkHand(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <rect x="34" y="60" width="28" height="14" rx="7" fill={CUFF} />
      <ellipse cx="46" cy="46" rx="19" ry="18" fill={SKIN} />
      <circle cx="30" cy="28" r="13" fill="none" stroke={SKIN} strokeWidth={11} />
      <rect x="44" y="10" width="11" height="30" rx="5.5" fill={SKIN} transform="rotate(-16 49.5 25)" />
      <rect x="55" y="8" width="11" height="32" rx="5.5" fill={SKIN} transform="rotate(2 60.5 24)" />
      <rect x="66" y="12" width="11" height="30" rx="5.5" fill={SKIN} transform="rotate(20 71.5 27)" />
    </FaceBase>
  );
}

export function PeopleClappingHands(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      <ellipse cx="26" cy="46" rx="16" ry="20" fill={SKIN} transform="rotate(-25 26 46)" />
      <ellipse cx="54" cy="46" rx="16" ry="20" fill={SKIN} transform="rotate(25 54 46)" />
      <rect x="16" y="14" width="7" height="20" rx="3.5" fill={SKIN} transform="rotate(-30 19.5 24)" />
      <rect x="24" y="8" width="7" height="22" rx="3.5" fill={SKIN} transform="rotate(-12 27.5 19)" />
      <rect x="49" y="8" width="7" height="22" rx="3.5" fill={SKIN} transform="rotate(12 52.5 19)" />
      <rect x="57" y="14" width="7" height="20" rx="3.5" fill={SKIN} transform="rotate(30 60.5 24)" />
      <line x1="40" y1="2" x2="40" y2="12" stroke="#7472F4" strokeWidth={3} strokeLinecap="round" />
      <line x1="30" y1="6" x2="35" y2="14" stroke="#7472F4" strokeWidth={3} strokeLinecap="round" />
      <line x1="50" y1="6" x2="45" y2="14" stroke="#7472F4" strokeWidth={3} strokeLinecap="round" />
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
