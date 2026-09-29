-- Real Coupang-style post-delivery Return & Exchange requests (반품/교환 신청)
-- (rw.itunda.commerce.OrderReturnService, 2026-07-26) -- distinct from OrderService
-- .cancelOrder, which is deliberately scoped to only PLACED orders. This covers the
-- separate real Coupang flow that starts only after DELIVERED: buyer requests a
-- return/exchange, seller reviews, an approved RETURN refunds via the same
-- reversing-ledger-entry technique cancelOrder already established.

CREATE TABLE order_return_requests (
    id                    VARCHAR(64) NOT NULL PRIMARY KEY,
    order_id              VARCHAR(64) NOT NULL,
    buyer_id              VARCHAR(64) NOT NULL,
    merchant_id           VARCHAR(64) NOT NULL,
    type                  VARCHAR(16) NOT NULL,
    reason_code           VARCHAR(32) NOT NULL,
    reason_note           VARCHAR(500),
    status                VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    refund_transaction_id VARCHAR(64),
    requested_at          DATETIME(6) NOT NULL,
    decided_at            DATETIME(6),
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    KEY idx_order_return_requests_order_id (order_id),
    KEY idx_order_return_requests_merchant_id (merchant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
