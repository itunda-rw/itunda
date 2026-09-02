import { useEffect, useState } from 'react';
import { EmptyState, ErrorCard } from './EmptyState';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { ApiError, getStoredUser } from './lib/api';
import { applyToJob, createJobPost, fetchApplicationsForJobPost, fetchJobPost, fetchJobPostReviews, fetchMyFavoriteJobPosts, fetchMyJobApplications, markJobPostFilled, removeJobPost, removeJobPostFavorite, respondToJobApplication, submitJobPostReview, type FavoriteJobPost, type JobApplication, type JobCategory, type JobPayType, type JobPost } from './lib/jobs';
import { type HoodReview } from './lib/marketplace';
import { HoodReportButton, HoodReviewForm, HoodReviewResultView, TrustBadge, WishlistButton } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

export function NewJobPostCard({ categories, onCreated }: { categories: JobCategory[]; onCreated: () => void }) {
  const { t } = useI18n();
  const [category, setCategory] = useState(categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [payType, setPayType] = useState<JobPayType>('HOURLY');
  const [payAmount, setPayAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createJobPost(category, title, description, payType, Number(payAmount));
      setTitle('');
      setDescription('');
      setPayAmount('');
      setOpen(false);
      showToast('Job posted.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + Post a job
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Post a job</h3>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      >
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What do you need done?" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the work" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <select
          value={payType} onChange={(e) => setPayType(e.target.value as JobPayType)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        >
          <option value="HOURLY">Per hour</option>
          <option value="FIXED">Fixed price</option>
        </select>
        <input
          type="number" value={payAmount} onChange={(e) => setPayAmount(e.target.value)} placeholder="Pay (RWF)" required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Posting…' : 'Post job'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

export function JobPostCard({ post, categoryLabel, isMine, onChanged, onContact, favorited, favoriteBusy, onToggleFavorite, posterTrustScore }: {
  post: JobPost; categoryLabel: string; isMine: boolean; onChanged: () => void; onContact: () => void;
  favorited: boolean; favoriteBusy: boolean; onToggleFavorite: () => void; posterTrustScore?: number;
}) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Real optional worker identification at mark-filled time (2026-07-24) -- see
  // backend JobPostService.markFilled's own doc comment.
  const [markingFilled, setMarkingFilled] = useState(false);
  const [workerPhone, setWorkerPhone] = useState('');

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
  useEffect(() => {
    if (!(isMine && post.status === 'FILLED' && post.workerId)) return;
    fetchJobPostReviews(post.id)
      .then((reviews) => {
        setHoodReviews(reviews);
        if (reviews.some((r) => r.reviewerId === myUserId)) setReviewSubmitted(true);
      })
      .catch(() => {
        // Real, non-critical -- the review form itself still works without this.
      });
  }, [post.id, post.status, post.workerId, isMine]);

  // Real 당근알바-style structured application (2026-07-25 on Android/iOS, ported here
  // 2026-07-29) -- the applicant's real self-introduction, not a bare DM. See backend
  // JobApplicationService's own doc comment. "Message poster" above still exists as a
  // separate, unstructured hand-off.
  const [applying, setApplying] = useState(false);
  const [applicationMessage, setApplicationMessage] = useState('');
  const [applicationSubmitted, setApplicationSubmitted] = useState(false);
  const [submittingApplication, setSubmittingApplication] = useState(false);
  const [showApplicants, setShowApplicants] = useState(false);
  const [applications, setApplications] = useState<JobApplication[] | null>(null);
  const showApplicationsSkeleton = useDeferredLoading(applications === null);
  const [respondingToId, setRespondingToId] = useState<string | null>(null);

  useEffect(() => {
    if (!showApplicants || applications !== null) return;
    fetchApplicationsForJobPost(post.id)
      .then(setApplications)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, [showApplicants]);

  const handleSubmitApplication = async () => {
    setSubmittingApplication(true);
    setError(null);
    try {
      await applyToJob(post.id, applicationMessage.trim());
      setApplying(false);
      setApplicationSubmitted(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmittingApplication(false);
    }
  };

  const handleRespond = async (applicationId: string, accept: boolean) => {
    setRespondingToId(applicationId);
    setError(null);
    try {
      await respondToJobApplication(applicationId, accept);
      setApplications((prev) => prev?.filter((a) => a.id !== applicationId) ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRespondingToId(null);
    }
  };

  const handleMarkFilled = async (workerPhoneNumber?: string) => {
    setBusy(true);
    setError(null);
    try {
      await markJobPostFilled(post.id, workerPhoneNumber || undefined);
      setMarkingFilled(false);
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
      const review = await submitJobPostReview(post.id, Array.from(selectedGoodPoints), Array.from(selectedUncomfortablePoints));
      setReviewSubmitted(true);
      setShowReviewSheet(false);
      setHoodReviews((prev) => [...(prev ?? []), review]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmittingReview(false);
    }
  };

  const payLabel = `${post.payAmount.toLocaleString()} RWF${post.payType === 'HOURLY' ? '/hr' : ''}`;

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{categoryLabel}</span>
          {post.status === 'FILLED' && (
            <span style={{ marginLeft: '8px', fontSize: '10px', fontWeight: 700, color: 'var(--itunda-grey-500)', backgroundColor: 'var(--itunda-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
              FILLED
            </span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {/* Real 당근알바 job-post wishlist (2026-07-22) -- mirrors ListingCard's own
              WishlistButton reuse exactly, closing a docs/DESIGN_REFERENCES.md-named
              gap: Marketplace listings already had this, Jobs never did. */}
          {!isMine && <WishlistButton favorited={favorited} busy={favoriteBusy} onToggle={onToggleFavorite} />}
          <span style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{payLabel}</span>
        </div>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{post.title}</p>
      {/* Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
          comment. Only shown for someone else's post. */}
      {!isMine && posterTrustScore != null && <TrustBadge score={posterTrustScore} />}
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{post.description}</p>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {/* Real optional "who did you hire?" prompt (2026-07-24) -- see backend
          JobPostService.markFilled's own doc comment. */}
      {markingFilled && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="tel"
            value={workerPhone}
            onChange={(e) => setWorkerPhone(e.target.value)}
            placeholder="Worker's phone (optional)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkFilled()}>
              Skip
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkFilled(workerPhone.trim())}>
              Confirm
            </button>
          </div>
        </div>
      )}
      {/* Real post-transaction review, preset checklist with asymmetric public/private
          visibility (2026-07-24) -- see backend HoodReviewService's own doc comment.
          Only offered once a real worker was recorded at mark-filled time. */}
      {isMine && post.status === 'FILLED' && post.workerId && reviewSubmitted && hoodReviews && (
        <HoodReviewResultView reviews={hoodReviews} myUserId={myUserId} />
      )}
      {isMine && post.status === 'FILLED' && post.workerId && !reviewSubmitted && (
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
            Rate this worker
          </button>
        )
      )}
      <div style={{ display: 'flex', gap: '10px' }}>
        {isMine ? (
          <>
            {post.status === 'OPEN' && !markingFilled && (
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={busy}
                onClick={() => setMarkingFilled(true)}
              >
                Mark filled
              </button>
            )}
            {post.status !== 'REMOVED' && (
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await removeJobPost(post.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
                  finally { setBusy(false); }
                }}
              >
                Remove
              </button>
            )}
          </>
        ) : (
          post.status === 'OPEN' && (
            <>
              <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={onContact}>
                Message poster
              </button>
              {!applicationSubmitted && !applying && (
                <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={() => setApplying(true)}>
                  Apply
                </button>
              )}
            </>
          )
        )}
      </div>
      {!isMine && applying && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <textarea
            value={applicationMessage}
            onChange={(e) => setApplicationMessage(e.target.value)}
            placeholder="Why should the poster pick you? (required)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', minHeight: '72px' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={submittingApplication} onClick={() => setApplying(false)}>
              Cancel
            </button>
            <button
              className="itunda-btn itunda-btn-primary" style={{ flex: 1 }}
              disabled={submittingApplication || applicationMessage.trim().length === 0}
              onClick={handleSubmitApplication}
            >
              {submittingApplication ? 'Submitting…' : 'Submit application'}
            </button>
          </div>
        </div>
      )}
      {!isMine && applicationSubmitted && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Application sent — you'll hear back once the poster reviews it</p>
      )}
      {isMine && post.status === 'OPEN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => setShowApplicants((v) => !v)}>
            {showApplicants ? 'Hide applicants' : 'View applicants'}
          </button>
          {showApplicants && (
            applications === null ? (showApplicationsSkeleton ? <div className="skeleton" style={{ height: '60px', borderRadius: 'var(--itunda-radius-md)' }} /> : null) :
            applications.filter((a) => a.status === 'PENDING').length === 0 ? (
              <EmptyState message="No applications yet — apply to a job post and it'll show up here." />
            ) : (
              applications.filter((a) => a.status === 'PENDING').map((app) => (
                <div key={app.id} style={{ backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', padding: '12px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{app.message}</p>
                  {app.resumeSnapshotJson && (
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-brand)', fontWeight: 600 }}>📄 Résumé attached</p>
                  )}
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={respondingToId === app.id} onClick={() => handleRespond(app.id, false)}>
                      Decline
                    </button>
                    <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={respondingToId === app.id} onClick={() => handleRespond(app.id, true)}>
                      Accept &amp; message
                    </button>
                  </div>
                </div>
              ))
            )
          )}
        </div>
      )}
      {!isMine && <HoodReportButton targetType="JOB_POST" targetId={post.id} />}
    </div>
  );
}

// Real "My applications" status view (2026-07-25 on Android as item 196, ported here
// 2026-07-29) -- an applicant could submit a real structured application and message
// the poster, but never see whether it was pending/accepted/declined. JobApplication
// carries no job-post title snapshot, so this fans out one real fetchJobPost per
// application to resolve the title, same N+1 shape Android's own port uses.
export function MyJobApplicationsView() {
  const { t } = useI18n();
  const [applications, setApplications] = useState<Array<{ application: JobApplication; title: string | null }> | null>(null);
  const showApplicationsSkeleton = useDeferredLoading(applications === null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMyJobApplications()
      .then(async (apps) => {
        const withTitles = await Promise.all(
          apps.map(async (application) => {
            const title = await fetchJobPost(application.jobPostId).then((post) => post.title).catch(() => null);
            return { application, title };
          }),
        );
        setApplications(withTitles);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  if (error) return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>;
  if (applications === null) return showApplicationsSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (applications.length === 0) return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>You haven't applied to any jobs yet.</p>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {applications.map(({ application, title }) => (
        <div key={application.id} className="itunda-flat-section">
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{title ?? 'Job post'}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{application.message}</p>
          <span
            style={{
              display: 'inline-block', marginTop: '6px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, padding: '2px 8px', borderRadius: '8px',
              color: application.status === 'ACCEPTED' ? 'var(--itunda-indigo)' : application.status === 'DECLINED' ? 'var(--itunda-red)' : 'var(--itunda-grey-700)',
              backgroundColor: application.status === 'ACCEPTED' ? 'rgba(49, 130, 246, 0.1)' : application.status === 'DECLINED' ? 'rgba(229, 57, 53, 0.1)' : 'var(--itunda-grey-100)',
            }}
          >
            {application.status}
          </span>
        </div>
      ))}
    </div>
  );
}

// Real 당근알바 job-post wishlist view (2026-07-22) -- mirrors ListingWishlistView
// exactly, closing a docs/DESIGN_REFERENCES.md-named gap.
export function JobPostWishlistView() {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteJobPost[] | null>(null);
  const showFavoritesSkeleton = useDeferredLoading(favorites === null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteJobPosts().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleRemove = async (jobPostId: string) => {
    setRemovingId(jobPostId);
    try {
      await removeJobPostFavorite(jobPostId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return showFavoritesSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (favorites.length === 0) return <EmptyState message="No saved jobs yet -- tap ♡ on any job post to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {favorites.map((f) => (
        <div key={f.jobPostId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{f.category} · {f.payAmount.toLocaleString()} RWF</p>
          </div>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={removingId === f.jobPostId}
            onClick={() => handleRemove(f.jobPostId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {removingId === f.jobPostId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

