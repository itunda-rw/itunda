// Real Links tab (itunda Talk redesign, 2026-08-28) -- deliberately Links only, NOT a
// general Files tab: no file-attachment-in-chat capability exists anywhere in this
// codebase today (only images, via imageUrl), so promising a Files tab here would be
// fabricating a capability that doesn't exist. Split into its own file, same
// file-size-lint-driven convention BankCardChip.tsx/MediaGalleryModal already
// established (a bottom-sheet modal, mirroring MediaGalleryModal's own exact shape).
// extractLinks itself lives in lib/talk.ts, not here -- a component file exporting a
// plain function trips this codebase's react/only-export-components fast-refresh rule.

export function TalkLinksModal({ links, onClose }: { links: string[]; onClose: () => void }) {
  return (
    <div
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        style={{ background: 'var(--itunda-white)', borderRadius: '16px 16px 0 0', padding: '16px', width: '100%', maxHeight: '70vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '12px' }}>Links ({links.length})</h3>
        {links.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>No links shared in this conversation yet.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {links.map((url, i) => (
              <a
                key={i}
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                style={{
                  fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)', wordBreak: 'break-all',
                  padding: '10px 12px', backgroundColor: 'var(--itunda-grey-50)', borderRadius: 'var(--itunda-radius-sm)',
                }}
              >
                {url}
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
