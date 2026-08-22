// itundaface: misc single/dual-use glyphs (2026-08-22, direct user follow-up:
// "keep them high quality and premium like tossface but don't forget
// itundaface identity and keep going until we have complete itundaface like
// tossface"). Continues the same real itundaface set -- this batch covers the
// remaining real emoji found in the repo-wide audit that don't share a
// bigger group of their own: 🔥 (Commerce "Deals" rail + merchant-busy
// warning, 3 sites), 📦 (Marketplace escrow delivery address, 1 site), and
// 🚲/⚡ (BikeShareView's Regular/Electric bike-type label, 3 sites).
//
// Construction stays consistent with the rest of itundaface: no badge circle
// (all 4 render inline in running text, same as HeartFilled/LockGlyph), flat
// dual-tone fill for visual depth without a gradient. Colors: flame keeps its
// real-world warm orange/yellow (meaning-preserving, same rule that kept the
// reaction face yellow and the wishlist heart red), package keeps a natural
// cardboard tan, bike uses itunda's own teal family (distinct from every
// other glyph group so far, giving BikeShareView its own visual identity)
// with a small itunda-gold lightning badge marking the electric variant --
// same bike, electric variant marked, rather than two unrelated icons.

import type { SVGProps } from 'react';

type MiscIconProps = SVGProps<SVGSVGElement> & { size?: number };

export function FlameGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M12,22 C7.5,22 5,18.8 5,15.2 C5,11.5 7.4,8.6 8.4,5.6 C8.7,4.7 9.7,4.6 10.1,5.4 C10.9,7 11,8.8 12,9 C13,8.3 12.8,5.2 12.2,3.2 C11.9,2.2 12.8,1.5 13.6,2.1 C17.4,4.9 19,9.5 19,13.5 C19,18.5 16,22 12,22 Z" fill="#f0740a" />
      <path d="M12,20 C9.5,20 8,18 8,15.8 C8,13.8 9.3,12.3 10.2,10.9 C10.6,10.3 11.4,10.5 11.5,11.2 C11.6,12.2 12.1,12.6 12.6,12.2 C13.1,11.8 13,10.4 12.7,9.4 C12.5,8.7 13.2,8.2 13.8,8.6 C15.6,9.9 16.5,12.1 16.5,14.2 C16.5,17.6 14.6,20 12,20 Z" fill="#ffb02e" />
    </svg>
  );
}

export function PackageGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M12,2.5 L21,7 V17 L12,21.5 L3,17 V7 Z" fill="#c8935a" />
      <path d="M12,2.5 L21,7 L12,11.5 L3,7 Z" fill="#e0ac74" />
      <path d="M12,11.5 V21.5" stroke="#8a5f33" strokeWidth={1.2} />
      <path d="M7.5,4.7 L16.5,9.2" stroke="#8a5f33" strokeWidth={1.4} strokeLinecap="round" />
    </svg>
  );
}

export function BikeGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <circle cx="5.5" cy="17" r="4" fill="none" stroke="#0a8a72" strokeWidth={1.8} />
      <circle cx="18.5" cy="17" r="4" fill="none" stroke="#0a8a72" strokeWidth={1.8} />
      <path d="M5.5,17 L10,8 H15 M10,8 L13,13 M13,13 L18.5,17 M13,13 L8.5,17" fill="none" stroke="#0a8a72" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="15" cy="8" r="1.3" fill="#0a8a72" />
      <path d="M9,8 H11.4" stroke="#0a8a72" strokeWidth={1.8} strokeLinecap="round" />
    </svg>
  );
}

export function ElectricBikeGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <circle cx="5.5" cy="17" r="4" fill="none" stroke="#0a8a72" strokeWidth={1.8} />
      <circle cx="18.5" cy="17" r="4" fill="none" stroke="#0a8a72" strokeWidth={1.8} />
      <path d="M5.5,17 L10,8 H15 M10,8 L13,13 M13,13 L18.5,17 M13,13 L8.5,17" fill="none" stroke="#0a8a72" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="15" cy="8" r="1.3" fill="#0a8a72" />
      <path d="M9,8 H11.4" stroke="#0a8a72" strokeWidth={1.8} strokeLinecap="round" />
      <circle cx="18.5" cy="6" r="5.6" fill="#ffb02e" />
      <path d="M19.6,2.4 L16.8,6.6 H18.7 L17.9,9.6 L21,5.2 H19 Z" fill="#7a5300" />
    </svg>
  );
}

