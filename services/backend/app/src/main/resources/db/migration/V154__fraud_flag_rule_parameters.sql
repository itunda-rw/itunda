ALTER TABLE fraud_flags
    ADD COLUMN rule_parameters VARCHAR(1000) NULL AFTER description;
