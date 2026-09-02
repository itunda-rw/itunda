import { useEffect, useState } from 'react';
import { ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { HeartOutline } from './icons/ItundaFaceHearts';
import { BellGlyph, WrenchGlyph } from './icons/ItundaFaceMisc';
import { ApiError, getStoredUser } from './lib/api';
import { addListingFavorite, fetchListings, fetchListingsMyNeighborhood, fetchMarketplaceCategories, fetchMyFavoriteListings, fetchMyListings, fetchMyPurchases, removeListingFavorite, type Listing, type TrustScores } from './lib/marketplace';
import { fetchProfile } from './lib/neighborhood';
import { NewListingCard, ListingWishlistView, KeywordAlertsView } from './HoodMarketplaceCards';
import { VehicleInspectionsView } from './VehicleInspectionsView';
import { ListingCard } from './HoodListingCard';
import { NeighborhoodSetupPrompt, NeighborhoodSwitcherRow } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

export function MarketplaceView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend ListingRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'PURCHASES' | 'NEIGHBORHOOD' | 'WISHLIST' | 'ALERTS' | 'INSPECTIONS'>('BROWSE');
  const [listings, setListings] = useState<Listing[] | null>(null);
  const showListingsSkeleton = useDeferredLoading(listings === null);
  // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
  const [trustScores, setTrustScores] = useState<TrustScores>({});
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);
  const currentUser = getStoredUser();
  // Real Karrot 중고거래 category taxonomy (2026-08-16) -- see backend
  // MarketplaceService.CATEGORIES's own doc comment. Only meaningful in BROWSE (the
  // other views have their own real scoping already -- a specific neighborhood, "my
  // own listings", etc -- layering a second filter on top would be noise).
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  useEffect(() => {
    fetchMarketplaceCategories().then(setCategories).catch(() => {
      // Real, non-critical -- browsing without category chips still works.
    });
  }, []);

  const loadFavoriteIds = () => {
    fetchMyFavoriteListings().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.listingId)))).catch(() => {
      // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
    });
  };

  const load = () => {
    setError(null);
    setListings(null);
    loadFavoriteIds();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchListingsMyNeighborhood()])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setListings(result.listings);
          setTrustScores(result.trustScores);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setListings([]);
          } else {
            setError(err instanceof ApiError ? err.message : t('common.loadError'));
          }
        });
      return;
    }
    if (view === 'WISHLIST' || view === 'ALERTS' || view === 'INSPECTIONS') return;
    const fetcher = view === 'BROWSE' ? fetchListings(selectedCategory ?? undefined) : view === 'PURCHASES' ? fetchMyPurchases() : fetchMyListings();
    fetcher
      .then((result) => {
        setListings(result.listings);
        setTrustScores(result.trustScores);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [view, selectedCategory]);

  // Real Marketplace listing wishlist (2026-07-21) -- mirrors Shop's own product
  // wishlist toggle (ProductCatalogView.toggleFavorite) field-for-field.
  const toggleFavorite = async (listingId: string) => {
    setFavoritingId(listingId);
    try {
      if (favoriteIds.has(listingId)) {
        await removeListingFavorite(listingId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(listingId); return next; });
      } else {
        await addListingFavorite(listingId);
        setFavoriteIds((prev) => new Set(prev).add(listingId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setFavoritingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE', 'PURCHASES', 'WISHLIST', 'ALERTS', 'INSPECTIONS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : v === 'MINE' ? 'My listings' : v === 'PURCHASES' ? 'Purchases' : v === 'WISHLIST' ? <><HeartOutline size={12} /> Wishlist</> : v === 'ALERTS' ? <><BellGlyph size={12} /> Alerts</> : <><WrenchGlyph size={12} /> Inspections</>}
          </button>
        ))}
      </div>

      {view === 'BROWSE' && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px', marginBottom: '14px' }}>
          <button
            onClick={() => setSelectedCategory(null)}
            style={{
              flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: selectedCategory === null ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: selectedCategory === null ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
            }}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c}
              onClick={() => setSelectedCategory(c === selectedCategory ? null : c)}
              style={{
                flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                color: selectedCategory === c ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                backgroundColor: selectedCategory === c ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
              }}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      {view === 'WISHLIST' ? (
        <ListingWishlistView />
      ) : view === 'ALERTS' ? (
        <KeywordAlertsView />
      ) : view === 'INSPECTIONS' ? (
        <VehicleInspectionsView />
      ) : (
        <>
          {view === 'MINE' && <NewListingCard onCreated={load} />}

          {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
            <NeighborhoodSetupPrompt onDone={() => load()} />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <NeighborhoodSwitcherRow
              secondNeighborhoodName={secondNeighborhoodName}
              onAddTapped={() => setShowSecondNeighborhoodPrompt(true)}
              onRemoved={(next) => setSecondNeighborhoodName(next)}
            />
          )}

          {view === 'NEIGHBORHOOD' && showSecondNeighborhoodPrompt && (
            <NeighborhoodSetupPrompt
              isSecond
              onDone={(name) => { setSecondNeighborhoodName(name); setShowSecondNeighborhoodPrompt(false); }}
            />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
              Your neighborhood: <strong style={{ color: 'var(--itunda-grey-900)' }}>{neighborhoodName}</strong>
            </p>
          )}

          {error && (
            <ErrorCard message={error} onRetry={load} />
          )}
          {!error && listings === null && showListingsSkeleton && <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />}
          {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && listings !== null && listings.length === 0 && (
            // Real copy-voice fix (item 244, round 5 of the empty-state pass --
            // docs/COPY_VOICE.md's rules, ported from the same-day Android/iOS
            // fix): say what's missing AND what fixes it, per this screen's own
            // real "+ List an item" button above in the MINE view.
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              {view === 'BROWSE' ? 'No listings yet — be the first to list something for sale.' : view === 'NEIGHBORHOOD' ? 'No listings in your neighborhood yet — try Browse to see listings from everywhere.' : view === 'PURCHASES' ? 'No purchases recorded yet — items you buy will show up here.' : 'You haven\'t listed anything yet — tap "+ List an item" above to list your first one.'}
            </p>
          )}
          {!error && listings !== null && listings.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {listings.map((listing) => (
                <ListingCard
                  key={listing.id}
                  listing={listing}
                  isMine={view === 'MINE' || listing.sellerId === currentUser?.id}
                  onChanged={load}
                  onMessageSeller={onMessageSeller}
                  favorited={favoriteIds.has(listing.id)}
                  favoriteBusy={favoritingId === listing.id}
                  onToggleFavorite={() => toggleFavorite(listing.id)}
                  sellerTrustScore={trustScores[listing.sellerId]}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

