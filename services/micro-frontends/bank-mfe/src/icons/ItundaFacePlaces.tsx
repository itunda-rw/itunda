// itundaface: place-category glyphs, ported from Android's
// features/maps/impl/ItundaFacePlaces.kt (2026-08-29, closing
// [[project_itunda_pure_tossface_icons]]'s disclosed "Place* glyphs never
// ported to web" follow-up -- confirmed via grep that no web equivalent
// existed despite both Android's and iOS's own doc comments claiming a web
// origin; ported from Android/iOS's own byte-identical shape data instead,
// which agrees between those two platforms independent of that disputed
// citation). Every coordinate/color below is copied unchanged from that
// Kotlin file's Shape2D primitives -- same viewBox-as-native-canvas-size
// convention (60x60), just re-expressed as plain SVG since web needs no
// custom drawing primitive layer.

import type { SVGProps } from 'react';

type PlaceIconProps = SVGProps<SVGSVGElement> & { size?: number };

function PlaceBadge({ color }: { color: string }) {
  return <circle cx={30} cy={30} r={28} fill={color} />;
}

export function PlaceRestaurant({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#FEB6AA" />
      <path d="M20,14 V26 M24,14 V26 M22,14 V44" fill="none" stroke="#A20800" strokeWidth={2.8} strokeLinecap="round" />
      <path d="M20,26 C20,29.5 24,29.5 24,26" fill="none" stroke="#A20800" strokeWidth={2.8} strokeLinecap="round" />
      <path d="M40,14 C34,16 34,22 40,24 V44" fill="none" stroke="#A20800" strokeWidth={2.8} strokeLinecap="round" />
    </svg>
  );
}

export function PlaceCafe({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#ECC38C" />
      <path d="M16,26 H40 V38 C40,43.5 35.5,48 30,48 H26 C20.5,48 16,43.5 16,38 Z" fill="#744C00" />
      <path d="M40,28 H45 C47.8,28 50,30.2 50,33 C50,35.8 47.8,38 45,38 H40" fill="none" stroke="#744C00" strokeWidth={2.6} strokeLinecap="round" />
      <path d="M22,20 C22,17 25,17 25,14 M29,20 C29,17 32,17 32,14" fill="none" stroke="#744C00" strokeWidth={2.2} strokeLinecap="round" opacity={0.8} />
    </svg>
  );
}

export function PlaceHospital({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#FEB6AA" />
      <rect x={14} y={16} width={32} height={34} rx={4} fill="#ffffff" />
      <path d="M30,22 V44 M19,33 H41" fill="none" stroke="#A20800" strokeWidth={5} strokeLinecap="round" />
    </svg>
  );
}

export function PlacePharmacy({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#8BDECB" />
      <g transform="rotate(-40 30 30)">
        <rect x={12} y={23} width={36} height={14} rx={7} fill="#ffffff" />
        <path d="M12,30 A7,7 0 0 1 19,23 H30 V37 H19 A7,7 0 0 1 12,30 Z" fill="#006455" />
      </g>
    </svg>
  );
}

export function PlaceBank({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#C0C6FF" />
      <path d="M14,22 L30,12 L46,22 Z" fill="#282565" />
      <rect x={14} y={22} width={32} height={4} fill="#282565" />
      <rect x={18} y={28} width={4} height={16} fill="#282565" />
      <rect x={26} y={28} width={4} height={16} fill="#282565" />
      <rect x={34} y={28} width={4} height={16} fill="#282565" />
      <rect x={42} y={28} width={4} height={16} fill="#282565" />
      <rect x={13} y={46} width={34} height={4} rx={1} fill="#282565" />
    </svg>
  );
}

export function PlaceAtm({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#C0C6FF" />
      <rect x={17} y={14} width={26} height={34} rx={4} fill="#282565" />
      <rect x={21} y={19} width={18} height={12} rx={1.5} fill="#7C7BFD" />
      <rect x={21} y={35} width={18} height={3} rx={1.5} fill="#7C7BFD" />
      <circle cx={34} cy={42} r={1.6} fill="#7C7BFD" />
    </svg>
  );
}

