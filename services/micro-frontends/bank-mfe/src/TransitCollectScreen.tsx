// Real "agent collects a fare from a rider's presented code" flow (2026-08-27, direct
// user follow-up: "for simplification we need nfc"). Reuses the same
// CustomerPaymentCode every user already generates and shows as a QR/barcode via "My
// payment code" (lib/shopping.ts's generateCustomerPaymentCode) -- see lib/transit.ts's
// own tapTransitFareByCode doc comment for the full account of why this isn't a new
// token system. Any logged-in user may act as a collector; itunda has no real
// relationship with actual Kigali conductors to gate this against.
//
// NFC itself is native-only (Android HCE emit / Android+iOS NFC read -- a browser
// can't reliably do either), so the web collector always scans a QR with the camera,
// same QrScanCamera component the merchant-payment flows already use, with a manual
// fallback when the camera is unavailable -- never a "type this code" default.

import { useState } from 'react';
import { ApiError } from './lib/api';
import { QrScanCamera } from './QrScanCamera';
import {
  tapTransitFareByCode,
  TRANSIT_FARE_STEP,
  TRANSIT_MAX_FARE,
  TRANSIT_MIN_FARE,
  TRANSIT_OPERATORS,
  type TransitCollectResult,
} from './lib/transit';

export function TransitCollectScreen() {
  const [code, setCode] = useState<string | null>(null);
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [operator, setOperator] = useState<string>(TRANSIT_OPERATORS[0]);
  const [fare, setFare] = useState(TRANSIT_MIN_FARE);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [collected, setCollected] = useState<TransitCollectResult | null>(null);

  const reset = () => {
    setCode(null);
    setCollected(null);
    setError(null);
    setManualCode('');
  };

  const handleCollect = async () => {
    if (!code) return;
    setBusy(true);
    setError(null);
    try {
      const result = await tapTransitFareByCode(code, operator, fare);
      setCollected(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not collect this fare.');
    } finally {
      setBusy(false);
    }
  };

  if (collected) {
    return (
      <div className="itunda-flat-section" style={{ textAlign: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, color: 'var(--itunda-green)' }}>Collected</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', marginTop: '4px' }}>{collected.fare.toLocaleString('en-US')} RWF · {collected.operator}</p>
        <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '16px', width: '100%' }} onClick={reset}>
          Collect next fare
        </button>
      </div>
    );
  }

  if (!code) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Scan the rider&apos;s payment code</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
          Ask the rider to open itunda and tap to show their payment code, then point your camera at it.
          On the Android app, an NFC tap does the same thing without a camera.
        </p>
        {!scanUnavailable ? (
          <QrScanCamera onDetect={setCode} onUnavailable={() => setScanUnavailable(true)} />
        ) : (
          <div className="itunda-flat-section">
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
              Camera unavailable -- enter the rider&apos;s code instead.
            </p>
            <input
              placeholder="Payment code" value={manualCode} onChange={(e) => setManualCode(e.target.value)}
              style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', marginBottom: '8px' }}
            />
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={!manualCode.trim()} onClick={() => setCode(manualCode.trim())}>
              Use this code
            </button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="itunda-flat-section">
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Collect fare</p>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
        {TRANSIT_OPERATORS.map((op) => (
          <button
            key={op}
            onClick={() => setOperator(op)}
            className={`itunda-btn ${operator === op ? 'itunda-btn-primary' : 'itunda-btn-secondary'}`}
            style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {op}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
        <input
          type="range" min={TRANSIT_MIN_FARE} max={TRANSIT_MAX_FARE} step={TRANSIT_FARE_STEP}
          value={fare} onChange={(e) => setFare(Number(e.target.value))}
          style={{ flex: 1 }}
        />
        <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, minWidth: '84px', textAlign: 'right' }}>{fare.toLocaleString('en-US')} RWF</span>
      </div>
      <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={busy} onClick={handleCollect}>
        {busy ? 'Collecting…' : `Collect ${fare.toLocaleString('en-US')} RWF`}
      </button>
      <button onClick={reset} style={{ width: '100%', marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        Scan a different code
      </button>
    </div>
  );
}
