// Extracted from InsuranceView.tsx (2026-09-03, file-size-lint crossing 500 lines) --
// real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
// crop insurance -- see lib/weatherIndexInsurance.ts's own doc comment for the full sourced
// account. Genuinely, structurally distinct from InsuranceView's claims-based plans: no
// individual claim is ever filed here. Rendered as a section inside InsuranceView rather
// than a separate top-level tab -- it's still "insurance" from the customer's point of view.
import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { useDeferredLoading } from './useDeferredLoading';
import {
  fetchCropIndexCatalog, enrollInCropIndexPolicy, fetchMyCropIndexPolicies, cancelCropIndexPolicy,
  type CropIndexCatalogEntry, type CropIndexPolicy, type WeatherIndexCropType,
} from './lib/weatherIndexInsurance';

export function CropWeatherIndexSection() {
  const [catalog, setCatalog] = useState<CropIndexCatalogEntry[] | null>(null);
  const showCatalogSkeleton = useDeferredLoading(!catalog);
  const [policies, setPolicies] = useState<CropIndexPolicy[]>([]);
  const [cropType, setCropType] = useState<WeatherIndexCropType>('MAIZE');
  const [district, setDistrict] = useState('');
  const [season, setSeason] = useState('2026B');
  const [insuredAmount, setInsuredAmount] = useState('100000');
  const [enrolling, setEnrolling] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchCropIndexCatalog().then(setCatalog).catch(() => setCatalog([]));
    fetchMyCropIndexPolicies().then(setPolicies).catch(() => setPolicies([]));
  };
  useEffect(load, []);

  const selectedRate = catalog?.find((c) => c.cropType === cropType)?.premiumRatePercent ?? 0;
  const amountNum = Number(insuredAmount || '0');
  const computedPremium = Number.isFinite(amountNum) ? Math.round(amountNum * selectedRate) / 100 : 0;

  const handleEnroll = async () => {
    if (!district.trim() || !season.trim() || !Number.isFinite(amountNum) || amountNum <= 0) {
      setError('Enter a district, a season, and an insured amount greater than zero.');
      return;
    }
    setEnrolling(true);
    setError(null);
    setMessage(null);
    try {
      const policy = await enrollInCropIndexPolicy(cropType, district.trim(), season.trim(), amountNum);
      setMessage(`Enrolled — premium ${policy.premiumAmount.toLocaleString('en-US')} RWF charged to your account.`);
      setDistrict('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not enroll in crop weather-index cover.');
    } finally {
      setEnrolling(false);
    }
  };

  const handleCancel = async (policyId: string) => {
    setBusyId(policyId);
    setError(null);
    setMessage(null);
    try {
      await cancelCropIndexPolicy(policyId);
      setMessage('Policy cancelled and premium refunded to your account.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this policy.');
    } finally {
      setBusyId(null);
    }
  };

  if (!catalog) {
    return showCatalogSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Crop Weather Insurance</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
          Rwanda's real National Agricultural Insurance Scheme model: if your district's official rainfall for this
          season falls below the drought threshold, every enrolled farmer in that district and season is paid
          automatically — no claim needed. The season's rainfall figure is transcribed by an admin from the real
          published NISR/Rwanda Meteorology Agency bulletin, not a live satellite feed.
        </p>
      </div>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}

      {policies.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {policies.map((p) => (
            <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{p.cropType.replace('_', ' ')} · {p.district} {p.season}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                  {p.status} · insured {p.insuredAmount.toLocaleString('en-US')} RWF · premium {p.premiumAmount.toLocaleString('en-US')} RWF
                </p>
              </div>
              {p.status === 'ENROLLED' && (
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === p.id} onClick={() => handleCancel(p.id)}>
                  {busyId === p.id ? '...' : 'Cancel'}
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <select value={cropType} onChange={(e) => setCropType(e.target.value as WeatherIndexCropType)} style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}>
          {catalog.map((c) => (
            <option key={c.cropType} value={c.cropType}>{c.name} — {c.premiumRatePercent}% premium rate</option>
          ))}
        </select>
        <input
          placeholder="District (e.g. Nyagatare)"
          value={district}
          onChange={(e) => setDistrict(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
        />
        <input
          placeholder="Season (e.g. 2026B)"
          value={season}
          onChange={(e) => setSeason(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
        />
        <input
          type="number"
          placeholder="Insured amount (RWF, max 500,000)"
          value={insuredAmount}
          onChange={(e) => setInsuredAmount(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
        />
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Premium: {computedPremium.toLocaleString('en-US')} RWF, charged now to your account.</p>
        <button className="itunda-btn itunda-btn-secondary" disabled={enrolling} onClick={handleEnroll}>
          {enrolling ? '...' : 'Enroll'}
        </button>
      </div>
    </div>
  );
}
