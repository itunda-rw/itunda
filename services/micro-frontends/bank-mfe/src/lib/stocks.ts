import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Toss Securities-style stock investing (rw.itunda.stocks) -- this is the FIRST
// bank-mfe UI this feature has ever had on any client (Android/iOS never got one
// either), even though buy/sell/portfolio have been real and ledger-backed since much
// earlier in this project. Day-over-day movement, watchlist, per-stock price history,
// and account-level portfolio history are all real, deterministic simulations (no live
// RSE market-data feed exists for this backend to poll) -- see StockCatalog's own doc
// comment on the backend for the full account.

export interface Stock {
  id: string;
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  marketCap: string;
  volume: number;
  // Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- "RSE" for the
  // original 6 domestic symbols, "NASDAQ" for the real US-listed names added
  // 2026-08-01. See StockCatalog's own doc comment on the backend.
  market: string;
}

export interface PricePoint {
  date: string;
  price: number;
}

export interface PortfolioValuePoint {
  date: string;
  value: number;
}

export interface StockHolding {
  stockId: string;
  symbol: string;
  name: string;
  shares: number;
  avgPrice: number;
  currentPrice: number;
  value: number;
  return: number;
}

export interface Portfolio {
  totalValue: number;
  totalReturn: number;
  totalReturnPercent: number;
  holdings: StockHolding[];
}

export const fetchStocks = () => apiFetch<{ success: boolean; stocks: Stock[] }>('/api/v1/stocks').then((r) => r.stocks);

export const fetchStockHistory = (stockId: string, days = 30) =>
  apiFetch<{ success: boolean; history: PricePoint[] }>(`/api/v1/stocks/${stockId}/history?days=${days}`).then((r) => r.history);

export const fetchPortfolio = () => apiFetch<{ success: boolean; portfolio: Portfolio }>('/api/v1/stocks/portfolio').then((r) => r.portfolio);

export const fetchPortfolioHistory = (days = 30) =>
  apiFetch<{ success: boolean; history: PortfolioValuePoint[] }>(`/api/v1/stocks/portfolio/history?days=${days}`).then((r) => r.history);

// Real Investment-wallet top-up (2026-08-04) -- found via a fresh "defined but
// uncalled" endpoint sweep: StocksService.fundInvestmentWallet (a real MAIN ->
// INVESTMENT internal ledger transfer) had zero client anywhere, so a user with no
// pre-seeded investment balance had no way to ever actually buy a stock. Ports the
// same fix already shipped on Android's InvestScreen.
export const fundInvestmentWallet = (amount: number) =>
  apiFetch<{ success: boolean; transaction: { id: string; amount: number; completedAt: string } }>('/api/v1/stocks/fund', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });

export const buyStock = (stockId: string, shares: number) =>
  apiFetch<{ success: boolean; message: string; transaction: unknown }>('/api/v1/stocks/buy', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ stockId, shares }),
  });

export const sellStock = (stockId: string, shares: number) =>
  apiFetch<{ success: boolean; message: string; transaction: unknown }>('/api/v1/stocks/sell', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ stockId, shares }),
  });

export const fetchWatchlist = () => apiFetch<{ success: boolean; watchlist: Stock[] }>('/api/v1/stocks/watchlist').then((r) => r.watchlist);

export const watchStock = (stockId: string) => apiFetch<{ success: boolean }>(`/api/v1/stocks/${stockId}/watch`, { method: 'POST' });

export const unwatchStock = (stockId: string) => apiFetch<{ success: boolean }>(`/api/v1/stocks/${stockId}/watch`, { method: 'DELETE' });

// Real Toss Securities 목표가 알림 (target price alert, section 113) -- found via a
// fresh "defined but uncalled" endpoint sweep: the backend (StocksService.setPriceAlert/
// clearPriceAlert/getPriceAlert, plus StockPriceAlertScheduler) shipped fully
// live-verified 2026-08-17, but no client anywhere ever called it. This is the first
// client wiring for it, in the same StockDetailSheet the existing Buy/Sell/Watch UI
// already lives in.
export interface PriceAlert {
  targetPrice: number | null;
  targetDirection: 'ABOVE' | 'BELOW' | null;
  alertTriggeredAt: string | null;
}

export const fetchPriceAlert = (stockId: string) =>
  apiFetch<{ success: boolean } & PriceAlert>(`/api/v1/stocks/${stockId}/price-alert`).then((r) => ({
    targetPrice: r.targetPrice, targetDirection: r.targetDirection, alertTriggeredAt: r.alertTriggeredAt,
  }));

export const setPriceAlert = (stockId: string, targetPrice: number, direction: 'ABOVE' | 'BELOW') =>
  apiFetch<{ success: boolean; watch: unknown }>(`/api/v1/stocks/${stockId}/price-alert`, {
    method: 'POST',
    body: JSON.stringify({ targetPrice, direction }),
  });

export const clearPriceAlert = (stockId: string) =>
  apiFetch<{ success: boolean; watch: unknown }>(`/api/v1/stocks/${stockId}/price-alert`, { method: 'DELETE' });
