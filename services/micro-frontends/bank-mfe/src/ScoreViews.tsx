// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Two
// small, self-contained "see your own score" screens (own lib/creditScore.ts /
// lib/trustScore.ts data layers, each with exactly one external call site), paired
// in one file since both are small and share the same shape.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { fetchCreditScore, fetchCreditScoreSuggestions, type CreditScoreResult, type CreditScoreSuggestion } from './lib/creditScore';
import { fetchTrustScore, type TrustScoreResult } from './lib/trustScore';
import { useDeferredLoading } from './useDeferredLoading';

// Real "alternative data" credit score (2026-07-22) -- found fully built on the
// backend (rw.itunda.creditscore) with zero client UI anywhere. Not a real bureau
// score -- computed live from a user's own real transaction/loan/savings/KYC history.
export function CreditScoreView() {
  const { t } = useI18n();
  const [result, setResult] = useState<CreditScoreResult | null>(null);
  const [suggestions, setSuggestions] = useState<CreditScoreSuggestion[] | null>(null);
  const showSkeleton = useDeferredLoading(!result);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCreditScore()
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    // Real Toss 신용플러스-style suggestions (2026-07-26) -- see
    // CreditScoreService.getImprovementSuggestions's own doc comment. Loaded alongside
    // the score itself, not gated behind it -- a real failure here shouldn't block the
    // score from rendering.
    fetchCreditScoreSuggestions()
      .then(setSuggestions)
      .catch(() => setSuggestions([]));
  }, []);

  if (!result) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : (showSkeleton ? <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} /> : null);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Your score</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{result.score} / 850</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Based on your own account activity, not a bureau report.</p>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>What makes up your score</h3>
        {result.factors.map((f) => (
          <div key={f.name} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <div>
              <p>{f.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{f.description}</p>
            </div>
            <span>+{f.points}</span>
          </div>
        ))}
      </div>
      {suggestions !== null && suggestions.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>What would raise your score</h3>
          {suggestions.map((s) => (
            <div key={s.action} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
              <div>
                <p>{s.action}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{s.description}</p>
              </div>
              <span style={{ color: 'var(--itunda-indigo)', fontWeight: 700 }}>+{s.pointsGain}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real Karrot-Score-style trust/reputation view (item 152) -- see lib/trustScore.ts's
// own doc comment. Every Hood card already shows a batch-read trustScore badge for the
// OTHER party (seller/poster/lister); this is the separate "see your own full factor
// breakdown" screen, mirroring CreditScoreView's own shape exactly.
export function TrustScoreView() {
  const { t } = useI18n();
  const [result, setResult] = useState<TrustScoreResult | null>(null);
  const showSkeleton = useDeferredLoading(!result);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTrustScore()
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  if (!result) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : (showSkeleton ? <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} /> : null);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Your trust score</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{result.score} / 1000</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>How your neighbors see you on Marketplace, Jobs, and Property.</p>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>What makes up your score</h3>
        {result.factors.map((f) => (
          <div key={f.name} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <div>
              <p>{f.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{f.description}</p>
            </div>
            <span>+{f.points}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
