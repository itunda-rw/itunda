import { useEffect, useState } from 'react';
import { EmptyState, ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { HeartOutline } from './icons/ItundaFaceHearts';
import { BellGlyph, WrenchGlyph } from './icons/ItundaFaceMisc';
import { ApiError, getStoredUser } from './lib/api';
import { addListingFavorite, fetchListings, fetchListingsMyNeighborhood, fetchMarketplaceCategories, fetchMyFavoriteListings, fetchMyListings, fetchMyPurchases, removeListingFavorite, type Listing, type TrustScores } from './lib/marketplace';
import { fetchProfile } from './lib/neighborhood';
import { acceptInspection, cancelInspection, completeInspection, fetchAvailableMechanics, fetchMyInspectionBookings, fetchMyMechanicBookings, fetchMyMechanicProfile, registerAsMechanic, requestInspection, setMechanicAvailability, type VehicleInspectionBooking, type VehicleInspectionMechanic } from './lib/vehicleInspection';
import { NewListingCard, ListingWishlistView, KeywordAlertsView } from './HoodMarketplaceCards';
import { ListingCard } from './HoodListingCard';
import { NeighborhoodSetupPrompt, NeighborhoodSwitcherRow } from './BankDashboard';

export function MarketplaceView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend ListingRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'PURCHASES' | 'NEIGHBORHOOD' | 'WISHLIST' | 'ALERTS' | 'INSPECTIONS'>('BROWSE');
  const [listings, setListings] = useState<Listing[] | null>(null);
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
          {!error && listings === null && <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />}
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

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
// lib/vehicleInspection.ts's own doc comment for the full sourced account. A buyer
// books and 100%-prepays a real mechanic to inspect a real used-car listing before
// purchase; a mechanic can register, browse incoming bookings, and deliver findings.
export function VehicleInspectionsView() {
  const { t } = useI18n();
  const [tab, setTab] = useState<'BUYER' | 'MECHANIC'>('BUYER');

  // Buyer side
  const [mechanics, setMechanics] = useState<VehicleInspectionMechanic[] | null>(null);
  const [myBookings, setMyBookings] = useState<VehicleInspectionBooking[] | null>(null);
  const [listingId, setListingId] = useState('');
  const [mechanicId, setMechanicId] = useState('');
  const [fee, setFee] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [buyerError, setBuyerError] = useState<string | null>(null);
  const [busyBookingId, setBusyBookingId] = useState<string | null>(null);

  const loadBuyerData = () => {
    Promise.all([fetchAvailableMechanics(), fetchMyInspectionBookings()])
      .then(([m, b]) => { setMechanics(m); setMyBookings(b); })
      .catch((err) => setBuyerError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (tab === 'BUYER') loadBuyerData();
  }, [tab]);

  const handleRequest = async () => {
    const numericFee = Number(fee);
    if (!listingId.trim() || !mechanicId || !numericFee || numericFee <= 0 || !scheduledAt) {
      setBuyerError('Fill in the listing id, a mechanic, a valid fee, and a scheduled time.');
      return;
    }
    setRequesting(true);
    setBuyerError(null);
    try {
      await requestInspection(listingId.trim(), mechanicId, numericFee, new Date(scheduledAt).toISOString());
      setListingId('');
      setMechanicId('');
      setFee('');
      setScheduledAt('');
      loadBuyerData();
    } catch (err) {
      setBuyerError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRequesting(false);
    }
  };

  const handleCancel = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await cancelInspection(bookingId);
      loadBuyerData();
    } catch (err) {
      setBuyerError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyBookingId(null);
    }
  };

  // Mechanic side
  const [mechanicProfile, setMechanicProfile] = useState<VehicleInspectionMechanic | null | undefined>(undefined);
  const [businessName, setBusinessName] = useState('');
  const [registering, setRegistering] = useState(false);
  const [mechanicBookings, setMechanicBookings] = useState<VehicleInspectionBooking[] | null>(null);
  const [mechanicError, setMechanicError] = useState<string | null>(null);
  const [findings, setFindings] = useState<Record<string, string>>({});

  const loadMechanicData = () => {
    fetchMyMechanicProfile()
      .then((m) => {
        setMechanicProfile(m);
        if (m) fetchMyMechanicBookings().then(setMechanicBookings).catch(() => {});
      })
      .catch((err) => setMechanicError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (tab === 'MECHANIC') loadMechanicData();
  }, [tab]);

  const handleRegister = async () => {
    if (!businessName.trim()) return;
    setRegistering(true);
    setMechanicError(null);
    try {
      setMechanicProfile(await registerAsMechanic(businessName.trim()));
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!mechanicProfile) return;
    try {
      setMechanicProfile(await setMechanicAvailability(!mechanicProfile.available));
    } catch {
      // Real, non-critical -- an availability toggle failure isn't worth a hard error.
    }
  };

  const handleAccept = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await acceptInspection(bookingId);
      loadMechanicData();
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyBookingId(null);
    }
  };

  const handleComplete = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await completeInspection(bookingId, findings[bookingId]);
      loadMechanicData();
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyBookingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BUYER', 'MECHANIC'] as const).map((t) => (
          <button
            key={t} onClick={() => setTab(t)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: tab === t ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: tab === t ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {t === 'BUYER' ? 'Get a car inspected' : 'Mechanic'}
          </button>
        ))}
      </div>

      {tab === 'BUYER' ? (
        <div>
          {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
              form section on this tab. */}
          <div style={{ padding: '10px 0' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Book an inspection</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
              Pay a local mechanic to inspect a used car before you buy it -- held until they deliver their findings.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <input
                type="text" value={listingId} onChange={(e) => setListingId(e.target.value)} placeholder="Listing ID"
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <select
                value={mechanicId} onChange={(e) => setMechanicId(e.target.value)}
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              >
                <option value="">Choose a mechanic</option>
                {(mechanics ?? []).map((m) => <option key={m.id} value={m.id}>{m.businessName}</option>)}
              </select>
              <input
                type="number" value={fee} onChange={(e) => setFee(e.target.value)} placeholder="Inspection fee (RWF)"
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <input
                type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <button className="itunda-btn itunda-btn-primary" disabled={requesting} onClick={handleRequest}>
                {requesting ? 'Booking…' : 'Book & pay'}
              </button>
            </div>
            {buyerError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{buyerError}</p>}
          </div>

          {myBookings === null ? (
            <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} />
          ) : myBookings.length === 0 ? (
            <EmptyState message="No inspections booked yet — book one to get a real used car checked before you buy." />
          ) : (
            // Real fix (2026-08-24, flat-design sweep): history log of booked
            // inspections, kept the per-row divider convention.
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {myBookings.map((b) => (
                <div key={b.id} className="itunda-flat-section">
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Listing {b.listingId}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{b.fee.toLocaleString()} RWF · {b.status}</p>
                  {b.findings && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginTop: '6px' }}>{b.findings}</p>}
                  {(b.status === 'REQUESTED' || b.status === 'ACCEPTED') && (
                    <button
                      className="itunda-btn itunda-btn-danger" style={{ marginTop: '8px' }} disabled={busyBookingId === b.id}
                      onClick={() => handleCancel(b.id)}
                    >
                      {busyBookingId === b.id ? 'Cancelling…' : 'Cancel'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : mechanicProfile === undefined ? (
        <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />
      ) : mechanicProfile === null ? (
        // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
        // form section shown in this state.
        <div style={{ padding: '10px 0' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Become an inspection mechanic</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
            Get booked and paid to inspect used cars for real buyers before they purchase.
          </p>
          <input
            type="text" value={businessName} onChange={(e) => setBusinessName(e.target.value)} placeholder="Business name"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={registering} onClick={handleRegister}>
            {registering ? 'Registering…' : 'Register'}
          </button>
          {mechanicError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{mechanicError}</p>}
        </div>
      ) : (
        // Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown
        // together -- reused .itunda-flat-section for the section-boundary divider.
        <div>
          <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{mechanicProfile.businessName}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{mechanicProfile.available ? 'Visible for new bookings' : 'Not accepting bookings'}</p>
            </div>
            <button className={mechanicProfile.available ? 'itunda-btn itunda-btn-danger' : 'itunda-btn itunda-btn-primary'} onClick={handleToggleAvailable}>
              {mechanicProfile.available ? 'Go unavailable' : 'Go available'}
            </button>
          </div>
          {mechanicError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{mechanicError}</p>}
          {mechanicBookings === null ? (
            <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} />
          ) : mechanicBookings.length === 0 ? (
            <EmptyState message="No bookings yet — they'll show up here once a buyer books an inspection." />
          ) : (
            // Real fix (2026-08-24, flat-design sweep): history log of mechanic
            // bookings, kept the per-row divider convention.
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {mechanicBookings.map((b) => (
                <div key={b.id} className="itunda-flat-section">
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Listing {b.listingId}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{b.fee.toLocaleString()} RWF · {b.status}</p>
                  {b.status === 'REQUESTED' && (
                    <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busyBookingId === b.id} onClick={() => handleAccept(b.id)}>
                      {busyBookingId === b.id ? 'Accepting…' : 'Accept'}
                    </button>
                  )}
                  {b.status === 'ACCEPTED' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                      <textarea
                        value={findings[b.id] ?? ''} onChange={(e) => setFindings((prev) => ({ ...prev, [b.id]: e.target.value }))}
                        placeholder="Inspection findings" rows={2}
                        style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', resize: 'vertical' }}
                      />
                      <button className="itunda-btn itunda-btn-primary" disabled={busyBookingId === b.id} onClick={() => handleComplete(b.id)}>
                        {busyBookingId === b.id ? 'Completing…' : 'Mark complete'}
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ============================== COMMUNITY (동네생활) ==============================

