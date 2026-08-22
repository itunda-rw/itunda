// itundaface: gift-theme + split-bill glyphs (2026-08-22, direct user follow-up:
// "keep improving until itunda face is fully like tossface"). Continues the same
// real itundaface set (ItundaFace.tsx for reactions, maps-mfe's
// ItundaFacePlaces.tsx for place categories) -- this is the next real slice from
// that roadmap: the gift-theme picker (GiftTheme enum, ./lib/gift.ts) plus the two
// standalone, non-enum emoji (🎁 "plain gift", 🎲 split-bill "ladder mode") found
// in the repo-wide real-emoji-usage audit that scoped itundaface in the first
// place.
//
// Construction matches ItundaFacePlaces.tsx: one bold, single-color pictogram
// inside a colored circular badge, flat only (no prominent/hero context exists for
// these today, matching itundaface's own flat-for-small/3D-for-prominent rule).
// Colors are itunda's own real OKLCH-derived brand family -- GIFT uses indigo
// (itunda's own core-domain color, matching PlaceBank/PlaceAtm/PlaceItundaAgent's
// same choice), the 4 GiftTheme presets each get a distinct family so the picker
// stays visually scannable, DICE uses teal to stay distinct from SETTLE_UP's
// slate-blue. Iterated render-and-inspect pass (rsvg-convert) caught a real miss --
// the first HEARTFELT draft's doc comment said "envelope + heart" but only drew
// the envelope -- fixed before shipping, not assumed correct.

import type { SVGProps } from 'react';
import type { GiftTheme } from '../lib/gift';

type GiftIconProps = SVGProps<SVGSVGElement> & { size?: number };

function GiftBase({ size = 24, children, ...rest }: GiftIconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      {children}
    </svg>
  );
}

export function GiftBox(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0c6ff" />
      <rect x="14" y="26" width="32" height="22" rx="2" fill="#282565" />
      <rect x="14" y="20" width="32" height="8" rx="2" fill="#483eb6" />
      <rect x="28" y="20" width="4" height="28" fill="#7c7bfd" />
      <path d="M22,20 C16,20 15,12 22,12 C27,12 28,17 28,20 Z" fill="#7c7bfd" />
      <path d="M38,20 C44,20 45,12 38,12 C33,12 32,17 32,20 Z" fill="#7c7bfd" />
    </GiftBase>
  );
}

export function GiftCongratulations(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#dccb8a" />
      <path d="M18,44 L36,18 L42,46 Z" fill="#665400" />
      <circle cx="14" cy="16" r="2.6" fill="#665400" />
      <circle cx="24" cy="8" r="2.2" fill="#665400" />
      <circle cx="36" cy="8" r="2.6" fill="#665400" />
      <circle cx="46" cy="15" r="2.2" fill="#665400" />
      <circle cx="20" cy="24" r="1.8" fill="#665400" />
    </GiftBase>
  );
}

export function GiftHeartfelt(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#feb6aa" />
      <rect x="13" y="18" width="34" height="24" rx="3" fill="#a20800" />
      <path d="M13,20 L30,32 L47,20" stroke="#feb6aa" strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M30,29 C30,29 25,25.5 25,22.3 C25,20 27,18.6 29,19.4 C29.6,19.6 30,20.1 30,20.7 C30,20.1 30.4,19.6 31,19.4 C33,18.6 35,20 35,22.3 C35,25.5 30,29 30,29 Z" fill="#feb6aa" />
    </GiftBase>
  );
}

export function GiftGoodLuck(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#b3d5b9" />
      <path d="M30,30 C30,22 24,18 20,22 C16,26 20,32 28,31 Z" fill="#156631" />
      <path d="M30,30 C38,30 42,24 38,20 C34,16 28,20 29,28 Z" fill="#156631" />
      <path d="M30,30 C22,30 18,36 22,40 C26,44 32,40 31,32 Z" fill="#156631" />
      <path d="M30,30 C30,38 36,42 40,38 C44,34 40,28 32,29 Z" fill="#156631" />
      <line x1="30" y1="30" x2="30" y2="46" stroke="#156631" strokeWidth={2.4} strokeLinecap="round" />
    </GiftBase>
  );
}

export function GiftSettleUp(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0ccdd" />
      <path d="M18,12 H42 V46 L38,43 L34,46 L30,43 L26,46 L22,43 L18,46 Z" fill="#253142" />
      <line x1="22" y1="20" x2="38" y2="20" stroke="#c0ccdd" strokeWidth={2} strokeLinecap="round" />
      <line x1="22" y1="26" x2="38" y2="26" stroke="#c0ccdd" strokeWidth={2} strokeLinecap="round" />
      <line x1="22" y1="32" x2="34" y2="32" stroke="#c0ccdd" strokeWidth={2} strokeLinecap="round" />
    </GiftBase>
  );
}

export function SplitBillDice(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#8bd8d1" />
      <rect x="14" y="16" width="24" height="24" rx="5" fill="#ffffff" stroke="#00695c" strokeWidth={2} />
      <circle cx="20" cy="22" r="2.1" fill="#00695c" />
      <circle cx="32" cy="22" r="2.1" fill="#00695c" />
      <circle cx="26" cy="28" r="2.1" fill="#00695c" />
      <circle cx="20" cy="34" r="2.1" fill="#00695c" />
      <circle cx="32" cy="34" r="2.1" fill="#00695c" />
      <rect x="26" y="26" width="24" height="24" rx="5" fill="#00695c" stroke="#00695c" strokeWidth={2} />
      <circle cx="32" cy="32" r="2.1" fill="#8bd8d1" />
      <circle cx="44" cy="32" r="2.1" fill="#8bd8d1" />
      <circle cx="32" cy="44" r="2.1" fill="#8bd8d1" />
      <circle cx="44" cy="44" r="2.1" fill="#8bd8d1" />
    </GiftBase>
  );
}

export function VoucherTicket(props: GiftIconProps) {
  return (
    <GiftBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#97d5f5" />
      <path
        d="M14,24 C14,21.8 15.8,20 18,20 H42 C44.2,20 46,21.8 46,24 V26 C44.3,26 43,27.3 43,29 C43,30.7 44.3,32 46,32 V36 C46,38.2 44.2,40 42,40 H18 C15.8,40 14,38.2 14,36 V32 C15.7,32 17,30.7 17,29 C17,27.3 15.7,26 14,26 Z"
        fill="#005d7f"
      />
      <line x1="30" y1="24" x2="30" y2="36" stroke="#97d5f5" strokeWidth={2} strokeDasharray="2.5,2.5" strokeLinecap="round" />
    </GiftBase>
  );
}

const ITUNDAFACE_GIFT_THEMES: Record<GiftTheme, (props: GiftIconProps) => React.ReactElement> = {
  CONGRATULATIONS: GiftCongratulations,
  HEARTFELT: GiftHeartfelt,
  GOOD_LUCK: GiftGoodLuck,
  SETTLE_UP: GiftSettleUp,
};

/** Renders itundaface's own glyph for a gift's theme, falling back to the plain
 * (untinted) gift box for a themeless gift -- mirrors GIFT_THEME_LABELS' own
 * `gift.theme ? ... : '🎁'` fallback pattern. */
export function GiftGlyph({ theme, size = 16 }: { theme: GiftTheme | null; size?: number }) {
  const Icon = theme ? ITUNDAFACE_GIFT_THEMES[theme] : GiftBox;
  return <Icon size={size} />;
}

export function DiceGlyph({ size = 16 }: { size?: number }) {
  return <SplitBillDice size={size} />;
}
