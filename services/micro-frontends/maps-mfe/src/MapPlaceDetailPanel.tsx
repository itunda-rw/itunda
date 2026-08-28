import { useEffect, useState } from 'react';
import { fetchMapPlaceDetail, EATS_GOOD_POINT_LABELS, type MapPlaceDetail } from './lib/maps';

const TEXT = '#191F28';
const TEXT_SECONDARY = '#4E5968';
const TEXT_TERTIARY = '#636E7C';
const DIVIDER = '#D1D6DB';

type Tab = 'HOME' | 'MENU' | 'REVIEWS' | 'PHOTOS' | 'NEWS' | 'INFO';

// Real tabbed place-detail panel (itunda Maps redesign, 2026-08-28, direct Naver Map
// reference: the place-detail Home/Menu/Reviews/Photos/News/Info tabs) -- reads the
// one real consolidated backend source (MapPlaceDetailService), replacing the old
// per-field ad hoc client-side enrichment MapView.tsx's own flat merchant card used.
// A real overlay over the map, dismissed via the close button -- this app has no
// modal-dialog primitive of its own to reuse, matching maps-mfe's own established
// "plain absolutely-positioned panel" convention (the bottom sheet itself is one).
export function MapPlaceDetailPanel({ merchantId, onClose }: { merchantId: string; onClose: () => void }) {
  const [detail, setDetail] = useState<MapPlaceDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('HOME');

  useEffect(() => {
    setDetail(null);
    setError(null);
    fetchMapPlaceDetail(merchantId)
      .then(setDetail)
      .catch(() => setError("Couldn't load this place right now."));
  }, [merchantId]);

  const tabs: { id: Tab; label: string }[] = [
    { id: 'HOME', label: 'Home' },
    { id: 'MENU', label: 'Menu' },
    { id: 'REVIEWS', label: 'Reviews' },
    { id: 'PHOTOS', label: 'Photos' },
    { id: 'NEWS', label: 'News' },
    { id: 'INFO', label: 'Info' },
  ];

  return (
    <div style={{ position: 'absolute', inset: 0, background: '#fff', zIndex: 20, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 16px', borderBottom: `1px solid ${DIVIDER}` }}>
        <button type="button" onClick={onClose} aria-label="Back" style={{ fontSize: '20px', color: TEXT }}>
          ←
        </button>
        <p style={{ fontSize: '16px', fontWeight: 700, color: TEXT, flex: 1 }}>{detail?.businessName ?? 'Place'}</p>
      </div>

      {error ? (
        <p style={{ padding: '16px', fontSize: '13px', color: TEXT_SECONDARY }}>{error}</p>
      ) : !detail ? (
        <p style={{ padding: '16px', fontSize: '13px', color: TEXT_SECONDARY }}>Loading…</p>
      ) : (
        <>
          {detail.photoUrl && (
            <img src={detail.photoUrl} alt="" style={{ width: '100%', height: '160px', objectFit: 'cover' }} />
          )}
          <div style={{ display: 'flex', overflowX: 'auto', borderBottom: `1px solid ${DIVIDER}` }}>
            {tabs.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                style={{
                  padding: '10px 14px', fontSize: '13px', fontWeight: 700, whiteSpace: 'nowrap',
                  color: tab === t.id ? 'var(--itunda-indigo)' : TEXT_SECONDARY,
                  borderBottom: tab === t.id ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
                }}
              >
                {t.label}
              </button>
            ))}
          </div>
          <div style={{ flex: 1, overflowY: 'auto', padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {tab === 'HOME' && <HomeTab detail={detail} />}
            {tab === 'MENU' && <MenuTab detail={detail} />}
            {tab === 'REVIEWS' && <ReviewsTab detail={detail} />}
            {tab === 'PHOTOS' && <PhotosTab detail={detail} />}
            {tab === 'NEWS' && <NewsTab detail={detail} />}
            {tab === 'INFO' && <InfoTab detail={detail} />}
          </div>
        </>
      )}
    </div>
  );
}

function HomeTab({ detail }: { detail: MapPlaceDetail }) {
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
        {detail.category && <span style={{ fontSize: '13px', color: TEXT_SECONDARY }}>{detail.category}</span>}
        {detail.rating.count > 0 && detail.rating.average != null && (
          <span style={{ fontSize: '13px', color: TEXT_SECONDARY }}>
            ★ {detail.rating.average.toFixed(1)} ({detail.rating.count})
          </span>
        )}
      </div>
      {/* Real AI summary (itunda Maps redesign, 2026-08-28) -- see AiSummaryService's
          own doc comment on the backend: generated only from real, already-known
          facts, always shown with a visible "AI" disclosure, never presented as
          human-written. */}
      {detail.aiSummary && (
        <div style={{ background: '#F2F4F6', borderRadius: '10px', padding: '10px 12px', display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
          <span style={{ fontSize: '10px', fontWeight: 700, color: '#fff', background: 'var(--itunda-indigo)', borderRadius: '4px', padding: '2px 6px', flexShrink: 0 }}>
            AI
          </span>
          <p style={{ fontSize: '13px', color: TEXT, lineHeight: 1.4 }}>{detail.aiSummary}</p>
        </div>
      )}
      {detail.openingHours && <p style={{ fontSize: '13px', color: TEXT_SECONDARY }}>🕒 {detail.openingHours}</p>}
      {detail.phoneNumber && (
        <a href={`tel:${detail.phoneNumber}`} style={{ fontSize: '13px', fontWeight: 700, color: 'var(--itunda-indigo)', textDecoration: 'none' }}>
          📞 {detail.phoneNumber}
        </a>
      )}
    </>
  );
}

function MenuTab({ detail }: { detail: MapPlaceDetail }) {
  if (detail.menu.length === 0) return <p style={{ fontSize: '13px', color: TEXT_TERTIARY }}>No menu items yet.</p>;
  return (
    <>
      {detail.menu.map((item) => (
        <div key={item.id} style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          {item.imageUrl && <img src={item.imageUrl} alt="" style={{ width: '48px', height: '48px', borderRadius: '8px', objectFit: 'cover', flexShrink: 0 }} />}
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: '13px', fontWeight: 700, color: TEXT }}>{item.name}</p>
            <p style={{ fontSize: '12px', color: TEXT_SECONDARY }}>{item.price.toLocaleString()} RWF</p>
          </div>
        </div>
      ))}
    </>
  );
}

function ReviewsTab({ detail }: { detail: MapPlaceDetail }) {
  const tagEntries = Object.entries(detail.goodPointCounts).sort((a, b) => b[1] - a[1]);
  if (detail.rating.count === 0 && tagEntries.length === 0) {
    return <p style={{ fontSize: '13px', color: TEXT_TERTIARY }}>No reviews yet — be the first to share how it went.</p>;
  }
  return (
    <>
      {detail.rating.count > 0 && detail.rating.average != null && (
        <p style={{ fontSize: '14px', fontWeight: 700, color: TEXT }}>
          ★ {detail.rating.average.toFixed(1)} · {detail.rating.count} review{detail.rating.count === 1 ? '' : 's'}
        </p>
      )}
      {/* Real preset-tag aggregate (itunda Maps redesign, 2026-08-28, direct Naver Map
          reference: "이런 점이 좋았어요") -- real counts from real submitted tags only,
          never fabricated. Collected in bank-mfe's ReviewOrderCard, displayed here. */}
      {tagEntries.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {tagEntries.map(([tag, count]) => (
            <div key={tag} style={{ display: 'flex', justifyContent: 'space-between', background: '#F2F4F6', borderRadius: '8px', padding: '8px 12px' }}>
              <span style={{ fontSize: '13px', color: TEXT }}>{EATS_GOOD_POINT_LABELS[tag] ?? tag}</span>
              <span style={{ fontSize: '13px', fontWeight: 700, color: TEXT }}>{count}</span>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

function PhotosTab({ detail }: { detail: MapPlaceDetail }) {
  const photos = detail.photoUrl ? [detail.photoUrl, ...detail.photoUrls.filter((p) => p !== detail.photoUrl)] : detail.photoUrls;
  if (photos.length === 0) return <p style={{ fontSize: '13px', color: TEXT_TERTIARY }}>No photos yet.</p>;
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
      {photos.map((url, i) => (
        <img key={i} src={url} alt="" style={{ width: '100%', aspectRatio: '1', borderRadius: '8px', objectFit: 'cover' }} />
      ))}
    </div>
  );
}

function NewsTab({ detail }: { detail: MapPlaceDetail }) {
  if (detail.updates.length === 0) return <p style={{ fontSize: '13px', color: TEXT_TERTIARY }}>No updates yet.</p>;
  const labelColor: Record<string, string> = { NOTICE: '#22B07D', EVENT: '#22B07D', PROMO: 'var(--itunda-indigo)' };
  return (
    <>
      {detail.updates.map((update) => (
        <div key={update.id} style={{ display: 'flex', flexDirection: 'column', gap: '4px', paddingBottom: '10px', borderBottom: `1px solid ${DIVIDER}` }}>
          <span style={{ fontSize: '11px', fontWeight: 700, color: labelColor[update.label] ?? TEXT_SECONDARY }}>{update.label}</span>
          <p style={{ fontSize: '13px', fontWeight: 700, color: TEXT }}>{update.title}</p>
          <p style={{ fontSize: '12px', color: TEXT_SECONDARY }}>{update.body}</p>
          {(update.periodStart || update.periodEnd) && (
            <span style={{ fontSize: '11px', color: TEXT_TERTIARY }}>
              {update.periodStart?.slice(0, 10) ?? ''} ~ {update.periodEnd?.slice(0, 10) ?? ''}
            </span>
          )}
          <span style={{ fontSize: '11px', color: TEXT_TERTIARY }}>♡ {update.likeCount}</span>
        </div>
      ))}
    </>
  );
}

function InfoTab({ detail }: { detail: MapPlaceDetail }) {
  const hasAny = detail.category || detail.openingHours || detail.phoneNumber;
  if (!hasAny) return <p style={{ fontSize: '13px', color: TEXT_TERTIARY }}>No additional info yet.</p>;
  return (
    <>
      {detail.category && <p style={{ fontSize: '13px', color: TEXT }}>Category: {detail.category}</p>}
      {detail.openingHours && <p style={{ fontSize: '13px', color: TEXT }}>Hours: {detail.openingHours}</p>}
      {detail.phoneNumber && <p style={{ fontSize: '13px', color: TEXT }}>Phone: {detail.phoneNumber}</p>}
    </>
  );
}