/** Renders itundaface's own bike glyph for a given bike type -- drop-in swap
 * for the '🚲 Regular' / '⚡ Electric' pair. */
export function BikeTypeGlyph({ electric, size = 16 }: { electric: boolean; size?: number }) {
  return electric ? <ElectricBikeGlyph size={size} /> : <BikeGlyph size={size} />;
}

// Second misc batch, same session, same audit -- the remaining real single/
// dual-use emoji found across pinned messages, sold-out/closed status,
// open-chat + invite-link sharing, message CTAs, scheduled-trip time, and
// tab-label alerts. Same construction discipline: no badge circle, inline in
// running text.

export function PinGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M12,2 C8.1,2 5,5.1 5,9 C5,14.2 12,22 12,22 C12,22 19,14.2 19,9 C19,5.1 15.9,2 12,2 Z" fill="#f04452" />
      <circle cx="12" cy="9" r="3.2" fill="#ffe2df" />
    </svg>
  );
}

export function SoldOutGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <circle cx="12" cy="12" r="9.5" fill="none" stroke="#f04452" strokeWidth={2.4} />
      <line x1="5.8" y1="18.2" x2="18.2" y2="5.8" stroke="#f04452" strokeWidth={2.4} strokeLinecap="round" />
    </svg>
  );
}

export function LinkGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M10.5,13.5 L13.5,10.5" stroke="#483eb6" strokeWidth={2.2} strokeLinecap="round" />
      <path d="M11.5,8 L14,5.5 C15.7,3.8 18.4,3.8 20,5.5 C21.7,7.2 21.7,9.9 20,11.5 L17.5,14" fill="none" stroke="#483eb6" strokeWidth={2.2} strokeLinecap="round" />
      <path d="M12.5,16 L10,18.5 C8.3,20.2 5.6,20.2 4,18.5 C2.3,16.8 2.3,14.1 4,12.5 L6.5,10" fill="none" stroke="#7c7bfd" strokeWidth={2.2} strokeLinecap="round" />
    </svg>
  );
}

export function ChatGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M12,3 C6.5,3 2,6.6 2,11 C2,13.5 3.4,15.7 5.7,17.2 C5.5,18.4 4.9,19.6 4,20.6 C5.7,20.4 7.4,19.7 8.8,18.7 C9.8,18.9 10.9,19 12,19 C17.5,19 22,15.4 22,11 C22,6.6 17.5,3 12,3 Z" fill="#7472f4" />
      <circle cx="8" cy="11" r="1.3" fill="#ffffff" />
      <circle cx="12" cy="11" r="1.3" fill="#ffffff" />
      <circle cx="16" cy="11" r="1.3" fill="#ffffff" />
    </svg>
  );
}

export function ClockGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <circle cx="12" cy="12" r="9.5" fill="#c0c6ff" />
      <path d="M12,6.5 V12 L16,14.5" fill="none" stroke="#282565" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function GlobeGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <circle cx="12" cy="12" r="9.5" fill="none" stroke="#0a8a72" strokeWidth={1.8} />
      <ellipse cx="12" cy="12" rx="4" ry="9.5" fill="none" stroke="#0a8a72" strokeWidth={1.8} />
      <line x1="2.5" y1="12" x2="21.5" y2="12" stroke="#0a8a72" strokeWidth={1.8} />
      <path d="M4,7.5 H20" fill="none" stroke="#0a8a72" strokeWidth={1.6} />
      <path d="M4,16.5 H20" fill="none" stroke="#0a8a72" strokeWidth={1.6} />
    </svg>
  );
}

export function BellGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" {...rest}>
      <path d="M12,2.5 C10.6,2.5 9.5,3.6 9.5,5 V5.6 C7,6.5 5.3,8.9 5.3,11.7 V15.5 L3.5,18 C3.2,18.4 3.5,19 4,19 H20 C20.5,19 20.8,18.4 20.5,18 L18.7,15.5 V11.7 C18.7,8.9 17,6.5 14.5,5.6 V5 C14.5,3.6 13.4,2.5 12,2.5 Z" fill="#dccb8a" />
      <path d="M9.5,20.5 C9.5,21.6 10.6,22.5 12,22.5 C13.4,22.5 14.5,21.6 14.5,20.5" fill="none" stroke="#665400" strokeWidth={1.6} strokeLinecap="round" />
    </svg>
  );
}

