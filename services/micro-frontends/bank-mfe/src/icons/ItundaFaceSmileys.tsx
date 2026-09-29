// itundaface Smileys & Emotion -- phase 1 of the "reach TossFace's 3,600-glyph
// scale" initiative (2026-08-22, direct user request: "let's [go] to 3,600-glyph
// like tossface" -> AskUserQuestion clarified the real target is full emoji-INPUT
// replacement, not just more in-app UI icons). See ItundaFaceEmoji.tsx for the
// real registry/lookup/picker infrastructure this category plugs into, and
// project_itunda_own_icons_graphics.md for the full staged category roadmap.
//
// Reuses ItundaFace.tsx's exact face template (circle cx=40 cy=40 r=34 fill
// #ffcc4d, 80x80 viewBox, #664500 linework) rather than inventing a new one --
// this is the "generative template" approach that makes approaching TossFace's
// real scale remotely realistic without an image-generation tool: one shared
// face base, distinguishing eyes/mouth/accents per glyph, matching TossFace's
// own stated rule of "one shared visual weight/style across the whole set."
// Every glyph here is Unicode's own official "Smileys & Emotion" group (the
// same category TossFace itself uses), picked for real chat-usage frequency,
// flat-only for this first pass (3D stays reserved for a genuinely prominent
// placement, per ItundaFace.tsx's own established small=flat/prominent=3D
// split -- these render at picker-grid size, not yet judged prominent enough
// to warrant it; can be revisited).

import type { SVGProps } from 'react';

type FaceIconProps = SVGProps<SVGSVGElement> & { size?: number };

function FaceBase({ size = 24, children, ...rest }: FaceIconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 80 80" {...rest}>
      {children}
    </svg>
  );
}

const FACE = <circle cx="40" cy="40" r="34" fill="#ffcc4d" />;
const LINE = '#664500';
const TEAR = '#7472f4'; // itunda's own real indigo, same signature accent as ReactionLaughing/Sad's tear.

export function SmileyGrinning(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <circle cx="26" cy="34" r="4" fill={LINE} />
      <circle cx="54" cy="34" r="4" fill={LINE} />
      <path d="M20,48 C20,48 26,64 40,64 C54,64 60,48 60,48 C60,48 54,56 40,56 C26,56 20,48 20,48 Z" fill="#66471b" />
      <path d="M25,49.5 C25,49.5 31,53.5 40,53.5 C49,53.5 55,49.5 55,49.5 L55,52.2 C55,52.2 49,55.5 40,55.5 C31,55.5 25,52.2 25,52.2 Z" fill="#ffffff" />
    </FaceBase>
  );
}

export function SmileyGrinningEyes(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <path d="M18,32 C21,26 27,26 30,32" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M50,32 C53,26 59,26 62,32" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M22,48 C22,48 28,62 40,62 C52,62 58,48 58,48 C58,48 52,55 40,55 C28,55 22,48 22,48 Z" fill="#66471b" />
    </FaceBase>
  );
}