export function PlaceHotel({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#DCCB8A" />
      <path d="M14,44 V26 C14,24.3 15.3,23 17,23 H27 C28.7,23 30,24.3 30,26 V32" fill="none" stroke="#665400" strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M30,32 H43 C44.7,32 46,33.3 46,35 V44" fill="none" stroke="#665400" strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" />
      <rect x={14} y={32} width={32} height={4} rx={1.5} fill="#665400" />
      <line x1={12} y1={44} x2={12} y2={38} stroke="#665400" strokeWidth={2.6} strokeLinecap="round" />
      <line x1={48} y1={44} x2={48} y2={38} stroke="#665400" strokeWidth={2.6} strokeLinecap="round" />
    </svg>
  );
}

export function PlaceSupermarket({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#B9D79B" />
      <path d="M16,16 H21 L26,38 H43 L47,22 H24" fill="none" stroke="#3E6200" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={29} cy={45} r={3.4} fill="#3E6200" />
      <circle cx={41} cy={45} r={3.4} fill="#3E6200" />
    </svg>
  );
}

export function PlaceGasStation({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#C0CCDD" />
      <rect x={17} y={16} width={18} height={32} rx={3} fill="#415676" />
      <rect x={21} y={21} width={10} height={8} rx={1.5} fill="#C0CCDD" />
      <path d="M35,26 H39 C41,26 42,27.5 42,29.5 V40 C42,41.5 43,42.5 44.5,42.5 C46,42.5 47,41.5 47,40 V32 L44,29" fill="none" stroke="#415676" strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function PlaceSchool({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#97D5F5" />
      <path d="M30,16 L50,25 L30,34 L10,25 Z" fill="#005D7F" />
      <path d="M20,29 V38 C20,41 24,44 30,44 C36,44 40,41 40,38 V29" fill="none" stroke="#005D7F" strokeWidth={2.4} strokeLinecap="round" />
      <line x1={50} y1={25} x2={50} y2={37} stroke="#005D7F" strokeWidth={2.2} strokeLinecap="round" />
    </svg>
  );
}

export function PlaceItundaAgent({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#C0C6FF" />
      <path d="M24,20 C24,16 27,13 30,13 C33,13 36,16 36,20" fill="none" stroke="#483EB6" strokeWidth={2.6} strokeLinecap="round" />
      <path d="M20,22 H40 L44,38 C45,43 41,48 35,48 H25 C19,48 15,43 16,38 Z" fill="#483EB6" />
      <circle cx={30} cy={34} r={6} fill="none" stroke="#C0C6FF" strokeWidth={2} />
      <line x1={30} y1={30} x2={30} y2={38} stroke="#C0C6FF" strokeWidth={2} strokeLinecap="round" />
    </svg>
  );
}

export function PlaceMarket({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#B3D5B9" />
      <path d="M16,26 H44 L40,44 C39.5,46.3 37.5,48 35,48 H25 C22.5,48 20.5,46.3 20,44 Z" fill="#156631" />
      <path d="M23,26 C23,20 26,16 30,16 C34,16 37,20 37,26" fill="none" stroke="#156631" strokeWidth={2.6} strokeLinecap="round" />
      <path d="M22,32 H38 M23,38 H37" fill="none" stroke="#B3D5B9" strokeWidth={1.8} opacity={0.7} />
    </svg>
  );
}

export function PlaceBusStop({ size = 24, ...rest }: PlaceIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#C0CCDD" />
      <rect x={14} y={18} width={32} height={22} rx={5} fill="#253142" />
      <rect x={18} y={22} width={9} height={8} rx={1.5} fill="#C0CCDD" />
      <rect x={33} y={22} width={9} height={8} rx={1.5} fill="#C0CCDD" />
      <circle cx={21} cy={43} r={3.4} fill="#253142" />
      <circle cx={39} cy={43} r={3.4} fill="#253142" />
    </svg>
  );
}

const ITUNDAFACE_PLACES: Record<string, (props: PlaceIconProps) => ReturnType<typeof PlaceRestaurant>> = {
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

/** Real category-to-glyph lookup, matching Android's PlaceGlyph fallback exactly
 * (a plain indigo dot for anything outside the 13 known categories). */
export function PlaceGlyph({ category, size = 24, ...rest }: PlaceIconProps & { category: string }) {
  const Icon = ITUNDAFACE_PLACES[category];
  if (Icon) return <Icon size={size} {...rest} />;
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" {...rest}>
      <PlaceBadge color="#C0C6FF" />
      <circle cx={30} cy={24} r={8} fill="#483EB6" />
    </svg>
  );
}
