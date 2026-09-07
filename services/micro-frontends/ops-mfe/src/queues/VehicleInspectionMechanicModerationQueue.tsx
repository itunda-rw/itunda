import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import {
  fetchVehicleInspectionMechanics, reactivateVehicleInspectionMechanic, suspendVehicleInspectionMechanic,
  type VehicleInspectionMechanic,
} from '../lib/vehicleInspectionMechanicAdminQueue';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function MechanicCard({ mechanic, onDecided }: { mechanic: VehicleInspectionMechanic; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = async () => {
    setPending(true);
    setError(null);
    try {
      if (mechanic.suspended) {
        await reactivateVehicleInspectionMechanic(mechanic.mechanicId);
      } else {
        await suspendVehicleInspectionMechanic(mechanic.mechanicId);
      }
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this mechanic.');
    } finally {
      setPending(false);
    }
  };

  const buttonLabel = (): string => {
    if (pending) return '…';
    return mechanic.suspended ? 'Reactivate' : 'Suspend';
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px' }}>
      <div style={{ flex: 1 }}>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{mechanic.businessName}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>Mechanic {mechanic.mechanicId}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          {mechanic.available ? 'Taking new bookings' : 'Not taking new bookings'} · Registered {new Date(mechanic.createdAt).toLocaleString()}
        </p>
        {mechanic.suspended && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }}>Suspended — cannot be booked for new inspections.</p>}
        {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      </div>
      <button
        className={mechanic.suspended ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-danger'}
        style={{ padding: '10px 18px' }}
        disabled={pending}
        onClick={toggle}
      >
        {buttonLabel()}
      </button>
    </div>
  );
}

export default function VehicleInspectionMechanicModerationQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchVehicleInspectionMechanics);

  return (
    <div>
      <QueueHeader title="Vehicle inspection mechanics" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No mechanics have registered yet." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((mechanic) => (
            <MechanicCard key={mechanic.mechanicId} mechanic={mechanic} onDecided={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
