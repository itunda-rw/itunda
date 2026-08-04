import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { ErrorCard, EmptyState } from '../components/EmptyState';
import { advanceDineInOrder, dineInTableQrPayload, fetchDineInOrders, type DineInOrder, type DineInOrderStatus } from '../lib/eats';
import type { Merchant } from '../lib/merchant';

// Real 배민오더-style table/QR in-store ordering, restaurant side -- see lib/eats.ts's
// own doc comment. Two independent sections: print/display a real per-table QR (top),
// and watch + progress real incoming table orders (below) -- mirrors DineInScreen.kt's
// own account of the backend field-for-field.
export default function DineInScreen({ merchant }: { merchant: Merchant }) {
  return (
    <div style={{ maxWidth: '640px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <TableQrGenerator restaurantId={merchant.id} />
      <DineInOrdersQueue />
    </div>
  );
}

function TableQrGenerator({ restaurantId }: { restaurantId: string }) {
  const [tableNumber, setTableNumber] = useState('');
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [generatedFor, setGeneratedFor] = useState<string | null>(null);

  const handleGenerate = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = tableNumber.trim();
    if (!trimmed) return;
    QRCode.toDataURL(dineInTableQrPayload(restaurantId, trimmed), { width: 200, margin: 1 }).then((dataUrl) => {
      setQrDataUrl(dataUrl);
      setGeneratedFor(trimmed);
    });
  };

  return (
    <div className="toss-card" style={{ padding: '20px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Table QR codes</h3>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        Print this and leave it on a table -- a customer scans it to order straight to that table.
      </p>
      <form onSubmit={handleGenerate} style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
        <input
          value={tableNumber} onChange={(e) => setTableNumber(e.target.value.slice(0, 50))}
          placeholder="Table number"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={!tableNumber.trim()}>Generate</button>
      </form>
      {qrDataUrl && (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <img src={qrDataUrl} alt={`QR code for table ${generatedFor}`} width={200} height={200} />
          <p style={{ fontSize: '13px', fontWeight: 700, marginTop: '6px' }}>Table {generatedFor}</p>
        </div>
      )}
    </div>
  );
}

const NEXT_ACTION: Partial<Record<DineInOrderStatus, { next: DineInOrderStatus; label: string }>> = {
  PLACED: { next: 'ACCEPTED', label: 'Accept order' },
  ACCEPTED: { next: 'PREPARING', label: 'Start preparing' },
  PREPARING: { next: 'SERVED', label: 'Mark served' },
};

const STATUS_LABEL: Record<DineInOrderStatus, string> = {
  PLACED: 'New order', ACCEPTED: 'Accepted', PREPARING: 'Preparing', SERVED: 'Served', CANCELLED: 'Cancelled',
};

function DineInOrdersQueue() {
  const [orders, setOrders] = useState<DineInOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = () => {
    fetchDineInOrders()
      .then((o) => { setOrders(o); setError(null); })
      .catch(() => setError("Couldn't reach itunda. Check your connection and try again."));
  };

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 8000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: DineInOrder) => {
    const action = NEXT_ACTION[order.status];
    if (!action) return;
    setBusyId(order.id);
    try {
      await advanceDineInOrder(order.id, action.next);
      refresh();
    } catch {
      setError("Couldn't update this order. Try again.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '10px' }}>Table orders</h3>
      {error && <ErrorCard message={error} onRetry={refresh} />}
      {orders === null ? (
        <div className="toss-card skeleton" style={{ height: '120px' }} />
      ) : (
        (() => {
          const active = orders.filter((o) => o.status === 'PLACED' || o.status === 'ACCEPTED' || o.status === 'PREPARING');
          if (active.length === 0) {
            return <EmptyState message="No open table orders right now." />;
          }
          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {active.map((order) => {
                const action = NEXT_ACTION[order.status];
                return (
                  <div key={order.id} className="toss-card" style={{ padding: '16px 18px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                      <span style={{ fontSize: '11px', fontWeight: 700, padding: '3px 8px', borderRadius: '8px', backgroundColor: 'var(--toss-grey-100)', color: 'var(--toss-grey-700)' }}>
                        {STATUS_LABEL[order.status]}
                      </span>
                      <span style={{ fontWeight: 700, fontSize: '13px' }}>{order.totalAmount.toLocaleString()} RWF</span>
                    </div>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>Table {order.tableNumber}</p>
                    {order.notes && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Note: {order.notes}</p>}
                    {action && (
                      <button
                        className="toss-btn toss-btn-primary"
                        onClick={() => handleAdvance(order)}
                        disabled={busyId === order.id}
                        style={{ width: '100%', marginTop: '10px' }}
                      >
                        {action.label}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          );
        })()
      )}
    </div>
  );
}
