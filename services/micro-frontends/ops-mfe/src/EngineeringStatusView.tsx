import { useCallback, useEffect, useState } from 'react';
import { Activity, AlertTriangle, CheckCircle2, Clock3, RefreshCw, XCircle } from 'lucide-react';
import { apiFetch } from '../lib/api';

type CheckResult = 'success' | 'failure' | 'pending' | 'cancelled' | 'unknown';
type EngineeringEnvironment = 'local' | 'sandbox' | 'staging' | 'production';

interface EngineeringCheck {
  id: string;
  name: string;
  result: CheckResult;
  commitSha: string;
  environment: EngineeringEnvironment;
  source: string;
  observedAt: string;
  detailsUrl?: string;
}

interface EngineeringStatusResponse {
  generatedAt: string;
  repository: string;
  branch: string;
  headSha: string;
  checks: EngineeringCheck[];
}

const FRESHNESS_LIMIT_MS = 15 * 60 * 1000;

function getDisplayResult(check: EngineeringCheck, now: number): CheckResult {
  if (!check.commitSha || !check.source || !Number.isFinite(Date.parse(check.observedAt))) return 'unknown';
  if (now - Date.parse(check.observedAt) > FRESHNESS_LIMIT_MS || Date.parse(check.observedAt) > now + 60_000) return 'unknown';
  return check.result;
}

const resultLabel: Record<CheckResult, string> = {
  success: 'Success',
  failure: 'Failed',
  pending: 'In progress',
  cancelled: 'Cancelled',
  unknown: 'Unknown / stale',
};

const resultIcon = {
  success: CheckCircle2,
  failure: XCircle,
  pending: Clock3,
  cancelled: AlertTriangle,
  unknown: AlertTriangle,
};

export default function EngineeringStatusView() {
  const [status, setStatus] = useState<EngineeringStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadStatus = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await apiFetch<EngineeringStatusResponse>('/api/v1/internal/engineering/status');
      if (!response || !response.headSha || !Array.isArray(response.checks) || response.checks.length === 0) {
        throw new Error('The status source returned incomplete data. Readiness cannot be confirmed.');
      }
      setStatus(response);
    } catch (cause) {
      setStatus(null);
      setError(cause instanceof Error ? cause.message : 'Engineering status is unavailable.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadStatus(); }, [loadStatus]);

  const now = Date.now();
  const checks = status?.checks.map((check) => ({ ...check, displayResult: getDisplayResult(check, now) }));
  const ready = Boolean(status && checks?.length && checks.every((check) =>
    check.displayResult === 'success' && check.commitSha === status.headSha
  ));

  return (
    <section style={{ display: 'grid', gap: '20px' }}>
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 700, color: 'var(--itunda-text-primary)' }}>Engineering status</h2>
          <p style={{ margin: '8px 0 0', color: 'var(--itunda-text-secondary)', fontSize: '14px' }}>
            Build and release signals tied to an exact commit. Unknown never means healthy.
          </p>
        </div>
        <button onClick={() => void loadStatus()} disabled={loading} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '10px 12px', borderRadius: 'var(--itunda-control-radius, 12px)', color: 'var(--itunda-text-primary)', background: 'var(--itunda-surface-default)' }}>
          <RefreshCw size={16} /> {loading ? 'Checking…' : 'Refresh'}
        </button>
      </header>

      {error && (
        <div role="alert" style={{ display: 'flex', gap: '12px', padding: '16px', borderRadius: '16px', background: 'var(--itunda-surface-subtle, var(--itunda-surface-default))', color: 'var(--itunda-text-primary)' }}>
          <AlertTriangle size={20} />
          <div>
            <strong>Readiness cannot be confirmed</strong>
            <p style={{ margin: '6px 0 0', color: 'var(--itunda-text-secondary)' }}>{error}</p>
            <p style={{ margin: '6px 0 0', color: 'var(--itunda-text-secondary)', fontSize: '13px' }}>
              The status endpoint must be configured on the server. This view does not fabricate CI results.
            </p>
          </div>
        </div>
      )}

      {status && checks && (
        <>
          <div style={{ padding: '20px', borderRadius: '16px', background: 'var(--itunda-surface-default)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: ready ? 'var(--itunda-brand)' : 'var(--itunda-text-primary)' }}>
              <Activity size={20} />
              <strong>{ready ? 'All reported checks are fresh for this commit' : 'Not ready to declare healthy'}</strong>
            </div>
            <p style={{ margin: '10px 0 0', color: 'var(--itunda-text-secondary)', fontSize: '13px', overflowWrap: 'anywhere' }}>
              {status.repository} · {status.branch} · commit {status.headSha}
            </p>
            <p style={{ margin: '6px 0 0', color: 'var(--itunda-text-tertiary)', fontSize: '12px' }}>
              Snapshot: {Number.isFinite(Date.parse(status.generatedAt)) ? new Date(status.generatedAt).toLocaleString() : 'timestamp unavailable'}
            </p>
          </div>
          <div style={{ display: 'grid', gap: '10px' }}>
            {checks.map((check) => {
              const Icon = resultIcon[check.displayResult];
              return (
                <article key={check.id} style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', padding: '16px', borderRadius: '14px', background: 'var(--itunda-surface-default)' }}>
                  <Icon size={20} color={check.displayResult === 'success' ? 'var(--itunda-brand)' : 'var(--itunda-text-secondary)'} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <strong style={{ color: 'var(--itunda-text-primary)' }}>{check.name}</strong>
                    <p style={{ margin: '5px 0', color: 'var(--itunda-text-secondary)', fontSize: '13px' }}>
                      {resultLabel[check.displayResult]} · {check.environment} · {check.source}
                    </p>
                    <p style={{ margin: 0, color: 'var(--itunda-text-tertiary)', fontSize: '12px', overflowWrap: 'anywhere' }}>
                      {check.commitSha ? `Commit ${check.commitSha.slice(0, 12)} · observed ${new Date(check.observedAt).toLocaleString()}` : 'Commit SHA missing'}
                    </p>
                  </div>
                  {check.detailsUrl && /^https:\/\//.test(check.detailsUrl) && <a href={check.detailsUrl} target="_blank" rel="noreferrer" style={{ color: 'var(--itunda-brand)', fontSize: '13px' }}>Details</a>}
                </article>
              );
            })}
          </div>
        </>
      )}
    </section>
  );
}
