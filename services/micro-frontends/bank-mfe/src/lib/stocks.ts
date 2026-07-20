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
