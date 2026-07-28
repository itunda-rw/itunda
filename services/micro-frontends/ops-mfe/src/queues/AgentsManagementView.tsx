import { useCallback, useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import {
  assignAgentOperator,
  fetchAgentOperators,
  fetchAgents,
  fundAgentTill,
  registerAgent,
  setAgentOperatorStatus,
  setAgentStatus,
  type Agent,
  type AgentOperator,
} from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

// Real MTN MoMo/Airtel Money-style physical cash-in/cash-out agent network -- Agent
// management (item 129), the "register/list/suspend an agent, fund a real till float"
// half of AgentAdminController this ops-mfe view had zero client for. Cash-in/cash-out/
// operator-assignment are real teller actions, deliberately left for a future,
// separate agent-operator client rather than folded into this admin register/status/
// float view.

function RegisterAgentForm({ onRegistered }: { onRegistered: () => void }) {
  const [displayName, setDisplayName] = useState('');
  const [cashInLimit, setCashInLimit] = useState('');
  const [cashOutLimit, setCashOutLimit] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await registerAgent(displayName, Number(cashInLimit), Number(cashOutLimit || cashInLimit));
      setDisplayName('');
      setCashInLimit('');
      setCashOutLimit('');
      onRegistered();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not register this agent.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>Register a new agent</p>
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="Display name (e.g. Kigali Heights Agent)"
          required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number"
          value={cashInLimit}
          onChange={(e) => setCashInLimit(e.target.value)}
          placeholder="Daily cash-in limit"
          required
          min={0}
          style={{ width: '180px', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number"
          value={cashOutLimit}
          onChange={(e) => setCashOutLimit(e.target.value)}
          placeholder="Daily cash-out limit (defaults to cash-in)"
          min={0}
          style={{ width: '220px', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? '…' : 'Register'}
        </button>
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
          {error}
        </p>
      )}
    </form>
  );
}

function AgentRow({ agent, onChanged }: { agent: Agent; onChanged: () => void }) {
  const [busy, setBusy] = useState<'status' | 'fund' | null>(null);
  const [funding, setFunding] = useState(false);
  const [amount, setAmount] = useState('');
  const [reference, setReference] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [showingOperators, setShowingOperators] = useState(false);

  const toggleStatus = async () => {
    setBusy('status');
    setError(null);
    try {
      await setAgentStatus(agent.id, agent.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE');
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this agent.');
    } finally {
      setBusy(null);
    }
  };

  const fund = async () => {
    setBusy('fund');
    setError(null);
    try {
      await fundAgentTill(agent.id, Number(amount), reference);
      setFunding(false);
      setAmount('');
      setReference('');
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not fund this till.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
    <tr style={{ borderTop: '1px solid var(--toss-grey-100)' }}>
      <td style={{ padding: '12px 16px' }}>
        <p style={{ fontWeight: 600, color: 'var(--toss-grey-900)' }}>{agent.displayName}</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{agent.id}</p>
      </td>
      <td style={{ padding: '12px 16px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: agent.status === 'ACTIVE' ? 'var(--toss-green)' : '#E53935' }}>
          {agent.status}
        </span>
      </td>
      <td style={{ padding: '12px 16px', fontSize: '13px' }}>
        {agent.dailyCashInLimit.toLocaleString()} / {agent.dailyCashOutLimit.toLocaleString()} RWF
      </td>
      <td style={{ padding: '12px 16px' }}>
        {funding ? (
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
            <input
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="Amount"
              style={{ width: '100px', padding: '6px 8px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            />
            <input
              type="text"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              placeholder="Reference"
              style={{ width: '120px', padding: '6px 8px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            />
            <button className="toss-btn toss-btn-primary" style={{ padding: '6px 10px', fontSize: '12px' }} disabled={busy !== null || !amount || !reference} onClick={fund}>
              {busy === 'fund' ? '…' : 'Confirm'}
            </button>
            <button className="toss-btn toss-btn-secondary" style={{ padding: '6px 10px', fontSize: '12px' }} disabled={busy !== null} onClick={() => setFunding(false)}>
              Cancel
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', gap: '6px' }}>
            <button className="toss-btn toss-btn-secondary" style={{ padding: '6px 10px', fontSize: '12px' }} disabled={busy !== null} onClick={() => setFunding(true)}>
              Fund till
            </button>
            <button
              className={agent.status === 'ACTIVE' ? 'toss-btn toss-btn-danger' : 'toss-btn toss-btn-primary'}
              style={{ padding: '6px 10px', fontSize: '12px' }}
              disabled={busy !== null}
              onClick={toggleStatus}
            >
              {busy === 'status' ? '…' : agent.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
            </button>
            <button
              className="toss-btn toss-btn-secondary"
              style={{ padding: '6px 10px', fontSize: '12px' }}
              disabled={busy !== null}
              onClick={() => setShowingOperators((v) => !v)}
            >
              {showingOperators ? 'Hide operators' : 'Operators'}
            </button>
          </div>
        )}
        {error && (
          <p style={{ fontSize: '12px', color: '#E53935', margin: '6px 0 0' }} role="alert">
            {error}
          </p>
        )}
      </td>
    </tr>
    {showingOperators && <OperatorsPanel agentId={agent.id} />}
    </>
  );
}

function OperatorsPanel({ agentId }: { agentId: string }) {
  // useQueue's internal useEffect depends on this fetcher's identity -- an inline
  // arrow function here would recreate on every render and cause an infinite refetch
  // loop, so it's memoized on agentId alone (which only changes when a different
  // agent's row is expanded).
  const fetcher = useCallback(() => fetchAgentOperators(agentId), [agentId]);
  const { items, error, reload } = useQueue(fetcher);
  const [newUserId, setNewUserId] = useState('');
  const [assigning, setAssigning] = useState(false);
  const [assignError, setAssignError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const assign = async () => {
    setAssigning(true);
    setAssignError(null);
    try {
      await assignAgentOperator(agentId, newUserId.trim());
      setNewUserId('');
      reload();
    } catch (err) {
      setAssignError(err instanceof ApiError ? err.message : 'Could not assign this operator.');
    } finally {
      setAssigning(false);
    }
  };

  const toggle = async (operator: AgentOperator) => {
    setTogglingId(operator.userId);
    setAssignError(null);
    try {
      await setAgentOperatorStatus(agentId, operator.userId, !operator.isActive);
      reload();
    } catch (err) {
      setAssignError(err instanceof ApiError ? err.message : 'Could not update this operator.');
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <tr>
      <td colSpan={4} style={{ padding: '0 16px 16px', backgroundColor: 'var(--toss-grey-100)' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', paddingTop: '12px' }}>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="text"
              value={newUserId}
              onChange={(e) => setNewUserId(e.target.value)}
              placeholder="User id to assign as an operator"
              style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            />
            <button className="toss-btn toss-btn-primary" style={{ padding: '8px 12px', fontSize: '12px' }} disabled={assigning || !newUserId.trim()} onClick={assign}>
              {assigning ? '…' : 'Assign'}
            </button>
          </div>
          {error && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }}>{error}</p>}
          {assignError && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }}>{assignError}</p>}
          {items === null ? (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading operators…</p>
          ) : items.length === 0 ? (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No operators assigned to this agent yet.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {items.map((operator) => (
                <div key={operator.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 10px', backgroundColor: 'var(--toss-white)', borderRadius: '8px' }}>
                  <span style={{ fontSize: '13px' }}>{operator.userId}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '12px', fontWeight: 600, color: operator.isActive ? 'var(--toss-green)' : '#E53935' }}>
                      {operator.isActive ? 'Active' : 'Inactive'}
                    </span>
                    <button
                      className="toss-btn toss-btn-secondary"
                      style={{ padding: '4px 8px', fontSize: '11px' }}
                      disabled={togglingId === operator.userId}
                      onClick={() => toggle(operator)}
                    >
                      {togglingId === operator.userId ? '…' : operator.isActive ? 'Deactivate' : 'Activate'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </td>
    </tr>
  );
}

export default function AgentsManagementView() {
  const { items, error, refreshing, reload } = useQueue(fetchAgents);

  return (
    <div>
      <QueueHeader title="Agents" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      <RegisterAgentForm onRegistered={reload} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No agents registered yet." />}
      {!error && items !== null && items.length > 0 && (
        <div className="toss-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--toss-grey-100)', textAlign: 'left' }}>
                {['Agent', 'Status', 'Daily limits (in / out)', 'Actions'].map((h) => (
                  <th key={h} style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((agent) => (
                <AgentRow key={agent.id} agent={agent} onChanged={reload} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
