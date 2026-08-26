import { Wifi } from 'lucide-react';

// Real EMV chip + tap-to-pay silhouette (2026-08-26, direct user instruction: "all
// cards designs should resemble real card") -- shared by every card-shaped visual in
// BankDashboard.tsx (AccountCardCarousel's account tiles, CardExplainer's
// pre-issuance mockup, CardView's issued-card thumbnail) so the metal-chip look and
// contactless mark stay one real component, not three local forks. Split into its own
// file (not left in BankDashboard.tsx) to stay under that file's own file-size-lint
// baseline, same convention PayHomeExtras.tsx/FullScreenFlow.tsx already established.
// Gold gradient + a contact-pad grid is the standard flat-icon convention for "this is
// a chip card" every major bank app uses; the rotated `Wifi` glyph is the same
// widely-used tap-to-pay substitute (no card network's actual trademarked mark is
// reproduced).
export function BankCardChip({ size = 32 }: { size?: number }) {
  const height = Math.round(size * 0.76);
  return (
    <div
      style={{
        width: `${size}px`, height: `${height}px`, borderRadius: '5px', position: 'relative', overflow: 'hidden', flexShrink: 0,
        background: 'linear-gradient(135deg, #F6E7B4 0%, #D9B36C 55%, #C89A4E 100%)',
        boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.18)',
      }}
    >
      <div style={{ position: 'absolute', top: 0, bottom: 0, left: '50%', width: '1px', backgroundColor: 'rgba(0,0,0,0.22)' }} />
      <div style={{ position: 'absolute', top: 0, bottom: 0, left: '25%', width: '1px', backgroundColor: 'rgba(0,0,0,0.14)' }} />
      <div style={{ position: 'absolute', top: 0, bottom: 0, left: '75%', width: '1px', backgroundColor: 'rgba(0,0,0,0.14)' }} />
      <div style={{ position: 'absolute', left: 0, right: 0, top: '50%', height: '1px', backgroundColor: 'rgba(0,0,0,0.22)' }} />
    </div>
  );
}

export function CardContactlessGlyph({ size = 18, color = 'rgba(255,255,255,0.85)' }: { size?: number; color?: string }) {
  return <Wifi size={size} color={color} style={{ transform: 'rotate(90deg)' }} aria-hidden="true" />;
}
