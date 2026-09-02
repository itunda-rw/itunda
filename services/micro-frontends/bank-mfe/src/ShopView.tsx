import { useState, useEffect } from 'react';
import { HeartOutline } from './icons/ItundaFaceHearts';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { ErrorCard } from './EmptyState';
import { SearchAndCategoryChips } from './BankDashboard';
import {
  fetchMerchantCategories, fetchShoppingCatalog, searchProducts,
  type ShoppingMerchant, type ProductSearchResult,
} from './lib/shopping';
import { fetchMerchantProducts, fetchOrderDetail, type CommerceProduct, type CommerceOrder } from './lib/commerce';
import { type CommerceCartGroup, type CommerceCart, cartTotalItems } from './CommerceOrders';
import { ShopSellerContactPicker } from './ShopSellerContactPicker';
import { ProductDetailView } from './ProductDisplay';
import { ProductCatalogView } from './ProductCatalogView';
import { MultiCartView, MultiCartResultsView, type CommerceCheckoutResult } from './MerchantBillingAndCart';
import { MyCommerceOrdersView, MerchantOrdersView, WishlistView } from './ShopOrdersAndWishlist';
import { MerchantReturnQueueView, MerchantRedeemVoucherCard } from './CommerceOrders';
import { BannerCarousel, MissionsRow, NearbyAdsRail, RecentlyViewedRail, DealsRail, SurplusDealsRail, TimeDealsRail } from './ShopRails';
import { SearchResultsList, MerchantList } from './ShopMerchantList';

