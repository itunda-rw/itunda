import { useState } from 'react';
import { overlay } from 'overlay-kit';
import { useFunnel } from '@toss/use-funnel';
import './KycDashboard.css';

export default function KycDashboard() {
  return (
    <div className="kyc-card">
      <h2 className="kyc-card__title">KYC Verification</h2>
      <p className="kyc-card__subtitle">
        Rwanda National ID (NIDA) verification.
      </p>
      <button
        className="kyc-card__cta"
        onClick={() => {
          overlay.open(({ isOpen, close }) => (
            <KycFunnelModal isOpen={isOpen} close={close} />
          ));
        }}>
        Verify NIDA ID
      </button>
    </div>
  );
}

const FUNNEL_STEPS = ['NIDA_INPUT', 'OTP_INPUT', 'SUCCESS'] as const;

function KycFunnelModal({ isOpen, close }: { isOpen: boolean; close: () => void }) {
  const [Funnel, setStep] = useFunnel(FUNNEL_STEPS);
  const [nida, setNida] = useState('');

  if (!isOpen) return null;

  return (
    <div className="kyc-modal-backdrop">
      <div className="kyc-modal-sheet">
        <Funnel>
          <Funnel.Step name="NIDA_INPUT">
            <div className="kyc-step">
              <h2 className="kyc-step__title">Enter your National ID</h2>
              <p className="kyc-step__subtitle">16-digit Rwandan ID number.</p>
              <input
                autoFocus
                maxLength={16}
                value={nida}
                onChange={(e) => setNida(e.target.value.replace(/[^0-9]/g, ''))}
                placeholder="1 1990 8 0000000 0 00"
                className="kyc-step__input"
              />
              <button
                disabled={nida.length !== 16}
                onClick={() => setStep('OTP_INPUT')}
                className={`kyc-step__next ${nida.length === 16 ? 'kyc-step__next--enabled' : 'kyc-step__next--disabled'}`}
              >
                Next
              </button>
            </div>
          </Funnel.Step>

          <Funnel.Step name="OTP_INPUT">
            <div className="kyc-step">
              <h2 className="kyc-step__title">Enter OTP</h2>
              <p className="kyc-step__subtitle">Sent to your registered MTN MoMo number.</p>
              <input
                autoFocus
                maxLength={6}
                placeholder="000000"
                className="kyc-step__input kyc-step__input--otp"
                onChange={(e) => {
                  if (e.target.value.length === 6) setStep('SUCCESS');
                }}
              />
            </div>
          </Funnel.Step>

          <Funnel.Step name="SUCCESS">
            <div className="kyc-success">
              <div className="kyc-success__badge">
                <span className="kyc-success__check">✓</span>
              </div>
              <h2 className="kyc-step__title">Verification Complete</h2>
              <p className="kyc-success__body">Your NIDA identity has been securely verified.</p>
              <button onClick={close} className="kyc-success__done">
                Done
              </button>
            </div>
          </Funnel.Step>
        </Funnel>
      </div>
    </div>
  );
}
