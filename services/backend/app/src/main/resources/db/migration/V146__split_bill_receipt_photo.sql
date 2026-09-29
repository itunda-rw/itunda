-- Real KakaoPay-style 정산하기 receipt photo attach -- see SplitBillService.attachReceipt's
-- own doc comment. NULL means no receipt attached yet.

ALTER TABLE split_bills ADD COLUMN receipt_image_url VARCHAR(2048) NULL;
