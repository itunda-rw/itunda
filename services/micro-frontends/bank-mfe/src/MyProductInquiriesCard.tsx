// Real gap found live (uncalled-endpoint sweep, 2026-09-03): OrderController's own
// GET /api/v1/orders/inquiries/my-questions existed on the backend with zero caller
// on bank-mfe or iOS -- only Android had this wired (2026-08-04, ShopOrders.kt's own
// MyProductInquiriesView), matching the exact same symmetric-pair shape as the
// moto-fare rider/driver gap this session already fixed. Mirrors
// MyProductSubscriptionsCard's own "one card, self-hides when empty, lives on
// MyView" placement convention.

import { useEffect, useState } from 'react';
import { fetchMyProductInquiries, type ProductInquiry } from './lib/commerce';

export function MyProductInquiriesCard() {
  const [inquiries, setInquiries] = useState<ProductInquiry[] | null>(null);

  useEffect(() => {
    fetchMyProductInquiries().then(setInquiries).catch(() => setInquiries([]));
  }, []);

  if (!inquiries || inquiries.length === 0) return null;

  // Real fix (2026-08-24, flat-design sweep convention, matched here): dropped
  // itunda-card -- one of many stacked sections on MyView's linear screen
  // (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My questions</h3>
      {inquiries.map((q) => (
        <div key={q.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{q.question}</p>
          {q.answer ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>Answered: {q.answer}</p>
          ) : (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '4px', fontStyle: 'italic' }}>Waiting for an answer…</p>
          )}
        </div>
      ))}
    </div>
  );
}

