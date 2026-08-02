-- Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
-- VendorCashAdvanceService's own doc comment for the full sourced account. City of
-- Kigali's 2016 census counted 12,197 registered street vendors, and in 2022/2024
-- (allAfrica) the city/LODA committed to relocating ~4,000 into formal mini-markets
-- with LODA/City of Kigali offering formalized vendors loans at 2% annual interest
-- specifically to help them restock -- government's own recognition that working-
-- capital access, not stall space, is the real constraint. A 2025 Streetnet
-- International field report documents VSLA savings/loan groups organized around
-- Kigali market/street vendors' irregular daily cash flow. AfDB SME-finance research
-- found only ~25% of Rwandan SMEs use bank loans; NISR FinScope 2024 found 72% of
-- Rwandan adults still use informal credit despite 96% formal financial inclusion.
-- Sources: allAfrica (x2), loda.gov.rw, Streetnet International, AfDB, NISR
-- FinScope 2024.
--
-- Honest v1 limitations (see VendorCashAdvanceService's own doc comment for the full
-- account): (1) underwriting/collection can only see settlement volume that actually
-- flows through itunda's own MerchantService.collect (QR/card) -- a vendor's cash
-- sales off-platform are invisible to both; (2) the inflow calculation filters
-- LedgerEntry.memo text matching MerchantService.collect's real narration pattern
-- ("... collection - ..."), a real but fragile v1 shortcut, not a structured
-- settlement-category field; (3) collection is always capped at
-- min(collectionRatePercent x inflow, remainingOwed, currentWalletBalance) -- it can
-- never push a merchant's wallet negative or over-collect beyond what's owed.

CREATE TABLE vendor_cash_advances (
    id                          VARCHAR(64)   NOT NULL PRIMARY KEY,
    merchant_id                 VARCHAR(64)   NOT NULL,
    principal_amount            DECIMAL(18,2) NOT NULL,
    fee_amount                  DECIMAL(18,2) NOT NULL,
    total_owed                  DECIMAL(18,2) NOT NULL,
    remaining_owed              DECIMAL(18,2) NOT NULL,
    collection_rate_percent     DOUBLE        NOT NULL,
    status                      VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    requested_at                DATETIME(6)   NOT NULL,
    disbursed_at                DATETIME(6)   NULL,
    repaid_at                   DATETIME(6)   NULL,
    last_collection_at          DATETIME(6)   NULL,
    version                     BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_vendor_cash_advances_merchant_id ON vendor_cash_advances (merchant_id);
CREATE INDEX idx_vendor_cash_advances_merchant_status ON vendor_cash_advances (merchant_id, status);
CREATE INDEX idx_vendor_cash_advances_status ON vendor_cash_advances (status);
