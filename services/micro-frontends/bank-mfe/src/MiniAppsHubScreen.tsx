import { useEffect, useState, type ReactNode } from 'react';
import { IconBack } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import { SearchAndCategoryChips } from './BankDashboard';
import { fetchMiniAppCatalog, MINI_APP_CATEGORIES, type PartnerMiniApp } from './lib/partners';
import { ApiError } from './lib/api';

// Real Toss/Kakao mini-app-store reference (2026-09-11, 7 real Kakao 미니앱
// screenshots) -- the real partner mini-app catalog (rw.itunda.partners) used
// to be a small, buried "Load more" widget inside MyView.tsx's My tab. This is
// its own dedicated screen now (MyView.tsx keeps a 3-item capped teaser + a
// "See all" row that opens this), matching the exact
// AccountDetailScreen.tsx "full-screen drill-in" navigation pattern already
// established for SavingsView -- position: fixed full-screen overlay, an
// onBack prop, own real data fetch (not prop-drilled).
//
// Search is client-side over the fetched (category-filtered) catalog -- the
// real catalog is genuinely tiny today (no seed data, no real onboarded
// partners), so real backend full-text search infra would be building ahead
// of real need. Games/point-earning mini-games from the reference are
// explicitly out of scope -- itunda has no real backing data for either; see
// docs/DESIGN_REFERENCES.md for the full account of what was and wasn't built
// this pass.
export function MiniAppsHubScreen({ onBack }: { onBack: () => void }) {
  const [miniApps, setMiniApps] = useState<PartnerMiniApp[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');

  useEffect(() => {
    setError(null);
    fetchMiniAppCatalog(selectedCategory)
      .then(setMiniApps)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load mini apps.'));
  }, [selectedCategory]);

  const query = searchInput.trim().toLowerCase();
  const filtered = (miniApps ?? []).filter(
    (app) => !query || app.name.toLowerCase().includes(query) || app.description.toLowerCase().includes(query),
  );

  let listContent: ReactNode;
  if (miniApps === null) {
    listContent = <div className="itunda-flat-section skeleton" style={{ height: '200px' }} />;
  } else if (filtered.length === 0) {
    listContent = <EmptyState message="No mini apps match yet -- try a different category or search term." />;
  } else {
    listContent = (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {filtered.map((app) => (
          <div key={app.id} style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
            {app.iconUrl ? (
              <img src={app.iconUrl} alt="" style={{ width: '36px', height: '36px', borderRadius: '8px', flexShrink: 0 }} />
            ) : (
              <div style={{ width: '36px', height: '36px', borderRadius: '8px', backgroundColor: 'var(--itunda-grey-100)', flexShrink: 0 }} />
            )}
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{app.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{app.description}</p>
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px' }}>
        <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
          <IconBack size={24} color="var(--itunda-grey-900)" />
        </button>
        <h1 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>Mini apps</h1>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 24px' }}>
        <SearchAndCategoryChips
          searchInput={searchInput}
          onSearchChange={setSearchInput}
          placeholder="Search mini apps"
          categories={MINI_APP_CATEGORIES}
          selectedCategory={selectedCategory}
          onSelectCategory={setSelectedCategory}
        />
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '16px' }} role="alert">{error}</p>}
        {listContent}
      </div>
    </div>
  );
}
