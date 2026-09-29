// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// vehicle valuation tracker (own lib/vehicles.ts data layer, exactly one external
// call site -- `<MyVehiclesCard />` inside MyView).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState, ErrorCard } from './EmptyState';
import {
  fetchMyVehicles, fetchVehicleValuation, registerVehicle, removeVehicle, updateVehicleMileage,
  type Vehicle, type VehicleValuation,
} from './lib/vehicles';

export function MyVehiclesCard() {
  const { t } = useI18n();
  const [vehicles, setVehicles] = useState<Vehicle[] | null>(null);
  const [valuations, setValuations] = useState<Record<string, VehicleValuation>>({});
  const [showCreate, setShowCreate] = useState(false);
  const [make, setMake] = useState('');
  const [model, setModel] = useState('');
  const [modelYear, setModelYear] = useState(String(new Date().getFullYear()));
  const [purchasePrice, setPurchasePrice] = useState('');
  const [purchaseDate, setPurchaseDate] = useState('');
  const [mileageKm, setMileageKm] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real fix (2026-09-07, Vehicle product-completeness pass) -- a failed load
  // previously fell through silently (`.catch(() => {})`), leaving the user staring
  // at the same "No vehicles added yet" empty state a genuine zero-vehicles user
  // would see, with no way to tell the two apart or retry. Distinct from `error`
  // above, which is scoped to mutations (create/update/remove).
  const [loadError, setLoadError] = useState<string | null>(null);
  // Real fix (2026-09-07): replaces a raw window.prompt() for updating mileage --
  // Android/iOS both already built a real dialog for this; web never matched.
  // Same inline editingId/Save/Cancel row-edit pattern
  // MyProductSubscriptionsCard.tsx's own startEdit/handleSaveEdit already establish.
  const [editingMileageId, setEditingMileageId] = useState<string | null>(null);
  const [editMileageValue, setEditMileageValue] = useState('');

  const load = () => {
    setLoadError(null);
    fetchMyVehicles()
      .then((list) => {
        setVehicles(list);
        Promise.all(list.map((v) => fetchVehicleValuation(v.id).then((val) => [v.id, val] as const)))
          .then((pairs) => setValuations(Object.fromEntries(pairs)))
          .catch(() => {});
      })
      .catch((err) => {
        setLoadError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
  };

  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const price = Number(purchasePrice);
    const mileage = Number(mileageKm);
    const year = Number(modelYear);
    if (!make.trim() || !model.trim() || !(price > 0) || !purchaseDate || mileage < 0) return;
    setBusy(true);
    setError(null);
    try {
      await registerVehicle(make.trim(), model.trim(), year, price, purchaseDate, mileage);
      setMake('');
      setModel('');
      setPurchasePrice('');
      setPurchaseDate('');
      setMileageKm('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const startEditMileage = (vehicle: Vehicle) => {
    setEditingMileageId(vehicle.id);
    setEditMileageValue(String(vehicle.mileageKm));
    setError(null);
  };

  const handleSaveMileage = async (vehicleId: string) => {
    const newMileage = Number(editMileageValue);
    if (!(newMileage >= 0)) return;
    setBusyId(vehicleId);
    setError(null);
    try {
      await updateVehicleMileage(vehicleId, newMileage);
      setEditingMileageId(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleRemove = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await removeVehicle(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>My vehicles</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? 'Cancel' : '+ Add'}
        </button>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Estimated resale value based on age and mileage -- itunda's own general estimate, not a market comp.
      </p>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Make (e.g. Toyota)" value={make} onChange={(e) => setMake(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder="Model (e.g. RAV4)" value={model} onChange={(e) => setModel(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder="Model year" value={modelYear} onChange={(e) => setModelYear(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder="Purchase price (RWF)" value={purchasePrice} onChange={(e) => setPurchasePrice(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} max={new Date().toISOString().slice(0, 10)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder="Current mileage (km)" value={mileageKm} onChange={(e) => setMileageKm(e.target.value)} min="0" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? 'Adding…' : 'Add vehicle'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {loadError && <ErrorCard message={loadError} onRetry={load} />}
      {!loadError && vehicles === null && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {!loadError && vehicles !== null && vehicles.length === 0 && (
        <EmptyState message="No vehicles added yet — add one to track its value and get real offers." />
      )}

      {!loadError && (vehicles ?? []).map((v) => {
        const valuation = valuations[v.id];
        return (
          <div key={v.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{v.modelYear} {v.make} {v.model}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                  {v.mileageKm.toLocaleString('en-US')} km
                  {valuation && (
                    <> · {valuation.ageYears} {valuation.ageYears === 1 ? 'year' : 'years'} old · expected {valuation.expectedMileageKm.toLocaleString('en-US')} km</>
                  )}
                </p>
              </div>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === v.id} onClick={() => startEditMileage(v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Update km
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === v.id} onClick={() => handleRemove(v.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Remove
                </button>
              </div>
            </div>
            {editingMileageId === v.id && (
              <div style={{ display: 'flex', gap: '6px', marginTop: '8px', alignItems: 'center' }}>
                <input
                  type="number" min="0" value={editMileageValue} onChange={(e) => setEditMileageValue(e.target.value)}
                  placeholder="Mileage (km)"
                  style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
                />
                <button
                  className="itunda-btn itunda-btn-primary" disabled={busyId === v.id || !(Number(editMileageValue) >= 0)}
                  onClick={() => handleSaveMileage(v.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}
                >
                  {busyId === v.id ? '…' : 'Save'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" onClick={() => setEditingMileageId(null)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
                  Cancel
                </button>
              </div>
            )}
            {valuation && (
              <div style={{ marginTop: '8px', display: 'flex', gap: '12px', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                <span>Now: <strong style={{ color: 'var(--itunda-grey-900)' }}>{valuation.currentEstimatedValue.toLocaleString('en-US')} RWF</strong></span>
                <span>+1y: {valuation.estimatedValueIn1Year.toLocaleString('en-US')}</span>
                <span>+2y: {valuation.estimatedValueIn2Years.toLocaleString('en-US')}</span>
                <span>+3y: {valuation.estimatedValueIn3Years.toLocaleString('en-US')}</span>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
