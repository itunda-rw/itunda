CREATE TABLE ride_trusted_contacts (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    contact_user_id VARCHAR(64) NOT NULL,
    contact_name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_ride_trusted_contacts_user_contact (user_id, contact_user_id),
    KEY idx_ride_trusted_contacts_user (user_id)
);
