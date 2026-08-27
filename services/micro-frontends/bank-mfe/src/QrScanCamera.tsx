// Extracted from BankDashboard.tsx (2026-08-27, direct user follow-up: "for
// simplification we need nfc") -- previously defined inline there and used by
// PayByCodeCard/StaticQrPayCard/OpenChatCard's own join-by-QR flows. Pulled into its
// own file so TransitCollectScreen.tsx's real "agent scans a rider's payment code"
// flow can reuse the exact same camera-scanning component without a circular import
// back into BankDashboard.tsx (which imports TransitCollectScreen.tsx).

import { useEffect, useRef, useState } from 'react';

export function QrScanCamera({ onDetect, onUnavailable }: { onDetect: (value: string) => void; onUnavailable: () => void }) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [status, setStatus] = useState<'starting' | 'scanning' | 'detected'>('starting');

  useEffect(() => {
    if (!('BarcodeDetector' in window)) {
      onUnavailable();
      return;
    }
    let stream: MediaStream | null = null;
    let raf = 0;
    let cancelled = false;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- BarcodeDetector isn't in TS's lib.dom yet
    const detector = new (window as any).BarcodeDetector({ formats: ['qr_code'] });

    const tick = async () => {
      if (cancelled || !videoRef.current) return;
      try {
        const codes = await detector.detect(videoRef.current);
        if (codes.length > 0) {
          // Real Toss-sourced fix: a brief, announced "found" moment (matching the real
          // article's distinct completion cue) before handing off to onDetect -- 500ms is
          // enough for a screen reader to pick up and start speaking the live-region
          // mutation before this component unmounts; sighted users get the same visual
          // confirmation (the frame below turns green) instead of an instant, jarring cut.
          setStatus('detected');
          window.setTimeout(() => onDetect(codes[0].rawValue), 500);
          return;
        }
      } catch {
        // Frame not ready yet -- keep polling.
      }
      raf = requestAnimationFrame(tick);
    };

    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
      .then((s) => {
        if (cancelled) { s.getTracks().forEach((t) => t.stop()); return; }
        stream = s;
        if (videoRef.current) {
          videoRef.current.srcObject = s;
          videoRef.current.play().catch(() => {});
        }
        setStatus('scanning');
        raf = requestAnimationFrame(tick);
      })
      .catch(() => onUnavailable());

    return () => {
      cancelled = true;
      cancelAnimationFrame(raf);
      stream?.getTracks().forEach((t) => t.stop());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- onDetect/onUnavailable are stable per mount, re-subscribing on every render would restart the camera
  }, []);

  let announcement = 'QR code found.';
  if (status === 'starting') announcement = 'Starting camera…';
  else if (status === 'scanning') announcement = 'Camera ready. Point at a QR code.';

  return (
    <div style={{ position: 'relative', width: '100%', aspectRatio: '1', borderRadius: '16px', overflow: 'hidden', background: '#111', marginBottom: '10px' }}>
      <video ref={videoRef} muted playsInline aria-label="Camera preview for QR scanning" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      <div
        style={{
          position: 'absolute', inset: '14%',
          border: `3px solid ${status === 'detected' ? 'var(--itunda-green)' : 'var(--itunda-indigo-500)'}`,
          borderRadius: '16px', pointerEvents: 'none',
        }}
      />
      {(status === 'starting' || status === 'detected') && (
        <p style={{ position: 'absolute', bottom: '10px', left: 0, right: 0, textAlign: 'center', fontSize: 'var(--itunda-type-scale-12-size)', color: '#fff' }}>
          {status === 'detected' ? 'QR code found' : 'Starting camera…'}
        </p>
      )}
      <div className="sr-only" aria-live="polite" aria-atomic="true">{announcement}</div>
    </div>
  );
}

// Real itunda://pay?intentId=... / itunda://pay-static?merchantId=... QR payload format
// -- see merchant-mfe/src/lib/merchant.ts's own paymentIntentQrPayload/staticQrPayload.
// A scanned code is either that full URI or (for a merchant who printed just the raw
// code) the bare id -- accept both rather than forcing the URI shape on the user.
export function parseQrParam(raw: string, key: string): string {
  const match = raw.match(new RegExp(`[?&]${key}=([^&]+)`));
  return match ? decodeURIComponent(match[1]) : raw.trim();
}
