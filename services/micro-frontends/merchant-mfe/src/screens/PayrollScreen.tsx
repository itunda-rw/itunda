import { useEffect, useState } from 'react';
import { CircleCheck, Trash2, Users } from 'lucide-react';
import { ApiError } from '../lib/api';
import {
  addPayrollEmployee,
  getPayrollRoster,
  removePayrollEmployee,
  runPayroll,
  type PayrollEmployee,
  type PayrollRunResult,
} from '../lib/merchant';

// Real B2B payroll UI -- see PayrollService.kt's own doc comment for why this is real
// wallet-to-wallet money movement, not a demo/simulation like CollectScreen's Card tab.
export default function PayrollScreen() {
  const [roster, setRoster] = useState<PayrollEmployee[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [runResult, setRunResult] = useState<PayrollRunResult | null>(null);

  const load = () => {
    setLoadError(null);
    getPayrollRoster()
      .then(setRoster)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : 'Could not load the payroll roster.'));
  };

  useEffect(load, []);

  if (runResult) {
    return <PayrollRunConfirmation result={runResult} onDone={() => { setRunResult(null); load(); }} />;
  }

  return (
    <div style={{ maxWidth: '640px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <AddEmployeeForm onAdded={load} />
      <RosterTable
        roster={roster}
        error={loadError}
        onReload={load}
        onRemove={(id) => removePayrollEmployee(id).then(load)}
        onRunPayroll={setRunResult}
      />
    </div>
  );
}

function AddEmployeeForm({ onAdded }: { onAdded: () => void }) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [salaryAmount, setSalaryAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await addPayrollEmployee(phoneNumber, Number(salaryAmount));
      setPhoneNumber('');
      setSalaryAmount('');
      onAdded();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add this employee.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card">
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>Add an employee</h2>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        Must be an existing itunda user's phone number -- payroll pays directly into their itunda wallet.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 2, minWidth: '180px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Phone number</span>
          <input
            type="tel"
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            placeholder="+250788123456"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '140px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Monthly salary (RWF)</span>
          <input
            type="number"
            min="1"
            step="1"
            value={salaryAmount}
            onChange={(e) => setSalaryAmount(e.target.value)}
            placeholder="150000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ height: '46px' }}>
          {submitting ? 'Adding…' : 'Add'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: '12px 0 0' }} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

function RosterTable({
  roster,
  error,
  onReload,
  onRemove,
  onRunPayroll,
}: {
  roster: PayrollEmployee[] | null;
  error: string | null;
  onReload: () => void;
  onRemove: (employeeId: string) => void;
  onRunPayroll: (result: PayrollRunResult) => void;
}) {
  const [runError, setRunError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const handleRunPayroll = async () => {
    setRunError(null);
    setRunning(true);
    try {
      const result = await runPayroll();
      onRunPayroll(result);
    } catch (err) {
      setRunError(err instanceof ApiError ? err.message : 'Could not run payroll.');
    } finally {
      setRunning(false);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">
          {error}
        </p>
        <button className="toss-btn toss-btn-secondary" onClick={onReload} style={{ marginTop: '12px' }}>
          Retry
        </button>
      </div>
    );
  }

  if (roster === null) {
    return <div className="toss-card">Loading…</div>;
  }

  const total = roster.reduce((acc, e) => acc + e.salaryAmount, 0);

  return (
    <div className="toss-card" style={{ padding: 0, overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Users size={18} color="var(--toss-blue)" />
          <h2 style={{ fontSize: '16px', fontWeight: 700 }}>Roster ({roster.length})</h2>
        </div>
        <button
          className="toss-btn toss-btn-primary"
          onClick={handleRunPayroll}
          disabled={running || roster.length === 0}
        >
          {running ? 'Running…' : `Run payroll (${total.toLocaleString()} RWF)`}
        </button>
      </div>
      {runError && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: '0 20px 16px' }} role="alert">
          {runError}
        </p>
      )}
      {roster.length === 0 ? (
        <p style={{ padding: '0 20px 20px', fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          No employees on the roster yet.
        </p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
          <thead>
            <tr style={{ backgroundColor: 'var(--toss-grey-100)', textAlign: 'left' }}>
              {['Employee', 'Monthly salary', ''].map((h) => (
                <th key={h} style={{ padding: '10px 20px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {roster.map((employee) => (
              <tr key={employee.id} style={{ borderTop: '1px solid var(--toss-grey-200)' }}>
                <td style={{ padding: '10px 20px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>{employee.employeeName}</td>
                <td style={{ padding: '10px 20px' }}>{employee.salaryAmount.toLocaleString()} RWF</td>
                <td style={{ padding: '10px 20px', textAlign: 'right' }}>
                  <button
                    onClick={() => onRemove(employee.id)}
                    style={{ color: 'var(--toss-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                  >
                    <Trash2 size={14} /> Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function PayrollRunConfirmation({ result, onDone }: { result: PayrollRunResult; onDone: () => void }) {
  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center', maxWidth: '480px' }}>
      <CircleCheck size={40} color="var(--toss-green)" />
      <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Payroll paid</h2>
      <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
        {result.totalAmount.toLocaleString()} RWF
      </p>
      <p style={{ fontSize: '14px', color: 'var(--toss-grey-500)' }}>{result.employeeCount} employees paid</p>
      <div style={{ width: '100%', textAlign: 'left' }}>
        {result.payslips.map((p) => (
          <div
            key={p.transactionId}
            style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderTop: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          >
            <span style={{ color: 'var(--toss-grey-700)' }}>{p.employeeName}</span>
            <span style={{ fontWeight: 600 }}>{p.amount.toLocaleString()} RWF</span>
          </div>
        ))}
      </div>
      <button className="toss-btn toss-btn-secondary" style={{ padding: '10px 20px' }} onClick={onDone}>
        Back to roster
      </button>
    </div>
  );
}
