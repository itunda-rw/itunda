import React, { useEffect, useRef, useState } from 'react';
import { loadPaymentWidget } from '@tosspayments/payment-widget-sdk';
import './App.css';

// Using a public Toss Payments test client key
const clientKey = 'test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq'; 
const customerKey = 'rwanda_user_001';

export default function App() {
  const paymentWidgetRef = useRef<any>(null);
  const paymentMethodsWidgetRef = useRef<any>(null);
  
  // RWF 5,000 adapted for Rwanda
  const [price, setPrice] = useState(5000); 

  useEffect(() => {
    (async () => {
      // 1. Initialize Toss Payment Widget using Toss's exact SDK
      const paymentWidget = await loadPaymentWidget(clientKey, customerKey);

      // 2. Render the checkout UI provided by Toss directly (no custom UI from scratch)
      const paymentMethodsWidget = paymentWidget.renderPaymentMethods(
        '#payment-widget', 
        { value: price },
        { variantKey: 'DEFAULT' } 
      );
      
      // Render Toss's built-in terms of service/agreement widget
      paymentWidget.renderAgreement('#agreement');

      paymentWidgetRef.current = paymentWidget;
      paymentMethodsWidgetRef.current = paymentMethodsWidget;
    })();
  }, []);

  useEffect(() => {
    const paymentMethodsWidget = paymentMethodsWidgetRef.current;
    if (paymentMethodsWidget == null) return;
    
    paymentMethodsWidget.updateAmount(price);
  }, [price]);

  const handlePayment = async () => {
    const paymentWidget = paymentWidgetRef.current;
    try {
      // Request payment using Toss's SDK, but applying Rwandan context
      await paymentWidget.requestPayment({
        orderId: `ITUNDA_RW_${Math.random().toString(36).substring(2, 11)}`,
        orderName: 'Irembo E-Gov Services & RRA Tax',
        successUrl: window.location.origin + '/success',
        failUrl: window.location.origin + '/fail',
        customerEmail: 'kigali.user@itunda.rw',
        customerName: 'Kagabo',
      });
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div style={{ maxWidth: '600px', margin: '0 auto', padding: '20px', fontFamily: 'sans-serif' }}>
      <h1 style={{ color: '#191F28', marginBottom: '8px' }}>Checkout</h1>
      <p style={{ color: '#8B95A1', marginBottom: '32px' }}>Secure payments via MTN MoMo, Airtel Money, and Bank Transfer.</p>
      
      {/* Target divs for the Toss Payments Widget to mount into */}
      <div id="payment-widget" style={{ width: '100%', marginBottom: '16px' }} />
      <div id="agreement" style={{ width: '100%', marginBottom: '24px' }} />

      <button 
        onClick={handlePayment}
        style={{ 
          backgroundColor: '#3182F6', color: 'white', padding: '16px', 
          borderRadius: '12px', border: 'none', width: '100%', 
          fontSize: '18px', fontWeight: 'bold', cursor: 'pointer'
        }}
      >
        Pay {price.toLocaleString()} RWF
      </button>
    </div>
  );
}
