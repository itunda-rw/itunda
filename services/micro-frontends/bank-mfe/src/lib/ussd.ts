// Real USSD basic-banking access (item 231) -- the fourth feature in this codebase not
// sourced from Toss Bank/당근마켓/Coupang/Naver/Kakao. Sourced from real, published
// statistics: Rwanda's smartphone penetration is only ~34-35% as of 2024/2025 despite
// ~87.4% overall mobile-phone penetration (IGIHE/Statista) -- roughly two-thirds of
// Rwandans with a phone have a feature phone, not a smartphone, and cannot use
// itunda's app at all. This file only covers the real, authenticated in-app PIN setup
// -- the actual USSD conversation itself (POST /api/v1/ussd/session) is called by a
// real telco USSD gateway, not a browser, so it has no bank-mfe client of its own.
// Honest scope, named explicitly: the real telco short-code + gateway partnership
// (a real *123#-style dial) requires an external MNO/gateway relationship this
// self-hosted repo has no path to obtain -- the menu state machine, PIN auth, and
// real money movement behind it are not simulated, only the telco connection is.

import { apiFetch } from './api';

export const setUssdPin = (pin: string) =>
  apiFetch<{ success: boolean }>('/api/v1/ussd/pin', {
    method: 'POST',
    body: JSON.stringify({ pin }),
  });
