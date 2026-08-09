// Real Coupang badge system (2026-08-05) -- see docs/DESIGN_REFERENCES.md Section 9's
// own account: a two-tier delivery badge tied to a real, named benefit tier, not a
// decorative label. Ports Android's own StatusBadge (core/designsystem/components/
// HoodShared.kt) / iOS's own IdsBadge (Core/DesignSystem/Sources/Components/
// Components.swift) to merchant-mfe -- KYB verification status here rendered as plain
// colored <p> text, no badge shape.
export function Badge({ text, filled = true, tint = 'var(--itunda-blue)' }: { text: string; filled?: boolean; tint?: string }) {
  return (
    <span
      style={{
        display: 'inline-block',
        fontSize: '11px',
        fontWeight: 700,
        padding: '2px 6px',
        borderRadius: '6px',
        color: filled ? '#fff' : tint,
        backgroundColor: filled ? tint : `color-mix(in srgb, ${tint} 12%, transparent)`,
      }}
    >
      {text}
    </span>
  );
}
