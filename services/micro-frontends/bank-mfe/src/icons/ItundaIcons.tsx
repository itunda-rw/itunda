// itunda's own icon set (2026-08-22, direct user identity work: "we need to work on
// our own icons, emojis and graphics as toss did") -- real research first
// (docs/DESIGN_REFERENCES.md): TDS's own icon/illustration foundation isn't publicly
// documented (unlike its colors/typography, which are), but Toss's real design blog
// (toss.tech/article/ai-graphic-generator-1) states their graphic philosophy
// directly: "가장 필요한 것만 예쁘게 남긴 상태" -- keep only what's essential, made
// beautiful. itunda's entire icon system before this was 100% lucide-react (a
// generic, off-the-shelf stroke-icon library, imported directly with zero
// itunda-specific styling) across every one of its ~40 distinct icons in
// BankDashboard.tsx alone -- functional, but with no more brand identity than any
// other app built on the same open-source kit.
//
// First slice: the 5 primary-tab icons (Home/Pay/Explore/Messages/You) -- the
// single most-repeated visual touchpoint in the whole app, present on every screen,
// same rationale that made the app-icon mark itself (see
// project_itunda_brand_identity.md) worth the highest design investment per line of
// code. Not a full icon-library replacement (~40 other Lucide icons in
// BankDashboard.tsx are untouched) -- that's real, separately-scoped follow-up work,
// same "small well-chosen first slice, not a mechanical sweep" discipline this
// session's other identity work (Toast, indigo rebrand) already followed.
//
// Construction: 24x24 viewBox, 2.4px stroke (vs. Lucide's default 2px -- a modest,
// deliberate bump for a bolder, more "designed" presence, verified legible at the
// real 18px deployed size via rsvg-convert before shipping), rounded caps/joins
// throughout, generous corner rounding on every rectilinear shape -- echoing the
// same soft, rounded language the "petal" brand mark uses, without borrowing its
// exact geometry (a literal petal-shaped house icon would be illegible, not
// branded). `stroke="currentColor"`, no hardcoded color, so these drop in as
// same-shape replacements for the lucide-react API already used at each call site
// (`<Icon size={18} />`, color inherited from the parent's own `color` style).

import type { SVGProps } from 'react';

type ItundaIconProps = SVGProps<SVGSVGElement> & { size?: number };

function IconBase({ size = 24, children, ...rest }: ItundaIconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.4}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...rest}
    >
      {children}
    </svg>
  );
}

export function IconHome(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <path d="M2,11 L11,3.2 C11.6,2.7 12.4,2.7 13,3.2 L22,11" />
      <path d="M5,9 V19.2 C5,19.6 5.4,20 5.8,20 H18.2 C18.6,20 19,19.6 19,19.2 V9" />
      <path d="M9.5,20 V14.5 C9.5,13.7 10.2,13 11,13 H13 C13.8,13 14.5,13.7 14.5,14.5 V20" />
    </IconBase>
  );
}

export function IconPay(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <rect x="2" y="4.5" width="20" height="15" rx="3.2" />
      <path d="M2,9.5 H22" strokeLinecap="butt" />
      <path d="M5.5,15 H10.5" />
    </IconBase>
  );
}

export function IconExplore(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <rect x="2" y="2" width="8.6" height="8.6" rx="2.4" />
      <rect x="13.4" y="2" width="8.6" height="8.6" rx="2.4" />
      <rect x="2" y="13.4" width="8.6" height="8.6" rx="2.4" />
      <rect x="13.4" y="13.4" width="8.6" height="8.6" rx="2.4" />
    </IconBase>
  );
}

export function IconMessages(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <path d="M2,5.5 C2,3.6 3.6,2 5.5,2 H18.5 C20.4,2 22,3.6 22,5.5 V13.5 C22,15.4 20.4,17 18.5,17 H10 L5,21 V17 H5.5 C3.6,17 2,15.4 2,13.5 Z" />
    </IconBase>
  );
}

export function IconYou(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="7.5" r="5" />
      <path d="M3,21 C3,15.5 7,13 12,13 C17,13 21,15.5 21,21" />
    </IconBase>
  );
}

// Phase 2 (2026-08-24, direct user follow-up: "let's search how toss archived
// [cross-platform consistency] ... let's itunda look the same across all three
// platforms as toss do") -- real research: Toss's own TDS icon/illustration system
// is private (confirmed by checking tossmini-docs.toss.im's own nav -- only Colors/
// Typography have public Foundation pages), but the generalizable lesson from their
// engineering blog is architectural: one custom icon set exported natively per
// platform, not each platform reaching for its own stock library. Confirmed that's
// itunda's real gap by auditing all 3 platforms directly: web uses lucide-react,
// Android uses Material Icons (`Icons.Outlined.X`), iOS uses SF Symbols
// (`Image(systemName:)`) -- the exact same UI concept (e.g. "back") renders as 3
// visually different glyphs today. This phase covers the 5 highest-value, most
// universal navigation/action concepts, prioritized by REAL combined cross-platform
// usage frequency (not guessed): Back (~90 combined uses -- Android's ArrowBackIosNew
// +ArrowBack, iOS's chevron.left alone at 55(!), web's ArrowLeft+ChevronLeft),
// ChevronRight (~29), Close (~14), Search (~12), Add (~11). Same 24x24/2.4px-stroke
// construction as phase 1, verified via rsvg-convert at both full size and the real
// 18px deployed size before shipping. Same shape ported byte-identical to Android
// (IdsIcons.kt, PathParser) and iOS (IDS.Icons, the existing ItundaFaceCanvas SVG
// path parser) in the same pass -- all 3 platforms render the literal same path data.
export function IconBack(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <path d="M15,4 L7,12 L15,20" />
    </IconBase>
  );
}

export function IconChevronRight(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <path d="M9,4 L17,12 L9,20" />
    </IconBase>
  );
}

export function IconClose(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <path d="M5,5 L19,19" />
      <path d="M19,5 L5,19" />
    </IconBase>
  );
}

export function IconSearch(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <circle cx="10.5" cy="10.5" r="7" />
      <path d="M20,20 L15.3,15.3" />
    </IconBase>
  );
}

export function IconAdd(props: ItundaIconProps) {
  return (
    <IconBase {...props}>
      <path d="M12,4 V20" />
      <path d="M4,12 H20" />
    </IconBase>
  );
}
