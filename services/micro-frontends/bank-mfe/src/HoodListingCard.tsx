import { useEffect, useState } from 'react';
import RouteMiniMap from './RouteMiniMap';
import { useI18n } from './i18n/I18nContext';
import { LockGlyph } from './icons/ItundaFaceSecurity';
import { ApiError, getStoredUser } from './lib/api';
import { boostListing, bumpListing, confirmEscrowReceipt, contactSeller, disputeEscrow, fetchBoostTiers, fetchListingDetail, fetchListingReviews, getEscrow, hideListing, makeOffer, markListingSold, payEscrow, removeListing, submitListingReview, type HoodReview, type Listing, type MarketplaceEscrow, updateListingPrice } from './lib/marketplace';
import { HoodReportButton, HoodReviewForm, HoodReviewResultView, TrustBadge, WishlistButton } from './BankDashboard';
import { VehicleDetailSection } from './HoodVehicleFields';
import { HoodListingSellerForms } from './HoodListingSellerForms';
import { HoodListingEscrowStatus } from './HoodListingEscrowStatus';

export function ListingCard({ listing, isMine, onChanged, onMessageSeller, favorited, favoriteBusy, onToggleFavorite, sellerTrustScore }: {
  listing: Listing;
  isMine: boolean;
  onChanged: () => void;
  onMessageSeller: (conversationId: string) => void;
  favorited: boolean;
  favoriteBusy: boolean;
  onToggleFavorite: () => void;
  sellerTrustScore?: number;
}) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real Karrot "이 글 숨기기" (hide this post) -- backend excludes this listing from
  // browse, so a plain onChanged() refetch is what makes it disappear.
  const [hiding, setHiding] = useState(false);
  const handleHide = async () => {
    setHiding(true);
    try {
      await hideListing(listing.id);
      onChanged();
    } catch {
      // Real, non-critical -- a failed hide just means the listing is still visible,
      // same "silent, non-blocking" discipline the favorite toggle above uses.
    } finally {
      setHiding(false);
    }
  };
  const [offering, setOffering] = useState(false);
  const [offerAmount, setOfferAmount] = useState('');
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null); // [lat, lng]
  const [showRoute, setShowRoute] = useState(false);
  const [locating, setLocating] = useState(false);
  // Real optional "who bought this?" prompt -- confirm with a phone or Skip either way.
  const [markingSold, setMarkingSold] = useState(false);
  const [buyerPhone, setBuyerPhone] = useState('');
  const myUserId = getStoredUser()?.id;
  // Real 가격 수정 (price edit) -- see lib/marketplace.ts's own doc comment.
  const [editingPrice, setEditingPrice] = useState(false);
  const [newPrice, setNewPrice] = useState('');
  // Real gap found live (2026-08-31, market-readiness audit): bump/boost had zero web
  // client despite being real, live features on Android/iOS -- see lib/marketplace.ts's
  // own doc comments on bumpListing/boostListing.
  const [bumping, setBumping] = useState(false);
  const [showBoostPicker, setShowBoostPicker] = useState(false);
  const [boostTiers, setBoostTiers] = useState<Record<string, number> | null>(null);
  const [boosting, setBoosting] = useState(false);
  const isBoosted = listing.boostedUntil != null && new Date(listing.boostedUntil) > new Date();
  // Real "pay via itunda" escrow -- status is fetched for BOTH buyer and seller of a
  // SOLD listing, not buyer-only, so a seller can see the delivery address.
  const [paying, setPaying] = useState(false);
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [escrow, setEscrow] = useState<MarketplaceEscrow | null>(null);
  const [loadedEscrow, setLoadedEscrow] = useState(false);
  const [showDispute, setShowDispute] = useState(false);
  const [disputeReason, setDisputeReason] = useState('');
  const [resolvingEscrow, setResolvingEscrow] = useState(false);
  const isMyEscrowTrade = listing.status === 'SOLD' && myUserId != null && (listing.sellerId === myUserId || listing.buyerId === myUserId);
  const isEscrowBuyer = isMyEscrowTrade && !isMine;
  useEffect(() => {
    if (!isMyEscrowTrade || loadedEscrow) return;
    getEscrow(listing.id).then(setEscrow).catch(() => {}).finally(() => setLoadedEscrow(true));
  }, [isMyEscrowTrade, loadedEscrow, listing.id]);

  // Real 당근마켓 조회수 (view count) -- a "view" is a non-owner seeing this card at all.
  const [freshViewCount, setFreshViewCount] = useState<number | null>(null);
  useEffect(() => {
    if (isMine) return;
    fetchListingDetail(listing.id).then((r) => setFreshViewCount(r.listing.viewCount ?? null)).catch(() => {});
  }, [isMine, listing.id]);
  const displayedViewCount = freshViewCount ?? listing.viewCount;

  // Real post-transaction review with asymmetric public/private visibility
  // (2026-07-24) -- see backend HoodReviewService's own doc comment.
  const [showReviewSheet, setShowReviewSheet] = useState(false);
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [selectedUncomfortablePoints, setSelectedUncomfortablePoints] = useState<Set<string>>(new Set());
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  // Real read-back (see lib/marketplace.ts's fetchListingReviews) -- without this,
  // reviewSubmitted was purely local/optimistic and reset on every refresh.
  const [hoodReviews, setHoodReviews] = useState<HoodReview[] | null>(null);
  useEffect(() => {
    if (!(isMine && listing.status === 'SOLD' && listing.buyerId)) return;
    fetchListingReviews(listing.id)
      .then((reviews) => {
        setHoodReviews(reviews);
        if (reviews.some((r) => r.reviewerId === myUserId)) setReviewSubmitted(true);
      })
      .catch(() => {
        // Real, non-critical -- the review form itself still works without this.
      });
  }, [listing.id, listing.status, listing.buyerId, isMine]);

  const handleShowDirections = () => {
    if (showRoute) {
      setShowRoute(false);
      return;
    }
    if (myLocation) {
      setShowRoute(true);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShowRoute(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleMarkSold = async (buyerPhoneNumber?: string) => {
    setBusy(true);
    setError(null);
    try {
      await markListingSold(listing.id, buyerPhoneNumber || undefined);
      setMarkingSold(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real post-transaction review with asymmetric public/private visibility -- see
  // backend HoodReviewService's own doc comment.
  const handleSubmitReview = async () => {
    setSubmittingReview(true);
    setError(null);
    try {
      const review = await submitListingReview(listing.id, Array.from(selectedGoodPoints), Array.from(selectedUncomfortablePoints));
      setReviewSubmitted(true);
      setShowReviewSheet(false);
      setHoodReviews((prev) => [...(prev ?? []), review]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmittingReview(false);
    }
  };

  const handleRemove = async () => {
    setBusy(true);
    setError(null);
    try {
      await removeListing(listing.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real 가격 수정 (price edit) + Karrot 가격 하락 알림 -- a price drop real-notifies
  // every real favoriter server-side, see lib/marketplace.ts's own doc comment.
  const handleUpdatePrice = async () => {
    const price = Number(newPrice);
    if (!(price > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await updateListingPrice(listing.id, price);
      setEditingPrice(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real gap found live (2026-08-31, market-readiness audit): bump/boost had zero web
  // client despite being real, live features on Android/iOS -- see lib/marketplace.ts's
  // own doc comments on bumpListing/boostListing.
  const handleBump = async () => {
    setBumping(true);
    setError(null);
    try {
      await bumpListing(listing.id);
      onChanged();
    } catch (err) {
      // A real 429 BUMP_COOLDOWN is expected/common here (once-per-24h), not a
      // failure -- surfaced with the backend's own honest message either way.
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBumping(false);
    }
  };

  const handleOpenBoostPicker = async () => {
    setShowBoostPicker(true);
    if (!boostTiers) {
      try {
        setBoostTiers(await fetchBoostTiers());
      } catch {
        // Non-critical -- the picker just shows nothing to pick until a retry works.
      }
    }
  };

  const handleBoost = async (days: number) => {
    setBoosting(true);
    setError(null);
    try {
      await boostListing(listing.id, days);
      setShowBoostPicker(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBoosting(false);
    }
  };

  const handleMessage = async () => {
    setBusy(true);
    setError(null);
    try {
      const conversation = await contactSeller(listing.id);
      onMessageSeller(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleMakeOffer = async () => {
    const amount = Number(offerAmount);
    if (!amount || amount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      const offer = await makeOffer(listing.id, amount);
      setOffering(false);
      setOfferAmount('');
      onMessageSeller(offer.conversationId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handlePayEscrow = async () => {
    setPaying(true);
    setError(null);
    try {
      await payEscrow(listing.id, deliveryAddress);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setPaying(false);
    }
  };

  const handleConfirmReceipt = async () => {
    setResolvingEscrow(true);
    setError(null);
    try {
      setEscrow(await confirmEscrowReceipt(listing.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setResolvingEscrow(false);
    }
  };

  const handleDisputeEscrow = async () => {
    if (!disputeReason.trim()) return;
    setResolvingEscrow(true);
    setError(null);
    try {
      setEscrow(await disputeEscrow(listing.id, disputeReason.trim()));
      setShowDispute(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setResolvingEscrow(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
            {listing.title}
            {listing.status === 'SOLD' && (
              <span style={{ marginLeft: '8px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', backgroundColor: 'var(--itunda-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
                SOLD
              </span>
            )}
            {/* Real gap found live (2026-08-31, market-readiness audit): this badge
                existed on Android/iOS with zero web equivalent -- see
                Listing.boostedUntil's own doc comment on the backend. */}
            {isBoosted && (
              <span style={{ marginLeft: '8px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)', backgroundColor: 'var(--itunda-indigo-light)', padding: '2px 8px', borderRadius: '8px' }}>
                BOOSTED
              </span>
            )}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {listing.category}
            {displayedViewCount != null && <> · Views {displayedViewCount.toLocaleString()}</>}
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {!isMine && (
            <button
              type="button"
              onClick={handleHide}
              disabled={hiding}
              title="Hide this listing -- you won't see it again"
              style={{ background: 'none', border: 'none', padding: 0, fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', cursor: 'pointer' }}
            >
              {hiding ? '…' : 'Hide'}
            </button>
          )}
          {!isMine && <WishlistButton favorited={favorited} busy={favoriteBusy} onToggle={onToggleFavorite} />}
          <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{listing.price.toLocaleString()} RWF</span>
        </div>
      </div>
      {/* Real seller-uploaded photo -- see lib/marketplace.ts's own doc comment on
          Listing.photoUrl. */}
      {listing.photoUrl && (
        <img
          src={listing.photoUrl}
          alt={listing.title}
          style={{ width: '100%', maxHeight: '220px', objectFit: 'cover', borderRadius: '10px' }}
        />
      )}
      {/* Real Karrot-Score trust badge -- see TrustBadge's own doc comment. Only
          shown for someone else's listing. */}
      {!isMine && sellerTrustScore != null && <TrustBadge score={sellerTrustScore} />}
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>{listing.description}</p>
      <VehicleDetailSection listing={listing} />
      {listing.meetingPlace && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)', margin: 0 }}>Suggested hand-off: {listing.meetingPlace}</p>
      )}
      {offering && (
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="number"
            value={offerAmount}
            onChange={(e) => setOfferAmount(e.target.value)}
            placeholder="Your offer (RWF)"
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !offerAmount} onClick={handleMakeOffer}>
            Send
          </button>
        </div>
      )}
      <HoodListingSellerForms
        editingPrice={editingPrice} newPrice={newPrice} setNewPrice={setNewPrice} setEditingPrice={setEditingPrice} handleUpdatePrice={handleUpdatePrice} busy={busy}
        markingSold={markingSold} buyerPhone={buyerPhone} setBuyerPhone={setBuyerPhone} handleMarkSold={handleMarkSold}
        showBoostPicker={showBoostPicker} boostTiers={boostTiers} boosting={boosting} handleBoost={handleBoost} setShowBoostPicker={setShowBoostPicker}
      />
      {/* Real post-transaction review, preset checklist with asymmetric public/private
          visibility -- see backend HoodReviewService's own doc comment. */}
      {isMine && listing.status === 'SOLD' && listing.buyerId && reviewSubmitted && hoodReviews && (
        <HoodReviewResultView reviews={hoodReviews} myUserId={myUserId} />
      )}
      {isMine && listing.status === 'SOLD' && listing.buyerId && !reviewSubmitted && (
        showReviewSheet ? (
          <HoodReviewForm
            selectedGoodPoints={selectedGoodPoints}
            onToggleGoodPoint={(id) => setSelectedGoodPoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            selectedUncomfortablePoints={selectedUncomfortablePoints}
            onToggleUncomfortablePoint={(id) => setSelectedUncomfortablePoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            submitting={submittingReview}
            onCancel={() => setShowReviewSheet(false)}
            onSubmit={handleSubmitReview}
          />
        ) : (
          <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={() => setShowReviewSheet(true)}>
            Rate this buyer
          </button>
        )
      )}
      {/* Real MarketplaceEscrow.deliveryAddress (당근마켓 바로구매-style shipped-item
          support) -- optional and blank by default; in-person handoff still works. */}
      {!isMine && listing.status === 'ACTIVE' && !offering && (
        <input
          type="text"
          value={deliveryAddress}
          onChange={(e) => setDeliveryAddress(e.target.value)}
          placeholder="Delivery address (optional, for a shipped item)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
      )}
      <div style={{ display: 'flex', gap: '8px' }}>
        {isMine ? (
          <>
            {listing.status === 'ACTIVE' && !markingSold && !editingPrice && (
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => { setEditingPrice(true); setNewPrice(String(listing.price)); }}>
                Edit price
              </button>
            )}
            {listing.status === 'ACTIVE' && !markingSold && (
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => setMarkingSold(true)}>
                Mark sold
              </button>
            )}
            {/* Real gap found live (2026-08-31, market-readiness audit): Bump/Boost
                existed on Android/iOS with zero web client -- see lib/marketplace.ts's
                own doc comments. */}
            {listing.status === 'ACTIVE' && !markingSold && !editingPrice && !showBoostPicker && (
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={bumping} onClick={handleBump}>
                {bumping ? '…' : 'Bump'}
              </button>
            )}
            {listing.status === 'ACTIVE' && !markingSold && !editingPrice && !showBoostPicker && !isBoosted && (
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={boosting} onClick={handleOpenBoostPicker}>
                Boost
              </button>
            )}
            {listing.status !== 'REMOVED' && (
              <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} disabled={busy} onClick={handleRemove}>
                Remove
              </button>
            )}
          </>
        ) : (
          listing.status === 'ACTIVE' && !offering && (
            <>
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={handleMessage}>
                {busy ? 'Starting…' : 'Message seller'}
              </button>
              <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => setOffering(true)}>
                Make an offer
              </button>
              {/* Real "pay via itunda" Marketplace escrow -- an opt-in safer
                  alternative to the in-person cash handoff, never replacing it. */}
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }} disabled={paying} onClick={handlePayEscrow}>
                {paying ? 'Paying…' : <><LockGlyph size={14} /> Pay via itunda</>}
              </button>
            </>
          )
        )}
      </div>
      {/* Real escrow status, shown to BOTH buyer and seller of a SOLD listing, not
          buyer-only. Confirm receipt/Report a problem stay buyer-only. */}
      {isMyEscrowTrade && escrow && (
        <HoodListingEscrowStatus
          escrow={escrow} isEscrowBuyer={isEscrowBuyer}
          showDispute={showDispute} setShowDispute={setShowDispute}
          disputeReason={disputeReason} setDisputeReason={setDisputeReason}
          resolvingEscrow={resolvingEscrow} handleDisputeEscrow={handleDisputeEscrow} handleConfirmReceipt={handleConfirmReceipt}
        />
      )}
      {!isMine && <HoodReportButton targetType="MARKETPLACE_LISTING" targetId={listing.id} />}
      {!isMine && listing.status === 'ACTIVE' && listing.latitude != null && listing.longitude != null && (
        <button className="itunda-btn itunda-btn-secondary" disabled={locating} onClick={handleShowDirections}>
          {locating ? 'Finding your real location…' : showRoute ? 'Hide directions' : '🚗 Directions to this seller'}
        </button>
      )}
      {showRoute && myLocation && listing.latitude != null && listing.longitude != null && (
        <RouteMiniMap
          fromLat={myLocation[0]}
          fromLng={myLocation[1]}
          toLat={listing.latitude}
          toLng={listing.longitude}
          fromLabel="You"
          toLabel={listing.title}
        />
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}
