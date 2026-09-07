import { useEffect, useState } from 'react';
import { ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { HeartOutline } from './icons/ItundaFaceHearts';
import { ApiError, getStoredUser } from './lib/api';
import { type TrustScores } from './lib/marketplace';
import { fetchProfile } from './lib/neighborhood';
import { addPropertyListingFavorite, contactLister, fetchMyAcquiredPropertyListings, fetchMyFavoritePropertyListings, fetchMyPropertyListings, fetchPropertyListings, fetchPropertyListingsMyNeighborhood, fetchPropertyTypes, fetchPropertyValuation, removePropertyListingFavorite, type PropertyListing, type PropertyListingType, type PropertyType, type PropertyValuationEstimate } from './lib/realestate';
import { NewPropertyListingCard, PropertyListingWishlistView } from './HoodPropertyCards';
import { PropertyListingCard } from './HoodPropertyListingCard';
import { useDeferredLoading } from './useDeferredLoading';
import { NeighborhoodSetupPrompt, NeighborhoodSwitcherRow } from './BankDashboard';

export function PropertyView({ onMessageLister }: { onMessageLister: (conversationId: string) => void }) {
  const { t } = useI18n();
  // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend PropertyListingRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'ACQUIRED' | 'NEIGHBORHOOD' | 'WISHLIST' | 'VALUATION'>('BROWSE');
  const [propertyTypes, setPropertyTypes] = useState<PropertyType[]>([]);
  const [listingTypeFilter, setListingTypeFilter] = useState<PropertyListingType | null>(null);
  const [propertyTypeFilter, setPropertyTypeFilter] = useState<string | null>(null);
  const [listings, setListings] = useState<PropertyListing[] | null>(null);
  const showSkeleton = useDeferredLoading(listings === null);
  // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
  const [trustScores, setTrustScores] = useState<TrustScores>({});
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchPropertyTypes().then(setPropertyTypes).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const loadFavoriteIds = () => {
    fetchMyFavoritePropertyListings().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.propertyListingId)))).catch(() => {
      // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
    });
  };

  const load = () => {
    setError(null);
    setListings(null);
    loadFavoriteIds();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchPropertyListingsMyNeighborhood()])
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
    if (view === 'WISHLIST' || view === 'VALUATION') return;
    const fetcher = view === 'BROWSE'
      ? fetchPropertyListings(listingTypeFilter ?? undefined, propertyTypeFilter ?? undefined)
      : view === 'ACQUIRED'
        ? fetchMyAcquiredPropertyListings()
        : fetchMyPropertyListings();
    fetcher
      .then((result) => { setListings(result.listings); setTrustScores(result.trustScores); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [view, listingTypeFilter, propertyTypeFilter]);

  const propertyTypeLabel = (id: string) => propertyTypes.find((t) => t.id === id)?.label ?? id;

  const handleContact = async (propertyListingId: string) => {
    try {
      const conversation = await contactLister(propertyListingId);
      onMessageLister(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  // Real 당근부동산 property-listing wishlist (2026-07-22) -- mirrors MarketplaceView's
  // own toggleFavorite field-for-field.
  const toggleFavorite = async (propertyListingId: string) => {
    setFavoritingId(propertyListingId);
    try {
      if (favoriteIds.has(propertyListingId)) {
        await removePropertyListingFavorite(propertyListingId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(propertyListingId); return next; });
      } else {
        await addPropertyListingFavorite(propertyListingId);
        setFavoriteIds((prev) => new Set(prev).add(propertyListingId));
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
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE', 'ACQUIRED', 'WISHLIST', 'VALUATION'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : v === 'MINE' ? 'My listings' : v === 'ACQUIRED' ? 'Places I got' : v === 'WISHLIST' ? <><HeartOutline size={12} /> Wishlist</> : '시세 Value'}
          </button>
        ))}
      </div>

      {view === 'WISHLIST' ? (
        <PropertyListingWishlistView />
      ) : view === 'VALUATION' ? (
        <PropertyValuationCard propertyTypes={propertyTypes} />
      ) : (
        <>
          {view === 'BROWSE' && (
            <div style={{ display: 'flex', gap: '6px', marginBottom: '10px' }}>
              {(['RENT', 'SALE'] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setListingTypeFilter(listingTypeFilter === t ? null : t)}
                  style={{
                    padding: '6px 12px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                    border: `1px solid ${listingTypeFilter === t ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)'}`,
                    color: listingTypeFilter === t ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                    backgroundColor: listingTypeFilter === t ? 'var(--itunda-indigo)' : 'transparent',
                  }}
                >
                  {t === 'RENT' ? 'For rent' : 'For sale'}
                </button>
              ))}
            </div>
          )}

          {view === 'BROWSE' && propertyTypes.length > 0 && (
            <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
              {propertyTypes.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setPropertyTypeFilter(propertyTypeFilter === t.id ? null : t.id)}
                  style={{
                    whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                    border: `1px solid ${propertyTypeFilter === t.id ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)'}`,
                    color: propertyTypeFilter === t.id ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                    backgroundColor: propertyTypeFilter === t.id ? 'var(--itunda-indigo)' : 'transparent',
                  }}
                >
                  {t.label}
                </button>
              ))}
            </div>
          )}

          {view === 'MINE' && <NewPropertyListingCard propertyTypes={propertyTypes} onCreated={load} />}

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
          {!error && listings === null && showSkeleton && <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />}
          {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && listings !== null && listings.length === 0 && (
            // Real copy-voice fix (item 244, round 5 of the empty-state pass,
            // ported from the same-day Android/iOS fix): say what's missing AND
            // what fixes it, per this screen's own real "+ List a property"
            // button above in the MINE view.
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              {view === 'BROWSE' ? 'No properties listed yet — check back soon, or list your own.' : view === 'NEIGHBORHOOD' ? 'No properties in your neighborhood yet — try Browse to see properties from everywhere.' : view === 'ACQUIRED' ? 'No properties acquired yet — properties you acquire will show up here.' : 'You haven\'t listed any properties yet — tap "+ List a property" above to list your first one.'}
            </p>
          )}
          {!error && listings !== null && listings.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {listings.map((listing) => (
                <PropertyListingCard
                  key={listing.id}
                  listing={listing}
                  propertyTypeLabel={propertyTypeLabel(listing.propertyType)}
                  isMine={view === 'MINE' || listing.listerId === currentUser?.id}
                  onChanged={load}
                  onContact={() => handleContact(listing.id)}
                  onMessageLister={onMessageLister}
                  favorited={favoriteIds.has(listing.id)}
                  favoriteBusy={favoritingId === listing.id}
                  onToggleFavorite={() => toggleFavorite(listing.id)}
                  listerTrustScore={trustScores[listing.listerId]}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see
// lib/realestate.ts's own doc comment. Read-only: enter a location + size, get a real
// comparable-listings-based estimate, nothing persisted.
export function PropertyValuationCard({ propertyTypes }: { propertyTypes: PropertyType[] }) {
  const { t } = useI18n();
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [propertyType, setPropertyType] = useState('');
  const [listingType, setListingType] = useState<PropertyListingType>('SALE');
  const [sizeSqm, setSizeSqm] = useState('');
  const [estimate, setEstimate] = useState<PropertyValuationEstimate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const useMyLocation = () => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition((pos) => {
      setLatitude(String(pos.coords.latitude));
      setLongitude(String(pos.coords.longitude));
    });
  };

  const handleEstimate = async () => {
    const lat = Number(latitude);
    const lng = Number(longitude);
    const size = Number(sizeSqm);
    if (!propertyType || Number.isNaN(lat) || Number.isNaN(lng) || Number.isNaN(size) || size <= 0) {
      setError('Fill in a real location, property type, and size.');
      return;
    }
    setLoading(true);
    setError(null);
    setEstimate(null);
    try {
      const result = await fetchPropertyValuation(lat, lng, propertyType, listingType, size);
      setEstimate(result);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INSUFFICIENT_COMPARABLES') {
        setError(err.message);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '4px' }}>우리집 시세 — Estimate my home's value</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
        A real estimate based on comparable listings near you, not a fabricated number.
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="text" value={latitude} placeholder={t('hood.property.latitudePlaceholder')} onChange={(e) => setLatitude(e.target.value)}
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" value={longitude} placeholder={t('hood.property.longitudePlaceholder')} onChange={(e) => setLongitude(e.target.value)}
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button className="itunda-btn itunda-btn-secondary" onClick={useMyLocation} style={{ padding: '10px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}>
            📍
          </button>
        </div>
        <select
          value={propertyType} onChange={(e) => setPropertyType(e.target.value)}
          style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        >
          <option value="">Property type…</option>
          {propertyTypes.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
        <div style={{ display: 'flex', gap: '8px' }}>
          {(['SALE', 'RENT'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setListingType(t)}
              style={{
                flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                border: `1px solid ${listingType === t ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)'}`,
                color: listingType === t ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                backgroundColor: listingType === t ? 'var(--itunda-indigo)' : 'transparent',
              }}
            >
              {t === 'SALE' ? 'For sale' : 'For rent'}
            </button>
          ))}
        </div>
        <input
          type="text" value={sizeSqm} placeholder={t('hood.property.sizeSqmPlaceholder')} onChange={(e) => setSizeSqm(e.target.value)}
          style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        <button className="itunda-btn itunda-btn-primary" disabled={loading} onClick={handleEstimate}>
          {loading ? 'Estimating…' : 'Estimate value'}
        </button>
        {estimate && (
          <div style={{ marginTop: '8px', padding: '12px', borderRadius: '10px', backgroundColor: 'var(--itunda-grey-100)' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-24-size)', fontWeight: 700 }}>{estimate.estimatedValue.toLocaleString('en-US')} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              Based on {estimate.comparableCount} comparable listing{estimate.comparableCount === 1 ? '' : 's'} within {estimate.radiusKm}km ({estimate.averagePricePerSqm.toLocaleString('en-US')} RWF/sqm avg)
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
