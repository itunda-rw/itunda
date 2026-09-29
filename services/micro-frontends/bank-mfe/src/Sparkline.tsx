// Extracted from StocksView.tsx (2026-08-30, keeping that file under the 500-line
// guideline after StockDetailSheet.tsx's own extraction) -- shared by both
// StockDetailSheet and StocksView's own portfolio chart.
//
// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app yet, and pulling one in just for this would be disproportionate to a real
// MVP chart. Real values, real relative scaling, just rendered as flexbox bars instead
// of an SVG line chart.
export function Sparkline({ values, positive }: { values: number[]; positive: boolean }) {
  if (values.length === 0) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height: '48px' }}>
      {values.map((v, i) => (
        <div
          key={i}
          style={{
            flex: 1,
            height: `${Math.max(8, ((v - min) / range) * 100)}%`,
            backgroundColor: positive ? 'var(--itunda-green)' : 'var(--itunda-red)',
            borderRadius: '2px',
            opacity: 0.3 + (0.7 * i) / values.length,
          }}
        />
      ))}
    </div>
  );
}
