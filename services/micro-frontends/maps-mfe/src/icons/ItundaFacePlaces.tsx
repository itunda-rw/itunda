// itundaface: place-category glyphs (2026-08-22, direct user follow-up: "keep
// improving until itunda face is fully like tossface"). Continues the same real
// itundaface set started in bank-mfe (services/micro-frontends/bank-mfe/src/icons/
// ItundaFace.tsx, project_itunda_brand_identity.md /
// project_itunda_own_icons_graphics.md) -- this is the next real slice from that
// file's own documented roadmap: the map place-category markers, the single
// largest group (13 glyphs) found in the repo-wide real-emoji-usage audit that
// scoped itundaface in the first place.
//
// Real source: MapView.tsx's own CATEGORY_ICONS map -- RESTAURANT/CAFE/HOSPITAL/
// PHARMACY/BANK/ATM/HOTEL/SUPERMARKET/GAS_STATION/SCHOOL/ITUNDA_AGENT/MARKET/
// BUS_STOP, currently raw Unicode emoji shown in a small (~12px) category-filter
// chip row -- no prominent/hero context exists for these today, so (matching
// itundaface's own established flat-for-small/3D-for-prominent rule) these are
// flat only, no 3D variant.
//
// Construction: unlike the reaction glyphs (expressive faces, needed eyes/mouths),
// these are simple OBJECT pictograms -- one bold, single-color icon inside a
// colored circular badge, matching how real map products (Naver Map, Google Maps,
// Kakao Map) render category markers: legible at a glance, not an elaborate
// illustration. Colors are itunda's own real OKLCH-derived brand family
// (project_itunda_brand_identity.md's indigo/coral/gold/teal/forest/sky palette,
// the same real hue-rotation technique used for the app's expanded color system),
// not arbitrary picks -- financial places (BANK/ATM/ITUNDA_AGENT) use indigo,
// itunda's own real brand color, since those are itunda's own core domain.
// Iterated real render-and-inspect passes (rsvg-convert) caught and fixed 3 of the
// first 13 designs (RESTAURANT, PHARMACY, HOTEL) that didn't read clearly at a
// glance before shipping -- verification caught them, not assumption.

import type { SVGProps } from 'react';

type PlaceIconProps = SVGProps<SVGSVGElement> & { size?: number };

function PlaceBase({ size = 24, children, ...rest }: PlaceIconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      {children}
    </svg>
  );
}

export function PlaceRestaurant(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#feb6aa" />
      <g stroke="#a20800" strokeWidth={2.8} strokeLinecap="round" fill="none">
        <path d="M20,14 V26 M24,14 V26 M22,14 V44" />
        <path d="M20,26 C20,29.5 24,29.5 24,26" />
        <path d="M40,14 C34,16 34,22 40,24 V44" />
      </g>
    </PlaceBase>
  );
}

export function PlaceCafe(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#ecc38c" />
      <path d="M16,26 H40 V38 C40,43.5 35.5,48 30,48 H26 C20.5,48 16,43.5 16,38 Z" fill="#744c00" />
      <path d="M40,28 H45 C47.8,28 50,30.2 50,33 C50,35.8 47.8,38 45,38 H40" fill="none" stroke="#744c00" strokeWidth={2.6} strokeLinecap="round" />
      <path d="M22,20 C22,17 25,17 25,14 M29,20 C29,17 32,17 32,14" stroke="#744c00" strokeWidth={2.2} strokeLinecap="round" fill="none" opacity={0.8} />
    </PlaceBase>
  );
}

export function PlaceHospital(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#feb6aa" />
      <rect x="14" y="16" width="32" height="34" rx="4" fill="#ffffff" />
      <path d="M30,22 V44 M19,33 H41" stroke="#a20800" strokeWidth={5} strokeLinecap="round" />
    </PlaceBase>
  );
}

export function PlacePharmacy(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#8bdecb" />
      <g transform="rotate(-40 30 30)">
        <rect x="12" y="23" width="36" height="14" rx="7" fill="#ffffff" />
        <path d="M12,30 A7,7 0 0 1 19,23 H30 V37 H19 A7,7 0 0 1 12,30 Z" fill="#006455" />
      </g>
    </PlaceBase>
  );
}

export function PlaceBank(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0c6ff" />
      <path d="M14,22 L30,12 L46,22 Z" fill="#282565" />
      <rect x="14" y="22" width="32" height="4" fill="#282565" />
      <rect x="18" y="28" width="4" height="16" fill="#282565" />
      <rect x="26" y="28" width="4" height="16" fill="#282565" />
      <rect x="34" y="28" width="4" height="16" fill="#282565" />
      <rect x="42" y="28" width="4" height="16" fill="#282565" />
      <rect x="13" y="46" width="34" height="4" rx="1" fill="#282565" />
    </PlaceBase>
  );
}

export function PlaceAtm(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0c6ff" />
      <rect x="17" y="14" width="26" height="34" rx="4" fill="#282565" />
      <rect x="21" y="19" width="18" height="12" rx="1.5" fill="#7c7bfd" />
      <rect x="21" y="35" width="18" height="3" rx="1.5" fill="#7c7bfd" />
      <circle cx="34" cy="42" r="1.6" fill="#7c7bfd" />
    </PlaceBase>
  );
}

