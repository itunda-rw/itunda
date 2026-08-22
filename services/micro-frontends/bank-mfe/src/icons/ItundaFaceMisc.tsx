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
