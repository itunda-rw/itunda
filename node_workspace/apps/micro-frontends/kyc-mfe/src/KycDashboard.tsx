import React, { useState } from 'react';
import { overlay } from 'overlay-kit';
import { useFunnel } from '@toss/use-funnel';

export default function KycDashboard() {
  return (
    <div style={{ padding: '20px', backgroundColor: '#F2F4F6', borderRadius: '16px' }}>
      <h2 style={{ color: '#3182F6', fontFamily: 'sans-serif', margin: '0 0 8px 0' }}>KYC Verification</h2>
      <p style={{ color: '#333D4B', fontFamily: 'sans-serif', fontSize: '14px', marginBottom: '16px' }}>
        Rwanda National ID (NIDA) verification.
      </p>
      <button 
        onClick={() => {
          overlay.open(({ isOpen, close }) => (
            <KycFunnelModal isOpen={isOpen} close={close} />
          ));
        }}
        style={{ 
          backgroundColor: '#3182F6', 
          color: 'white', 
          padding: '14px 24px', 
          border: 'none', 
          borderRadius: '12px', 
          fontWeight: '600',
          cursor: 'pointer',
          width: '100%'
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
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'flex-end',
      zIndex: 9999
    }}>
      <div style={{
        backgroundColor: '#fff', width: '100%', minHeight: '400px',
        borderTopLeftRadius: '24px', borderTopRightRadius: '24px', padding: '24px',
        boxSizing: 'border-box', fontFamily: 'sans-serif',
        animation: 'slideUp 0.3s ease-out forwards'
      }}>
        <Funnel>
          <Funnel.Step name="NIDA_INPUT">
            <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
              <h2 style={{ margin: '0 0 16px 0', color: '#191F28' }}>Enter your National ID</h2>
              <p style={{ color: '#8B95A1', margin: '0 0 24px 0' }}>16-digit Rwandan ID number.</p>
              <input 
                autoFocus
                maxLength={16}
                value={nida}
                onChange={(e) => setNida(e.target.value.replace(/[^0-9]/g, ''))}
                placeholder="1 1990 8 0000000 0 00"
                style={{
                  fontSize: '24px', padding: '16px', borderRadius: '12px',
                  border: '1px solid #E5E8EB', width: '100%', boxSizing: 'border-box',
                  marginBottom: 'auto'
                }}
              />
              <button 
                disabled={nida.length !== 16}
                onClick={() => setStep('OTP_INPUT')}
                style={{
                  backgroundColor: nida.length === 16 ? '#3182F6' : '#E5E8EB',
                  color: nida.length === 16 ? 'white' : '#8B95A1',
                  padding: '18px', borderRadius: '16px', border: 'none',
                  fontWeight: 'bold', fontSize: '16px', cursor: nida.length === 16 ? 'pointer' : 'default',
                  marginTop: '40px'
                }}
              >
                Next
              </button>
            </div>
          </Funnel.Step>

          <Funnel.Step name="OTP_INPUT">
            <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
              <h2 style={{ margin: '0 0 16px 0', color: '#191F28' }}>Enter OTP</h2>
              <p style={{ color: '#8B95A1', margin: '0 0 24px 0' }}>Sent to your registered MTN MoMo number.</p>
              <input 
                autoFocus
                maxLength={6}
                placeholder="000000"
                style={{
                  fontSize: '24px', padding: '16px', borderRadius: '12px',
                  border: '1px solid #E5E8EB', width: '100%', boxSizing: 'border-box',
                  letterSpacing: '8px', textAlign: 'center'
                }}
                onChange={(e) => {
                  if (e.target.value.length === 6) setStep('SUCCESS');
                }}
              />
            </div>
          </Funnel.Step>

          <Funnel.Step name="SUCCESS">
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', paddingTop: '40px' }}>
              <div style={{ width: '64px', height: '64px', backgroundColor: '#3182F6', borderRadius: '32px', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '24px' }}>
                <span style={{ color: 'white', fontSize: '32px' }}>✓</span>
              </div>
              <h2 style={{ margin: '0 0 16px 0', color: '#191F28' }}>Verification Complete</h2>
              <p style={{ color: '#8B95A1', margin: '0 0 40px 0', textAlign: 'center' }}>Your NIDA identity has been securely verified.</p>
              <button 
                onClick={close}
                style={{
                  backgroundColor: '#3182F6', color: 'white',
                  padding: '18px', borderRadius: '16px', border: 'none',
                  fontWeight: 'bold', fontSize: '16px', cursor: 'pointer',
                  width: '100%'
                }}
              >
                Done
              </button>
            </div>
          </Funnel.Step>
        </Funnel>
      </div>
      <style>
        {`
          @keyframes slideUp {
            from { transform: translateY(100%); }
            to { transform: translateY(0); }
          }
        `}
      </style>
    </div>
  );
}
