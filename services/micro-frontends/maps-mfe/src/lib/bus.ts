import { apiFetch } from './api';

// Narrow slice of bank-mfe/src/lib/bus.ts (2026-08-19, the maps-mfe split) -- only
// what MapView's scheduled-bus-trip search needs, same "each MFE re-implements its
// own slice" convention as lib/api.ts/lib/maps.ts's own doc comments in this package.

export interface BusTrip {
  id: string;
  operatorUserId: string;
  walletId: string;
  origin: string;
  destination: string;
  departureTime: string;
  totalSeats: number;
  availableSeats: number;
  farePerSeat: number;
  createdAt: string;
}

export const searchBusTrips = (origin?: string, destination?: string) => {
  const params = new URLSearchParams();
  if (origin) params.set('origin', origin);
  if (destination) params.set('destination', destination);
  const qs = params.toString();
  return apiFetch<{ success: boolean; trips: BusTrip[] }>(`/api/v1/bus/trips/search${qs ? `?${qs}` : ''}`).then((r) => r.trips);
};
