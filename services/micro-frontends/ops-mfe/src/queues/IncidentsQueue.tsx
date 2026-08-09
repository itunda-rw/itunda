import { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { useQueue } from '../hooks/useQueue';
import { fetchIncidents, resolveIncident, type Incident } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function IncidentCard({ incident, onResolved }: { incident: Incident; onResolved: () => void }) {
  const [pending, setPending] = useState(false);
  const isOpen = incident.status === 'OPEN';

  const resolve = async () => {
    setPending(true);
    try {
      await resolveIncident(incident.id);
      onResolved();
    } finally {
      setPending(false);
    }
  };

  return (
    <div
      className="itunda-card"
      style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px', opacity: isOpen ? 1 : 0.65 }}
    >
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
          <span
            style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: isOpen ? 'var(--itunda-red)' : 'var(--itunda-green)',
              display: 'inline-block',
            }}
          />
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{incident.railDisplayName}</p>
        </div>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{incident.description}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          {incident.failureCount} failures · Opened {new Date(incident.openedAt).toLocaleString()}
          {incident.resolvedAt && ` · Resolved ${new Date(incident.resolvedAt).toLocaleString()}`}
        </p>
      </div>
      {isOpen ? (
        <button className="itunda-btn itunda-btn-primary" style={{ padding: '10px 18px', gap: '6px' }} disabled={pending} onClick={resolve}>
          <CheckCircle2 size={16} /> Resolve
        </button>
      ) : (
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-green)' }}>Resolved</span>
      )}
    </div>
  );
}

export default function IncidentsQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchIncidents);
  const openCount = items?.filter((i) => i.status === 'OPEN').length ?? null;

  return (
    <div>
      <QueueHeader title="Incidents" count={openCount} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No incidents recorded." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {[...items]
            .sort((a, b) => (a.status === b.status ? 0 : a.status === 'OPEN' ? -1 : 1))
            .map((incident) => (
              <IncidentCard key={incident.id} incident={incident} onResolved={reload} />
            ))}
        </div>
      )}
    </div>
  );
}
