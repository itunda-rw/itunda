import { apiFetch } from './api';

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (item 154) -- see the
// backend's ForeignCurrencyWalletService.kt doc comment: scoped to USD/EUR/GBP, the
// three currencies real Rwandan diaspora remittance corridors (US, Eurozone/Belgium,
// UK) actually run through, not Toss's full 17-currency breadth. A conversion moves
// real money entirely between the caller's OWN RWF and foreign-currency wallets at a
// real live mid-market rate plus a real, transparent 1.5% itunda margin (a genuine
// forex spread, not a flat fee) -- NOT a cross-border receiving/SWIFT rail, which would
// need a real correspondent-banking relationship this backend has no path to. Found
// with zero client anywhere despite being real and ledger-backed.
export const FOREIGN_CURRENCY_SUPPORTED = ['USD', 'EUR', 'GBP'] as const;
export type ForeignCurrencyCode = (typeof FOREIGN_CURRENCY_SUPPORTED)[number];

export interface ForeignCurrencyWallet {
  id: string;
  userId: string;
  accountNumber: string;
  accountName: string;
  type: 'FOREIGN_CURRENCY';
  balance: number;
  availableBalance: number;
  currency: string;
}

export interface CurrencyConversion {
  id: string;
  userId: string;
  fromCurrency: string;
  toCurrency: string;
  fromAmount: number;
  toAmount: number;
  rate: number;
  marginAmount: number;
  transactionId: string;
  createdAt: string;
}

export const openForeignCurrencyWallet = (currency: ForeignCurrencyCode) =>
  apiFetch<{ success: boolean; wallet: ForeignCurrencyWallet }>('/api/v1/wallet/foreign-currency/wallets', {
    method: 'POST',
    body: JSON.stringify({ currency }),
  }).then((r) => r.wallet);

export const fetchMyForeignCurrencyWallets = () =>
  apiFetch<{ success: boolean; wallets: ForeignCurrencyWallet[] }>('/api/v1/wallet/foreign-currency/wallets').then((r) => r.wallets);

// Real live mid-market rate, before itunda's own margin -- backs a quote preview before
// committing to convertCurrency.
export const fetchExchangeRate = (from: string, to: string) =>
  apiFetch<{ success: boolean; from: string; to: string; rate: number }>(
    `/api/v1/wallet/foreign-currency/rate?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );

export const convertCurrency = (fromCurrency: string, toCurrency: string, amount: number) =>
  apiFetch<{ success: boolean; conversion: CurrencyConversion }>('/api/v1/wallet/foreign-currency/convert', {
    method: 'POST',
    body: JSON.stringify({ fromCurrency, toCurrency, amount }),
  }).then((r) => r.conversion);

export const fetchMyCurrencyConversions = () =>
  apiFetch<{ success: boolean; conversions: CurrencyConversion[] }>('/api/v1/wallet/foreign-currency/conversions').then((r) => r.conversions);
