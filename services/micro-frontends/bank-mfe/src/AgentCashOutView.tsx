// Real "cash out at an agent" screen -- see lib/agentWithdrawal.ts's own doc
// comment for the full sourced account. Mirrors Android's AgentCashScreen.kt
// exactly (amount entry -> create a one-time, 10-minute-expiry code -> show it as
// a QR/text to the agent -> cancel if unused), the reference this screen ports
// since it's the only platform that already had this feature.

import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { ApiError } from './lib/api';
import { IconBack } from './icons/ItundaIcons';
import { useI18n } from './i18n/I18nContext';
import {
  fetchAgentWithdrawalAuthorizations, createAgentWithdrawalAuthorization, cancelAgentWithdrawalAuthorization,
  type AgentWithdrawalAuthorization,
} from './lib/agentWithdrawal';
import { randomUUID } from './lib/uuid';

function AuthorizationCard({ authorization, onCancelled }: { authorization: AgentWithdrawalAuthorization; onCancelled: () => void }) {
  const { t } = useI18n();
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const active = authorization.status === 'ACTIVE';

  useEffect(() => {
    if (!active) { setQrDataUrl(null); return; }
    QRCode.toDataURL(authorization.code, { width: 140, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
  }, [active, authorization.code]);

  const handleCancel = async () => {
    setCancelling(true);
    setError(null);
    try {
      await cancelAgentWithdrawalAuthorization(authorization.code);
      onCancelled();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="itunda-card" style={{ padding: '16px', marginBottom: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
            {authorization.amount.toLocaleString('en-US')} RWF
          </p>
          {active ? (
            <>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Withdrawal code</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, letterSpacing: '1px', color: 'var(--itunda-grey-900)' }}>{authorization.code}</p>
            </>
          ) : (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{authorization.status}</p>
          )}
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            Expires: {new Date(authorization.expiresAt).toLocaleString()}
          </p>
        </div>
        {active && qrDataUrl && <img src={qrDataUrl} alt={`QR code for withdrawal code ${authorization.code}`} width={90} height={90} />}
      </div>
      {active && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>
          For your security, give this code only to an itunda agent at the counter. It can be used once for the exact amount shown.
        </p>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
      {active && (
        <button className="itunda-btn itunda-btn-secondary" disabled={cancelling} onClick={handleCancel} style={{ marginTop: '10px' }}>
          {cancelling ? 'Cancelling…' : 'Cancel code'}
        </button>
      )}
    </div>
  );
}

export function AgentCashOutView({ onBack, onFindNearbyAgent }: { onBack: () => void; onFindNearbyAgent: () => void }) {
  const { t } = useI18n();
  const [authorizations, setAuthorizations] = useState<AgentWithdrawalAuthorization[] | null>(null);
  const [amount, setAmount] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState(randomUUID());
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchAgentWithdrawalAuthorizations().then(setAuthorizations).catch(() => setAuthorizations([]));
  };
  useEffect(load, []);

  // Editing the amount is a new intent -- a fresh Idempotency-Key, same convention
  // Android's AgentCashScreen.kt already established, so a retry of the SAME create
  // attempt (e.g. a network error) reuses one key while a changed amount never
  // replays a stale one.
  const handleAmountChange = (value: string) => {
    setAmount(value);
    setIdempotencyKey(randomUUID());
  };

  const handleCreate = async () => {
    const parsed = Number(amount);
    if (!Number.isFinite(parsed) || parsed <= 0) return;
    setCreating(true);
    setError(null);
    try {
      await createAgentWithdrawalAuthorization(parsed, idempotencyKey);
      setAmount('');
      setIdempotencyKey(randomUUID());
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCreating(false);
    }
  };

  const activeCount = (authorizations ?? []).filter((a) => a.status === 'ACTIVE').length;

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
          <h2 style={{ margin: 0, fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>Cash out at an agent</h2>
        </div>

        <div style={{ padding: '0 20px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
            Create a one-time code, then show it to the agent only when they are ready to hand over cash. Codes expire in 10 minutes.
          </p>

          <button className="itunda-btn itunda-btn-secondary" onClick={onFindNearbyAgent} style={{ width: '100%', marginBottom: '16px' }}>
            Find a nearby itunda agent
          </button>

          <div className="itunda-flat-section" style={{ marginBottom: '16px' }}>
            <input
              type="number"
              placeholder="Amount (RWF)"
              value={amount}
              onChange={(e) => handleAmountChange(e.target.value)}
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }}
            />
            <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
              {[10000, 100000].map((quick) => (
                <button key={quick} className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => handleAmountChange(String(quick))}>
                  {quick.toLocaleString('en-US')} RWF
                </button>
              ))}
            </div>
            {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
            <button
              className="itunda-btn itunda-btn-primary"
              disabled={creating || !Number.isFinite(Number(amount)) || Number(amount) <= 0}
              onClick={handleCreate}
              style={{ width: '100%' }}
            >
              {creating ? 'Creating…' : 'Create withdrawal code'}
            </button>
          </div>

          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>
            Your codes ({activeCount} active)
          </h3>
          {authorizations === null && <div className="itunda-flat-section skeleton" style={{ height: '120px' }} />}
          {authorizations !== null && authorizations.length === 0 && (
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-500)' }}>No withdrawal codes yet.</p>
          )}
          {authorizations !== null && authorizations.length > 0 && authorizations.map((a) => (
            <AuthorizationCard key={a.id} authorization={a} onCancelled={load} />
          ))}
        </div>
      </div>
    </div>
  );
}