function ShopView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'ORDERS' | 'WISHLIST'>('BROWSE');
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
  // Real seller chat (2026-08-28) -- see ShopSellerContactPicker.tsx's own doc
  // comment. The merchant currently being contacted, or null when the picker is closed.
  const [contactingMerchant, setContactingMerchant] = useState<ShoppingMerchant | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [merchantSearchInput, setMerchantSearchInput] = useState('');
  const [debouncedMerchantSearch, setDebouncedMerchantSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<CommerceProduct | null>(null);
  const [cart, setCart] = useState<CommerceCart>({});
  const [showCart, setShowCart] = useState(false);
  const [results, setResults] = useState<CommerceCheckoutResult[] | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<ProductSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [reorderError, setReorderError] = useState<string | null>(null);
  // Computed before the early returns below narrow `selected` to `null` -- TS treats
  // a property access on an optional chain whose object narrows to exactly `null` as
  // an access on `never`, which errors, so this must be read while `selected` is
  // still its real ShoppingMerchant | null type.
  const selectedBusinessName = selected?.businessName ?? '';

  // Real Coupang/Amazon-style "Buy it again" (2026-08-23) -- direct port of this
  // file's own real Eats "Reorder" (see EatsView's handleReorder). Re-populates the
  // cross-merchant `cart` from a past order's still-active products and opens the
  // cart for review, same "review before a real-money action, not an instant one-tap
  // purchase" precedent Eats already established (a delivery address could be stale,
  // a price could have changed since). Commerce products never carry option groups
  // (only Eats' menu items do), so unlike Eats this needs no "drop items that now
  // require an option selection" sanitization -- only "drop items that are no longer
  // active."
  const handleReorder = async (order: CommerceOrder) => {
    setReorderingId(order.id);
    setReorderError(null);
    try {
      const [detail, menu] = await Promise.all([fetchOrderDetail(order.id), fetchMerchantProducts(order.merchantId)]);
      if (!detail.success || !menu.success) {
        setReorderError('Could not reorder.');
        return;
      }
      const activeProducts = new Map(menu.products.filter((p) => p.active).map((p) => [p.id, p]));
      const newLines: CommerceCartGroup['lines'] = {};
      detail.items.forEach((item) => {
        const product = activeProducts.get(item.productId);
        if (!product) return;
        newLines[product.id] = { product, quantity: (newLines[product.id]?.quantity ?? 0) + item.quantity };
      });
      if (Object.keys(newLines).length === 0) {
        setReorderError('None of the items from that order are available anymore.');
        return;
      }
      setCart((prev) => {
        const mergedLines = { ...prev[order.merchantId]?.lines };
        Object.entries(newLines).forEach(([productId, line]) => {
          mergedLines[productId] = { product: line.product, quantity: (mergedLines[productId]?.quantity ?? 0) + line.quantity };
        });
        return { ...prev, [order.merchantId]: { businessName: menu.merchant.businessName, lines: mergedLines } };
      });
      setShowCart(true);
    } catch (err) {
      setReorderError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setReorderingId(null);
    }
  };

  // Real Coupang-style commerce (rw.itunda.commerce) -- deliberately reuses the same
  // GET /api/v1/shopping/merchants catalog the Shopping tab (Toss Shopping cashback
  // browsing) already uses, matching how Android/iOS's own Shop tab reuses the same
  // merchant directory rather than inventing a second one.
  const load = () => {
    setError(null);
    fetchShoppingCatalog(selectedCategory ?? undefined, debouncedMerchantSearch || undefined)
      .then(setMerchants)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    fetchMerchantCategories().then(setCategories).catch(() => {});
  }, []);

  // Real category/name filter for the merchant list (2026-07-21), debounced the same
  // way OrderFoodView's restaurant search already is -- see SearchAndCategoryChips.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedMerchantSearch(merchantSearchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [merchantSearchInput]);

  useEffect(load, [selectedCategory, debouncedMerchantSearch]);

  // Real cross-merchant product search (2026-07-20) -- see lib/shopping.ts's own doc
  // comment. Opening a result reuses ProductCatalogView as-is: it only ever reads
  // merchant.merchantId (confirmed by reading the component directly), so a minimal
  // ShoppingMerchant built from the search result -- not a second real fetch -- is
  // honest, not a shortcut that risks showing stale/wrong data.
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setSearching(true);
    try {
      setSearchResults(await searchProducts(searchQuery.trim()));
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  };
  const openSearchResult = (r: ProductSearchResult) => {
    setSelected({ merchantId: r.merchantId, businessName: r.merchantName, category: null, cashbackRate: '1%' });
  };

  const setQtyByMerchant = (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => {
    setCart((prev) => {
      const next = { ...prev };
      const existing = next[merchant.merchantId] ?? { businessName: merchant.businessName, lines: {} };
      const lines = { ...existing.lines };
      if (quantity <= 0) delete lines[product.id];
      else lines[product.id] = { product, quantity };
      if (Object.keys(lines).length === 0) delete next[merchant.merchantId];
      else next[merchant.merchantId] = { ...existing, lines };
      return next;
    });
  };

  const setQtyByIds = (merchantId: string, productId: string, quantity: number) => {
    setCart((prev) => {
      const existing = prev[merchantId];
      if (!existing) return prev;
      const next = { ...prev };
      const lines = { ...existing.lines };
      if (quantity <= 0) delete lines[productId];
      else if (lines[productId]) lines[productId] = { ...lines[productId], quantity };
      if (Object.keys(lines).length === 0) delete next[merchantId];
      else next[merchantId] = { ...existing, lines };
      return next;
    });
  };

  const handleCheckedOut = (checkoutResults: CommerceCheckoutResult[]) => {
    // Only clear the merchants that actually succeeded -- a failed group's items
    // stay in the cart so the buyer doesn't lose their selection and can retry
    // (e.g. after fixing the delivery address or topping up their account).
    setCart((prev) => {
      const next = { ...prev };
      checkoutResults.filter((r) => r.success).forEach((r) => delete next[r.merchantId]);
      return next;
    });
    setResults(checkoutResults);
    setShowCart(false);
  };

  // Real seller chat (2026-08-28) -- see ShopSellerContactPicker.tsx's own doc
  // comment. Owned here (not inside ProductCatalogView/ProductDetailView
  // themselves) so the picker overlay renders once, shared by both sub-screens.
  const contactPickerNode = contactingMerchant && (
    <ShopSellerContactPicker
      merchantId={contactingMerchant.merchantId}
      merchantName={contactingMerchant.businessName}
      onClose={() => setContactingMerchant(null)}
      onOpened={(conversationId) => { setContactingMerchant(null); onMessageSeller(conversationId); }}
    />
  );

  if (results) {
    return (
      <MultiCartResultsView
        results={results}
        onDone={() => { setResults(null); setSelected(null); setView('ORDERS'); }}
      />
    );
  }

  if (showCart) {
    return <MultiCartView cart={cart} onBack={() => setShowCart(false)} onSetQty={setQtyByIds} onCheckedOut={handleCheckedOut} />;
  }

  if (selected && selectedProduct) {
    return (
      <>
        <ProductDetailView
          merchant={selected}
          product={selectedProduct}
          cart={cart}
          onSetQty={setQtyByMerchant}
          onBack={() => setSelectedProduct(null)}
          onViewCart={() => { setSelectedProduct(null); setShowCart(true); }}
          onContactSeller={() => setContactingMerchant(selected)}
        />
        {contactPickerNode}
      </>
    );
  }

  if (selected) {
    return (
      <>
        <ProductCatalogView
          merchant={selected}
          cart={cart}
          onSetQty={setQtyByMerchant}
          onBack={() => setSelected(null)}
          onViewCart={() => setShowCart(true)}
          onOpenProduct={setSelectedProduct}
          onContactSeller={() => setContactingMerchant(selected)}
        />
        {contactPickerNode}
      </>
    );
  }

  const totalItems = cartTotalItems(cart);

  return (
    <div>
      <MerchantOrdersView />
      <MerchantReturnQueueView />
      <MerchantRedeemVoucherCard />

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'ORDERS', 'WISHLIST'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Merchants' : v === 'ORDERS' ? 'My orders' : <><HeartOutline size={12} /> Wishlist</>}
          </button>
        ))}
      </div>

      {view === 'BROWSE' && (
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search products across every merchant"
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={searching || !searchQuery.trim()}>
            {searching ? '…' : 'Search'}
          </button>
          {searchResults !== null && (
            <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>
              Clear
            </button>
          )}
        </form>
      )}

      {view === 'BROWSE' && searchResults === null && <BannerCarousel />}
      {view === 'BROWSE' && searchResults === null && <MissionsRow />}
      {view === 'BROWSE' && searchResults === null && <NearbyAdsRail onOpenMerchant={setSelected} />}
      {view === 'BROWSE' && searchResults === null && (
        <RecentlyViewedRail selectedProduct={selectedProduct} selectedMerchantName={selectedBusinessName} onOpenMerchant={setSelected} />
      )}
      {view === 'BROWSE' && searchResults === null && <DealsRail onOpenSearchResult={openSearchResult} />}
      {view === 'BROWSE' && searchResults === null && <SurplusDealsRail onOpenSearchResult={openSearchResult} />}
      {view === 'BROWSE' && searchResults === null && <TimeDealsRail onOpenMerchant={setSelected} />}

      {view === 'BROWSE' && searchResults === null && (
        <SearchAndCategoryChips
          searchInput={merchantSearchInput}
          onSearchChange={setMerchantSearchInput}
          placeholder="Search merchants"
          categories={categories}
          selectedCategory={selectedCategory}
          onSelectCategory={setSelectedCategory}
        />
      )}

      {view === 'ORDERS' ? (
        <div>
          <MyCommerceOrdersView onReorder={handleReorder} reorderingId={reorderingId} />
          {reorderError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{reorderError}</p>}
        </div>
      ) : view === 'WISHLIST' ? (
        <WishlistView onOpenMerchant={setSelected} />
      ) : view === 'BROWSE' && searchResults !== null ? (
        <SearchResultsList searchQuery={searchQuery} searchResults={searchResults} onOpenSearchResult={openSearchResult} />
      ) : error ? (
        <ErrorCard message={error} onRetry={load} />
      ) : merchants === null ? (
        <div className="itunda-flat-section skeleton" style={{ height: '220px' }} />
      ) : merchants.length === 0 ? (
        // Real copy-voice fix (item 244, round 5 of the empty-state pass, ported
        // from the same-day Android/iOS fix): "registered yet" is honest about
        // whose gap this is -- no merchant has joined yet, not something the
        // reader is missing a step on.
        // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a plain
        // one-line empty-state message.
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          {selectedCategory || debouncedMerchantSearch ? 'No merchants match your search — try a different category or search term.' : 'No merchants registered yet — check back once merchants in your area join itunda Shop.'}
        </p>
      ) : (
        <MerchantList merchants={merchants} totalItems={totalItems} onSelectMerchant={setSelected} />
      )}
      {view === 'BROWSE' && totalItems > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCart(true)}
        >
          View cart ({totalItems} item{totalItems === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

export { ShopView };
