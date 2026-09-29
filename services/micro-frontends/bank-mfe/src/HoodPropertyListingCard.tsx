import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError, getStoredUser } from './lib/api';
import { type HoodReview } from './lib/marketplace';
import { fetchPropertyListingReviews, makePropertyOffer, markPropertyListingTaken, removePropertyListing, submitPropertyListingReview, submitPropertyOwnershipVerification, type PropertyListing, updatePropertyListingPrice } from './lib/realestate';
import { uploadFile } from './lib/upload';
import { fetchNeighborhoodReviews, submitNeighborhoodReview, type NeighborhoodReview } from './lib/community';
import { HoodReportButton, HoodReviewForm, HoodReviewResultView, TrustBadge, WishlistButton } from './BankDashboard';

export function PropertyListingCard({ listing, propertyTypeLabel, isMine, onChanged, onContact, onMessageLister, favorited, favoriteBusy, onToggleFavorite, listerTrustScore }: {
  listing: PropertyListing; propertyTypeLabel: string; isMine: boolean; onChanged: () => void; onContact: () => void;
  onMessageLister: (conversationId: string) => void;
  favorited: boolean; favoriteBusy: boolean; onToggleFavorite: () => void; listerTrustScore?: number;
}) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService's
  // own doc comment; mirrors ListingCard's own offering state exactly.
  const [offering, setOffering] = useState(false);
  const [offerAmount, setOfferAmount] = useState('');

  // Real optional buyer/tenant identification at mark-taken time (2026-07-24) -- see
  // backend PropertyListingService.markTaken's own doc comment.
  const [markingTaken, setMarkingTaken] = useState(false);
  const [counterpartyPhone, setCounterpartyPhone] = useState('');
  // Real Karrot(당근마켓)-style price edit -- see lib/realestate.ts's own doc comment.
  const [editingPrice, setEditingPrice] = useState(false);
  const [newPrice, setNewPrice] = useState('');

  // Real post-transaction review with asymmetric public/private visibility
  // (2026-07-24) -- see backend HoodReviewService's own doc comment.
  const [showReviewSheet, setShowReviewSheet] = useState(false);
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [selectedUncomfortablePoints, setSelectedUncomfortablePoints] = useState<Set<string>>(new Set());
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  // Real read-back (item 192) -- see lib/marketplace.ts's fetchListingReviews doc comment.
  const [hoodReviews, setHoodReviews] = useState<HoodReview[] | null>(null);
  const myUserId = getStoredUser()?.id;

  // Real ownership verification (2026-07-25, real upload added 2026-08-01) -- see
  // lib/realestate.ts's own doc comment on ownershipVerificationStatus. Was a
  // paste-a-URL text field (an honest v1 scope-down) until lib/upload.ts's real
  // POST /api/v1/uploads client -- mirrors PropertyScreen.kt's own pickOwnershipDoc
  // flow exactly now. submittedStatus is local so "pending" shows immediately without
  // a full listing refetch, mirroring Android's own submittedOwnershipStatus.
  const [showOwnershipForm, setShowOwnershipForm] = useState(false);
  const [ownershipDocUrl, setOwnershipDocUrl] = useState('');
  const [uploadingOwnershipDoc, setUploadingOwnershipDoc] = useState(false);
  const [submittingOwnership, setSubmittingOwnership] = useState(false);
  const [submittedOwnershipStatus, setSubmittedOwnershipStatus] = useState<string | null>(null);
  const ownershipStatus = submittedOwnershipStatus ?? listing.ownershipVerificationStatus ?? 'NONE';

  const handleOwnershipDocSelected = async (file: File | undefined) => {
    if (!file) return;
    setUploadingOwnershipDoc(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      setOwnershipDocUrl(url);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploadingOwnershipDoc(false);
    }
  };

  const handleSubmitOwnership = async () => {
    if (!ownershipDocUrl.trim()) return;
    setSubmittingOwnership(true);
    setError(null);
    try {
      await submitPropertyOwnershipVerification(listing.id, ownershipDocUrl.trim());
      setSubmittedOwnershipStatus('PENDING');
      setShowOwnershipForm(false);
      setOwnershipDocUrl('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmittingOwnership(false);
    }
  };
  useEffect(() => {
    if (!(isMine && listing.status === 'TAKEN' && listing.counterpartyId)) return;
    fetchPropertyListingReviews(listing.id)
      .then((reviews) => {
        setHoodReviews(reviews);
        if (reviews.some((r) => r.reviewerId === myUserId)) setReviewSubmitted(true);
      })
      .catch(() => {
        // Real, non-critical -- the review form itself still works without this.
      });
  }, [listing.id, listing.status, listing.counterpartyId, isMine]);

  const priceLabel = `${listing.price.toLocaleString('en-US')} RWF${listing.listingType === 'RENT' ? '/mo' : ''}`;
  const detailsLabel = [
    listing.bedrooms != null ? `${listing.bedrooms} bd` : null,
    listing.sizeSqm != null ? `${listing.sizeSqm} m²` : null,
  ].filter(Boolean).join(' · ');

  const handleMakeOffer = async () => {
    const amount = Number(offerAmount);
    if (!amount || amount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      const offer = await makePropertyOffer(listing.id, amount);
      setOffering(false);
      setOfferAmount('');
      onMessageLister(offer.conversationId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleMarkTaken = async (counterpartyPhoneNumber?: string) => {
    setBusy(true);
    setError(null);
    try {
      await markPropertyListingTaken(listing.id, counterpartyPhoneNumber || undefined);
      setMarkingTaken(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleUpdatePrice = async () => {
    const price = Number(newPrice);
    if (!(price > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await updatePropertyListingPrice(listing.id, price);
      setEditingPrice(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitReview = async () => {
    setSubmittingReview(true);
    setError(null);
    try {
      const review = await submitPropertyListingReview(listing.id, Array.from(selectedGoodPoints), Array.from(selectedUncomfortablePoints));
      setReviewSubmitted(true);
      setShowReviewSheet(false);
      setHoodReviews((prev) => [...(prev ?? []), review]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmittingReview(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
            {listing.listingType === 'RENT' ? 'For rent' : 'For sale'} · {propertyTypeLabel}
          </span>
          {listing.status === 'TAKEN' && (
            <span style={{ marginLeft: '8px', fontSize: '10px', fontWeight: 700, color: 'var(--itunda-grey-500)', backgroundColor: 'var(--itunda-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
              TAKEN
            </span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {/* Real 당근부동산 property-listing wishlist (2026-07-22) -- mirrors
              ListingCard's own WishlistButton reuse exactly, closing a
              docs/DESIGN_REFERENCES.md-named gap: Marketplace listings already had
              this, Property never did. */}
          {!isMine && <WishlistButton favorited={favorited} busy={favoriteBusy} onToggle={onToggleFavorite} />}
          <span style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{priceLabel}</span>
        </div>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{listing.title}</p>
      {detailsLabel && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{detailsLabel}</p>}
      {/* Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
          comment. Only shown for someone else's listing. */}
      {!isMine && listerTrustScore != null && <TrustBadge score={listerTrustScore} />}
      {/* Real ownership verification badge (2026-07-25) -- shown to every viewer, not
          just the lister, a trust signal for the buyer/tenant deciding whether to
          contact this listing. Mirrors PropertyScreen.kt's own badge exactly. */}
      {ownershipStatus === 'VERIFIED' && (
        <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)', backgroundColor: 'rgba(49,130,246,0.12)', padding: '2px 8px', borderRadius: '8px', width: 'fit-content' }}>
          ✓ Owner verified
        </span>
      )}
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{listing.description}</p>
      {listing.neighborhood && <NeighborhoodReviewsSection neighborhood={listing.neighborhood} />}
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
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {editingPrice && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="number" min="1" value={newPrice} onChange={(e) => setNewPrice(e.target.value)}
            placeholder="New price (RWF)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => setEditingPrice(false)}>
              Cancel
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy || !(Number(newPrice) > 0)} onClick={handleUpdatePrice}>
              Save
            </button>
          </div>
        </div>
      )}
      {/* Real optional "who's the buyer/tenant?" prompt (2026-07-24) -- see backend
          PropertyListingService.markTaken's own doc comment. */}
      {markingTaken && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="tel"
            value={counterpartyPhone}
            onChange={(e) => setCounterpartyPhone(e.target.value)}
            placeholder="Their phone (optional)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkTaken()}>
              Skip
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkTaken(counterpartyPhone.trim())}>
              Confirm
            </button>
          </div>
        </div>
      )}
      {/* Real post-transaction review, preset checklist with asymmetric public/private
          visibility (2026-07-24) -- see backend HoodReviewService's own doc comment.
          Only offered once a real counterparty was recorded at mark-taken time. */}
      {isMine && listing.status === 'TAKEN' && listing.counterpartyId && reviewSubmitted && hoodReviews && (
        <HoodReviewResultView reviews={hoodReviews} myUserId={myUserId} />
      )}
      {isMine && listing.status === 'TAKEN' && listing.counterpartyId && !reviewSubmitted && (
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
            {listing.listingType === 'RENT' ? 'Rate this tenant' : 'Rate this buyer'}
          </button>
        )
      )}
      {/* Real ownership verification action (2026-07-25, real upload 2026-08-01) --
          NONE -> offer to upload a real deed/title photo; PENDING -> awaiting a real
          human reviewer, nothing to do; VERIFIED -> already covered by the badge
          above. Mirrors PropertyScreen.kt's own pickOwnershipDoc flow exactly. */}
      {isMine && ownershipStatus === 'NONE' && (
        showOwnershipForm ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={uploadingOwnershipDoc || submittingOwnership}
              onChange={(e) => handleOwnershipDocSelected(e.target.files?.[0])}
              style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}
            />
            {uploadingOwnershipDoc && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Uploading…</p>}
            {ownershipDocUrl && !uploadingOwnershipDoc && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>✓ Document uploaded</p>}
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={submittingOwnership} onClick={() => { setShowOwnershipForm(false); setOwnershipDocUrl(''); }}>
                Cancel
              </button>
              {/* Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11): "Submit"
                  doesn't say what happens -- this starts a real human-reviewer process (see
                  this block's own doc comment above), not an instant action. */}
              <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submittingOwnership || uploadingOwnershipDoc || !ownershipDocUrl.trim()} onClick={handleSubmitOwnership}>
                {submittingOwnership ? 'Submitting…' : 'Submit for review'}
              </button>
            </div>
          </div>
        ) : (
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => setShowOwnershipForm(true)}>
            Verify ownership
          </button>
        )
      )}
      {isMine && ownershipStatus === 'PENDING' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Verification pending review</p>
      )}
      <div style={{ display: 'flex', gap: '10px' }}>
        {isMine ? (
          <>
            {listing.status === 'AVAILABLE' && !markingTaken && !editingPrice && (
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={busy}
                onClick={() => { setEditingPrice(true); setNewPrice(String(listing.price)); }}
              >
                Edit price
              </button>
            )}
            {listing.status === 'AVAILABLE' && !markingTaken && (
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={busy}
                onClick={() => setMarkingTaken(true)}
              >
                Mark taken
              </button>
            )}
            {listing.status !== 'REMOVED' && (
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await removePropertyListing(listing.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
                  finally { setBusy(false); }
                }}
              >
                Remove
              </button>
            )}
          </>
        ) : (
          listing.status === 'AVAILABLE' && !offering && (
            <>
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={onContact}>
                Message lister
              </button>
              <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => setOffering(true)}>
                Make an offer
              </button>
            </>
          )
        )}
      </div>
      {!isMine && <HoodReportButton targetType="PROPERTY_LISTING" targetId={listing.id} />}
    </div>
  );
}

// Real 살아본 후기 (Karrot "lived here" neighborhood reviews), itunda Hood redesign
// 2026-08-28 -- see backend NeighborhoodReview.kt's own doc comment. Distinct from
// HoodReview (a buyer/seller transaction review) -- this is a public review of an
// area, shown on every property listing in that neighborhood.
function NeighborhoodReviewsSection({ neighborhood }: { neighborhood: string }) {
  const { t } = useI18n();
  const [reviews, setReviews] = useState<NeighborhoodReview[] | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [body, setBody] = useState('');
  const [years, setYears] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = () => { fetchNeighborhoodReviews(neighborhood).then(setReviews).catch(() => setReviews([])); };
  useEffect(load, [neighborhood]);

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await submitNeighborhoodReview(neighborhood, body.trim(), years ? Number(years) : undefined);
      setBody(''); setYears(''); setShowForm(false); setError(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>살아본 후기 · {neighborhood}</p>
      {reviews === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : reviews.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>No reviews yet — be the first to share what it's like living here.</p>
      ) : (
        reviews.map((r) => (
          <div key={r.id} style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
            {r.residencyYears != null && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, color: 'var(--itunda-grey-600)' }}>{r.residencyYears} years living here</p>}
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{r.body}</p>
          </div>
        ))
      )}
      {!showForm ? (
        <button className="itunda-btn itunda-btn-secondary" style={{ width: 'fit-content' }} onClick={() => setShowForm(true)}>+ Write a review</button>
      ) : (
        <>
          <textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="What's it like living here?" rows={3}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }} />
          <input type="number" value={years} onChange={(e) => setYears(e.target.value)} placeholder="Years living here (optional)"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }} />
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => { setShowForm(false); setError(null); }}>Cancel</button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting || !body.trim()} onClick={handleSubmit}>
              {submitting ? 'Saving…' : 'Save'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}

