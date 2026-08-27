// Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28,
// direct user reference: real Coupang Eats "다른 고객은 함께 주문했어요" rail). See
// backend OrderItemRepository.getFrequentlyOrderedWith's own doc comment: a real,
// derived co-occurrence signal, never a fabricated pairing -- a real minimum
// co-occurrence threshold gates what ever surfaces, and this renders nothing at all
// when the real list comes back empty (no placeholder, no "customers also viewed"
// filler). Extracted into its own file per this session's own file-size-lint
// discipline.

import { useEffect, useState } from 'react';
import type { FrequentlyOrderedWithItem } from './lib/eats';
import { fetchFrequentlyOrderedWith } from './lib/eats';

export function EatsFrequentlyOrderedWith({ productId, onAdd }: { productId: string; onAdd: (item: FrequentlyOrderedWithItem) => void }) {
  const [items, setItems] = useState<FrequentlyOrderedWithItem[]>([]);

  useEffect(() => {
    setItems([]);
    fetchFrequentlyOrderedWith(productId).then(setItems).catch(() => {});
  }, [productId]);

  if (items.length === 0) return null;

  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>Frequently ordered together</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {items.map((item) => (
          <div key={item.id} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', width: '120px', flexShrink: 0, gap: '4px' }}>
            <div style={{ width: '100px', height: '80px', borderRadius: 'var(--itunda-radius-md)', overflow: 'hidden', backgroundColor: 'var(--itunda-grey-100)' }}>
              {item.imageUrl && <img src={item.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={(e) => { e.currentTarget.style.display = 'none'; }} />}
            </div>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{item.name}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{item.price.toLocaleString()} RWF</p>
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              style={{ width: '100%', padding: '6px', fontSize: 'var(--itunda-type-scale-12-size)' }}
              disabled={item.stockQuantity === 0}
              onClick={() => onAdd(item)}
            >
              {item.stockQuantity === 0 ? 'Out of stock' : '+ Add'}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
