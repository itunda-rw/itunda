import { useEffect, useState } from 'react';
import { CircleCheck, Trash2, Users } from 'lucide-react';
import { ApiError } from '../lib/api';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';
import {
  addPayrollEmployee,
  getPayrollRoster,
  removePayrollEmployee,
  runPayroll,
  type PayrollEmployee,
  type PayrollRunResult,
} from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

// Real B2B payroll UI -- see PayrollService.kt's own doc comment for why this is real
// wallet-to-wallet money movement, not a demo/simulation like CollectScreen's Card tab.
export default function PayrollScreen() {
  const { t } = useI18n();
  const [roster, setRoster] = useState<PayrollEmployee[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [runResult, setRunResult] = useState<PayrollRunResult | null>(null);

  const load = () => {
    setLoadError(null);
    getPayrollRoster()
      .then(setRoster)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t('payroll.loadError')));
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
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('payroll.addError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('payroll.addEmployeeTitle')}</h2>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        {t('payroll.addEmployeeBody')}
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 2, minWidth: '180px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('payroll.phoneNumberLabel')}</span>
          <input
            type="tel"
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            placeholder="+250788123456"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '140px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('payroll.monthlySalaryLabel')}</span>
          <input
            type="number"
            min="1"
            step="1"
            value={salaryAmount}
            onChange={(e) => setSalaryAmount(e.target.value)}
            placeholder="150000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ height: '46px' }}>
          {submitting ? t('payroll.adding') : t('payroll.addButton')}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '12px 0 0' }} role="alert">
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
  const { t } = useI18n();
  const [runError, setRunError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  // Real device step-up (2026-07-28 port) -- a real 403 DEVICE_NOT_VERIFIED (this
  // device hasn't been step-up-verified yet) gets its own case, not a generic error.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleRunPayroll = async () => {
    setRunError(null);
    setRunning(true);
    try {
      const result = await runPayroll();
      onRunPayroll(result);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setRunError(err instanceof ApiError ? err.message : t('payroll.runError'));
      }
    } finally {
      setRunning(false);
    }
  };

  if (error) {
    return (
      <div className="itunda-card">
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">
          {error}
        </p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onReload} style={{ marginTop: '12px' }}>
          {t('payroll.retryButton')}
        </button>
      </div>
    );
  }

  if (roster === null) {
    return <div className="itunda-card">{t('payroll.loading')}</div>;
  }

  const total = roster.reduce((acc, e) => acc + e.salaryAmount, 0);

  return (
    <div className="itunda-card" style={{ padding: 0, overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Users size={18} color="var(--itunda-indigo)" />
          <h2 style={{ fontSize: '16px', fontWeight: 700 }}>{t('payroll.rosterTitle', { count: roster.length })}</h2>
        </div>
        <button
          className="itunda-btn itunda-btn-primary"
          onClick={handleRunPayroll}
          disabled={running || roster.length === 0}
        >
          {running ? t('payroll.running') : t('payroll.runPayrollButton', { total: total.toLocaleString() })}
        </button>
      </div>
      {runError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '0 20px 16px' }} role="alert">
          {runError}
        </p>
      )}
      {needsDeviceVerification && (
        <div style={{ margin: '0 20px 16px' }}>
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      )}
      {roster.length === 0 ? (
        <p style={{ padding: '0 20px 20px', fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
          {t('payroll.rosterEmpty')}
        </p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
          <thead>
            <tr style={{ backgroundColor: 'var(--itunda-grey-100)', textAlign: 'left' }}>
              {[t('payroll.columnEmployee'), t('payroll.columnMonthlySalary'), ''].map((h, i) => (
                <th key={i === 2 ? 'actions' : h} style={{ padding: '10px 20px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {roster.map((employee) => (
              <tr key={employee.id} style={{ borderTop: '1px solid var(--itunda-grey-200)' }}>
                <td style={{ padding: '10px 20px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{employee.employeeName}</td>
                <td style={{ padding: '10px 20px' }}>{employee.salaryAmount.toLocaleString()} RWF</td>
                <td style={{ padding: '10px 20px', textAlign: 'right' }}>
                  <button
                    onClick={() => onRemove(employee.id)}
                    style={{ color: 'var(--itunda-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                  >
                    <Trash2 size={14} /> {t('payroll.removeButton')}
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
  const { t } = useI18n();
  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center', maxWidth: '480px' }}>
      <CircleCheck size={40} color="var(--itunda-green)" />
      <h2 style={{ fontSize: '18px', fontWeight: 700 }}>{t('payroll.paidTitle')}</h2>
      <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
        {result.totalAmount.toLocaleString()} RWF
      </p>
      <p style={{ fontSize: '14px', color: 'var(--itunda-grey-500)' }}>{t('payroll.employeesPaidCount', { count: result.employeeCount })}</p>
      <div style={{ width: '100%', textAlign: 'left' }}>
        {result.payslips.map((p) => (
          <div
            key={p.transactionId}
            style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          >
            <span style={{ color: 'var(--itunda-grey-700)' }}>{p.employeeName}</span>
            <span style={{ fontWeight: 600 }}>{p.amount.toLocaleString()} RWF</span>
          </div>
        ))}
      </div>
      <button className="itunda-btn itunda-btn-secondary" style={{ padding: '10px 20px' }} onClick={onDone}>
        {t('payroll.backToRoster')}
      </button>
    </div>
  );
}
