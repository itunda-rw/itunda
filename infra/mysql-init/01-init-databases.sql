CREATE DATABASE IF NOT EXISTS itunda;
CREATE DATABASE IF NOT EXISTS itunda_ledger;
CREATE DATABASE IF NOT EXISTS itunda_payment;

GRANT ALL PRIVILEGES ON itunda.* TO 'itunda'@'%';
GRANT ALL PRIVILEGES ON itunda_ledger.* TO 'itunda'@'%';
GRANT ALL PRIVILEGES ON itunda_payment.* TO 'itunda'@'%';

FLUSH PRIVILEGES;