export function SmileySlight(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <circle cx="26" cy="34" r="3.6" fill={LINE} />
      <circle cx="54" cy="34" r="3.6" fill={LINE} />
      <path d="M28,52 C32,58 48,58 52,52" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

export function SmileyWink(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <circle cx="26" cy="34" r="4" fill={LINE} />
      <path d="M48,32 C51,36 57,36 60,32" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M26,50 C30,58 50,58 54,50" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

function HeartEye({ cx, cy }: { cx: number; cy: number }) {
  return (
    <path
      d={`M${cx},${cy - 2.4} C${cx + 1.2},${cy - 4.8} ${cx + 3.6},${cy - 6} ${cx + 5.6},${cy - 4.6} C${cx + 7.6},${cy - 3.2} ${cx + 7.6},${cy - 0.4} ${cx + 5.6},${cy + 1.6} C${cx + 4},${cy + 3.2} ${cx},${cy + 5.6} ${cx},${cy + 5.6} C${cx},${cy + 5.6} ${cx - 4},${cy + 3.2} ${cx - 5.6},${cy + 1.6} C${cx - 7.6},${cy - 0.4} ${cx - 7.6},${cy - 3.2} ${cx - 5.6},${cy - 4.6} C${cx - 3.6},${cy - 6} ${cx - 1.2},${cy - 4.8} ${cx},${cy - 2.4} Z`}
      fill="#ef4a63"
    />
  );
}

export function SmileyHeartEyes(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <HeartEye cx={26} cy={33} />
      <HeartEye cx={54} cy={33} />
      <path d="M26,50 C30,58 50,58 54,50" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

export function SmileyKissHeart(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <path d="M18,32 C21,26 27,26 30,32" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <circle cx="54" cy="34" r="4" fill={LINE} />
      <ellipse cx="38" cy="54" rx="5" ry="4" fill="#c94f63" />
      <path d="M62,38 C63.5,35.6 66.6,34.7 69,36.2 C71.4,37.7 72.1,41 70.6,43.4 C68.6,46.6 62,50 62,50 C62,50 60.8,42.6 62,38 Z" fill="#ef4a63" />
    </FaceBase>
  );
}

export function SmileySleeping(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <path d="M19,34 C22,31.4 28,31.4 31,34" stroke={LINE} strokeWidth={4} strokeLinecap="round" fill="none" />
      <path d="M49,34 C52,31.4 58,31.4 61,34" stroke={LINE} strokeWidth={4} strokeLinecap="round" fill="none" />
      <path d="M32,54 C35,56.4 45,56.4 48,54" stroke={LINE} strokeWidth={3.6} strokeLinecap="round" fill="none" />
      <path d="M56,16 L64,16 L55,25 L64,25" stroke={LINE} strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <path d="M64,10 L70,10 L63,17 L70,17" stroke={LINE} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" opacity={0.7} />
    </FaceBase>
  );
}

export function SmileyLoudlyCrying(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <path d="M19,35 C22,31 28,31 31,35" stroke={LINE} strokeWidth={4.2} strokeLinecap="round" fill="none" />
      <path d="M49,35 C52,31 58,31 61,35" stroke={LINE} strokeWidth={4.2} strokeLinecap="round" fill="none" />
      <ellipse cx="40" cy="58" rx="11" ry="9" fill="#5c3d15" />
      <path d="M23,36 C23,36 21,50 15,58 C15,58 22,55 25,60" stroke={TEAR} strokeWidth={4.4} strokeLinecap="round" fill="none" />
      <path d="M57,36 C57,36 59,50 65,58 C65,58 58,55 55,60" stroke={TEAR} strokeWidth={4.4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

export function SmileyAngry(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <path d="M18,29 L32,34" stroke="#7a4d15" strokeWidth={4.4} strokeLinecap="round" />
      <path d="M62,29 L48,34" stroke="#7a4d15" strokeWidth={4.4} strokeLinecap="round" />
      <circle cx="27" cy="37" r="3.6" fill={LINE} />
      <circle cx="53" cy="37" r="3.6" fill={LINE} />
      <path d="M28,58 C32,53 48,53 52,58" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

export function SmileyCool(props: FaceIconProps) {
  return (
    <FaceBase {...props}>
      {FACE}
      <path d="M14,30 H66 V33 C66,33 60,30 40,30 C20,30 14,33 14,33 Z" fill="#20242c" />
      <rect x="15" y="30" width="21" height="12" rx="6" fill="#20242c" />
      <rect x="44" y="30" width="21" height="12" rx="6" fill="#20242c" />
      <ellipse cx="21" cy="34" rx="4" ry="2.6" fill="#ffffff" opacity={0.35} />
      <ellipse cx="50" cy="34" rx="4" ry="2.6" fill="#ffffff" opacity={0.35} />
      <path d="M28,52 C32,58 48,58 52,52" stroke={LINE} strokeWidth={4.4} strokeLinecap="round" fill="none" />
    </FaceBase>
  );
}

/** Registry: real Unicode codepoint (the same character a system emoji picker or
 * keyboard would insert) -> itundaface glyph. This is the actual set consumed by
 * ItundaFaceEmoji.tsx's lookup/render/picker pipeline -- adding a new glyph here
 * is the whole integration step, no other file needs to change. */
export const ITUNDAFACE_SMILEYS: Record<string, (props: FaceIconProps) => React.ReactElement> = {
  '😀': SmileyGrinning,
  '😄': SmileyGrinningEyes,
  '🙂': SmileySlight,
  '😉': SmileyWink,
  '😍': SmileyHeartEyes,
  '😘': SmileyKissHeart,
  '😴': SmileySleeping,
  '😭': SmileyLoudlyCrying,
  '😡': SmileyAngry,
  '😎': SmileyCool,
};