export function PlaceHotel(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#dccb8a" />
      <path d="M14,44 V26 C14,24.3 15.3,23 17,23 H27 C28.7,23 30,24.3 30,26 V32" stroke="#665400" strokeWidth={2.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M30,32 H43 C44.7,32 46,33.3 46,35 V44" stroke="#665400" strokeWidth={2.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="14" y="32" width="32" height="4" rx="1.5" fill="#665400" />
      <line x1="12" y1="44" x2="12" y2="38" stroke="#665400" strokeWidth={2.6} strokeLinecap="round" />
      <line x1="48" y1="44" x2="48" y2="38" stroke="#665400" strokeWidth={2.6} strokeLinecap="round" />
    </PlaceBase>
  );
}

export function PlaceSupermarket(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#b9d79b" />
      <path d="M16,16 H21 L26,38 H43 L47,22 H24" stroke="#3e6200" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <circle cx="29" cy="45" r="3.4" fill="#3e6200" />
      <circle cx="41" cy="45" r="3.4" fill="#3e6200" />
    </PlaceBase>
  );
}

export function PlaceGasStation(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0ccdd" />
      <rect x="17" y="16" width="18" height="32" rx="3" fill="#415676" />
      <rect x="21" y="21" width="10" height="8" rx="1.5" fill="#c0ccdd" />
      <path d="M35,26 H39 C41,26 42,27.5 42,29.5 V40 C42,41.5 43,42.5 44.5,42.5 C46,42.5 47,41.5 47,40 V32 L44,29" stroke="#415676" strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </PlaceBase>
  );
}

export function PlaceSchool(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#97d5f5" />
      <path d="M30,16 L50,25 L30,34 L10,25 Z" fill="#005d7f" />
      <path d="M20,29 V38 C20,41 24,44 30,44 C36,44 40,41 40,38 V29" stroke="#005d7f" strokeWidth={2.4} fill="none" strokeLinecap="round" />
      <line x1="50" y1="25" x2="50" y2="37" stroke="#005d7f" strokeWidth={2.2} strokeLinecap="round" />
    </PlaceBase>
  );
}

export function PlaceItundaAgent(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0c6ff" />
      <path d="M24,20 C24,16 27,13 30,13 C33,13 36,16 36,20" stroke="#483eb6" strokeWidth={2.6} fill="none" strokeLinecap="round" />
      <path d="M20,22 H40 L44,38 C45,43 41,48 35,48 H25 C19,48 15,43 16,38 Z" fill="#483eb6" />
      <circle cx="30" cy="34" r="6" fill="none" stroke="#c0c6ff" strokeWidth={2} />
      <line x1="30" y1="30" x2="30" y2="38" stroke="#c0c6ff" strokeWidth={2} strokeLinecap="round" />
    </PlaceBase>
  );
}

export function PlaceMarket(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#b3d5b9" />
      <path d="M16,26 H44 L40,44 C39.5,46.3 37.5,48 35,48 H25 C22.5,48 20.5,46.3 20,44 Z" fill="#156631" />
      <path d="M23,26 C23,20 26,16 30,16 C34,16 37,20 37,26" stroke="#156631" strokeWidth={2.6} fill="none" strokeLinecap="round" />
      <path d="M22,32 H38 M23,38 H37" stroke="#b3d5b9" strokeWidth={1.8} opacity={0.7} />
    </PlaceBase>
  );
}

export function PlaceBusStop(props: PlaceIconProps) {
  return (
    <PlaceBase {...props}>
      <circle cx="30" cy="30" r="28" fill="#c0ccdd" />
      <rect x="14" y="18" width="32" height="22" rx="5" fill="#253142" />
      <rect x="18" y="22" width="9" height="8" rx="1.5" fill="#c0ccdd" />
      <rect x="33" y="22" width="9" height="8" rx="1.5" fill="#c0ccdd" />
      <circle cx="21" cy="43" r="3.4" fill="#253142" />
      <circle cx="39" cy="43" r="3.4" fill="#253142" />
    </PlaceBase>
  );
}

// Maps MapView.tsx's own real CATEGORY_ICONS keys to itundaface's glyphs --
// display-layer only, the underlying category id strings sent to/from the
// backend are unchanged.
const ITUNDAFACE_PLACES: Record<string, (props: PlaceIconProps) => React.ReactElement> = {
  RESTAURANT: PlaceRestaurant,
  CAFE: PlaceCafe,
  HOSPITAL: PlaceHospital,
  PHARMACY: PlacePharmacy,
  BANK: PlaceBank,
  ATM: PlaceAtm,
  HOTEL: PlaceHotel,
  SUPERMARKET: PlaceSupermarket,
  GAS_STATION: PlaceGasStation,
  SCHOOL: PlaceSchool,
  ITUNDA_AGENT: PlaceItundaAgent,
  MARKET: PlaceMarket,
  BUS_STOP: PlaceBusStop,
};

/** Renders itundaface's own glyph for a known place-category id, falling back to
 * a plain pin emoji for anything outside the 13 known categories (matches
 * CATEGORY_ICONS' own existing `?? '📍'` fallback). */
export function PlaceGlyph({ category, size = 16 }: { category: string; size?: number }) {
  const Icon = ITUNDAFACE_PLACES[category];
  return Icon ? <Icon size={size} /> : <span>📍</span>;
}
