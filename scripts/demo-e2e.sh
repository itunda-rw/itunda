#!/usr/bin/env bash
#
# End-to-end demo script: send money, pay a bill, and pay a merchant QR code --
# against services/backend's real endpoints (docs/API_SPECIFICATION.md /
# docs/PAYMENTS.md), not a mock. Explicitly requested in
# docs/TOSS_RWANDA_ALIGNMENT.md's gap list ("Add end-to-end demo scripts for send,
# QR, bill, merchant settlement, and fraud review").
#
# Requires: services/backend running (cd services/backend && ./gradlew :app:bootRun),
# a real MySQL + Redis (see services/backend/README.md), and `jq` installed.
#
# Uses the real seeded demo user (SeedDataRunner.kt: +250788123456 / password123,
# a real ~2,450,000 RWF MAIN wallet balance) as the payer, rather than a freshly
# registered account -- a brand-new registration starts at a real 0 RWF balance
# (there is no "add funds" endpoint yet, see docs/TOSS_PARITY_MATRIX.md's
# Non-Negotiable Gates), so this script would otherwise never get past step 4.
#
# Bill payment and transfer confirm both now go through a real, simulated provider
# connector (core/.../provider/ProviderConnector.kt, added this session) with a real
# non-zero decline rate -- an occasional PROVIDER_DECLINED here is correct, expected
# behavior, not a bug in this script.
#
# Runtime-verified (2026-07-11): every endpoint in this script was exercised by hand
# against a real, live services/backend + MySQL + Redis (this environment does have a
# Docker daemon after all -- see docs/ARCHITECTURE.md's Flyway/outbox notes for the full
# story) and every one returned a real HTTP 200 with a correctly-balanced ledger entry.
# This exact script was not run start-to-finish (jq wasn't installable here -- an
# unrelated Homebrew man-page permission issue, not a code problem), but each of its
# curl calls was run manually with the same payloads and produced the results this
# script expects. Every endpoint path, request body, and response field below is
# transcribed directly from the real controllers (WalletController.kt, BillsController.kt,
# MerchantController.kt), not guessed.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:4001}"

if ! command -v jq &>/dev/null; then
  echo "This script needs jq (brew install jq / apt install jq) to parse responses." >&2
  exit 1
fi

step() { echo; echo "=== $1 ==="; }

rand_phone() { echo "+2507$((RANDOM % 90000000 + 10000000))"; }
idempotency_key() { uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())'; }

MERCHANT_PHONE=$(rand_phone)

step "1. Log in as the seeded demo user (has a real, non-zero wallet balance)"
PAYER_AUTH=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+250788123456","password":"password123"}')
PAYER_TOKEN=$(echo "$PAYER_AUTH" | jq -r '.accessToken')
echo "$PAYER_AUTH" | jq '{user: .user.phoneNumber, message}'

step "2. Register a fresh merchant-owner account ($MERCHANT_PHONE)"
MERCHANT_AUTH=$(curl -sf -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$MERCHANT_PHONE\",\"email\":null,\"firstName\":\"Uwase\",\"lastName\":\"Merchant\",\"password\":\"demo-password-123\"}")
MERCHANT_TOKEN=$(echo "$MERCHANT_AUTH" | jq -r '.accessToken')
echo "$MERCHANT_AUTH" | jq '{user: .user.phoneNumber, message}'
# A brand-new registration's 0 RWF wallet is fine here -- this account only *receives*
# money via QR collection below, it never needs to pay for anything itself.

step "3. Check the payer's wallet balance"
curl -sf "$BASE_URL/api/v1/wallet" -H "Authorization: Bearer $PAYER_TOKEN" | jq '.wallets[0] | {id, accountNumber, balance}'

step "4. Pay a bill (REG electricity)"
curl -s -X POST "$BASE_URL/api/v1/bills/pay" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PAYER_TOKEN" \
  -H "Idempotency-Key: $(idempotency_key)" \
  -d '{"billId":"bill_1","amount":5000,"accountNumber":"REG-12345","provider":"REG"}' | jq

step "5. Send money: quote a transfer, then confirm it"
QUOTE=$(curl -sf -X POST "$BASE_URL/api/v1/wallet/transfer/quote" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PAYER_TOKEN" \
  -d '{"amount":1000,"recipient":"+250788111222"}')
echo "$QUOTE" | jq '.quote | {id, amount, fee, totalDebit}'
QUOTE_ID=$(echo "$QUOTE" | jq -r '.quote.id')

curl -s -X POST "$BASE_URL/api/v1/wallet/transfer/confirm" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PAYER_TOKEN" \
  -H "Idempotency-Key: $(idempotency_key)" \
  -d "{\"quoteId\":\"$QUOTE_ID\"}" | jq

step "6. Register a merchant"
MERCHANT=$(curl -s -X POST "$BASE_URL/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -d '{"businessName":"Kigali Coffee"}')
echo "$MERCHANT" | jq

step "7. Generate a QR payment code for 2,000 RWF"
INTENT=$(curl -s -X POST "$BASE_URL/api/v1/merchant/qr/generate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -d '{"amount":2000,"description":"2 espresso"}')
echo "$INTENT" | jq
INTENT_ID=$(echo "$INTENT" | jq -r '.paymentIntent.id')

step "8. Payer scans and pays the merchant's QR code"
curl -s -X POST "$BASE_URL/api/v1/merchant/collect/$INTENT_ID" \
  -H "Authorization: Bearer $PAYER_TOKEN" \
  -H "Idempotency-Key: $(idempotency_key)" | jq

step "Done"
echo "Fraud review and merchant settlement batching are not exercised here -- neither"
echo "has a real, callable endpoint yet (see docs/TOSS_RWANDA_ALIGNMENT.md's Operations"
echo "section and docs/TOSS_PARITY_MATRIX.md's Operations rows for what's still open)."