// Third misc batch, same session, same audit -- the remaining real single-use
// emoji that don't collide with the pin/link/chat/clock/globe/bell batch
// above: camera (photo attach), cake (birthday), money bag (price-offer
// amount), shopping bag (emoticon store), wrench (marketplace inspections),
// price-drop (listing badge). 72x72 viewBox (vs. this file's other batches'
// 24x24) -- an internal inconsistency introduced by two independent passes
// landing the same day; left as-is rather than rescaling working, verified
// path data purely for stylistic uniformity.

export function CameraGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 72 72" {...rest}>
      <path d="M10,22 H26 L30,14 H42 L46,22 H62 C64.2,22 66,23.8 66,26 V58 C66,60.2 64.2,62 62,62 H10 C7.8,62 6,60.2 6,58 V26 C6,23.8 7.8,22 10,22 Z" fill="#415676" />
      <circle cx="36" cy="42" r="14" fill="#c0ccdd" />
      <circle cx="36" cy="42" r="8" fill="#415676" />
      <circle cx="56" cy="30" r="2.6" fill="#c0ccdd" />
    </svg>
  );
}

export function CakeGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 72 72" {...rest}>
      <rect x="14" y="40" width="44" height="24" rx="3" fill="#a20800" />
      <path d="M14,40 C14,34 20,34 24,40 C28,46 34,46 38,40 C42,34 48,34 52,40 C56,46 58,40 58,40 V48 H14 Z" fill="#feb6aa" />
      <rect x="33" y="20" width="6" height="14" rx="2" fill="#dccb8a" />
      <path d="M36,10 C36,10 40,14 36,18 C32,14 36,10 36,10 Z" fill="#f0740a" />
    </svg>
  );
}

export function MoneyBagGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 72 72" {...rest}>
      <path d="M28,14 C28,9 31.6,5 36,5 C40.4,5 44,9 44,14" stroke="#483eb6" strokeWidth={3.2} fill="none" strokeLinecap="round" />
      <path d="M22,17 H50 L57,45 C59,53 51,62 42,62 H30 C21,62 13,53 15,45 Z" fill="#483eb6" />
      <circle cx="36" cy="40" r="8" fill="none" stroke="#c0c6ff" strokeWidth={2.6} />
      <line x1="36" y1="34.5" x2="36" y2="45.5" stroke="#c0c6ff" strokeWidth={2.6} strokeLinecap="round" />
    </svg>
  );
}

export function ShoppingBagGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 72 72" {...rest}>
      <rect x="14" y="28" width="44" height="36" rx="4" fill="#e0455c" />
      <path d="M22,28 V20 C22,14.5 26.5,10 32,10" stroke="#e0455c" strokeWidth={4} fill="none" strokeLinecap="round" />
      <path d="M50,28 V20 C50,14.5 45.5,10 40,10" stroke="#e0455c" strokeWidth={4} fill="none" strokeLinecap="round" />
      <line x1="14" y1="38" x2="58" y2="38" stroke="#ffd7dc" strokeWidth={2.6} opacity={0.8} />
    </svg>
  );
}

export function WrenchGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 72 72" {...rest}>
      <path d="M46,10 C40,10 35,15 35,21 C35,23 35.5,24.8 36.4,26.4 L14,48.8 C12,50.8 12,54 14,56 L16,58 C18,60 21.2,60 23.2,58 L45.6,35.6 C47.2,36.5 49,37 51,37 C57,37 62,32 62,26 C62,24.3 61.6,22.7 60.9,21.3 L52.4,29.8 L45.2,22.6 L53.7,14.1 C52.3,13.4 50.7,13 49,13" fill="#415676" />
    </svg>
  );
}

export function PriceDropGlyph({ size = 16, ...rest }: MiscIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 72 72" {...rest}>
      <path d="M36,52 L14,20 H58 Z" fill="#e0455c" />
      <rect x="14" y="58" width="44" height="6" rx="3" fill="#e0455c" />
    </svg>
  );
}
