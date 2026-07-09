#!/bin/bash

echo "======================================"
echo "Itunda Bank - Toss-style Outbox Test"
echo "======================================"

echo ""
echo "1. Registering Debezium MySQL Connector for CDC..."
curl -s -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d '{
  "name": "ledger-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "tasks.max": "1",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "root",
    "database.password": "itunda_password",
    "database.server.id": "184054",
    "topic.prefix": "dbserver1",
    "database.include.list": "itunda_ledger",
    "table.include.list": "itunda_ledger.outbox_event_entity",
    "schema.history.internal.kafka.bootstrap.servers": "kafka-broker-1:29092",
    "schema.history.internal.kafka.topic": "schema-changes.itunda_ledger",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.route.topic.replacement": "ledger-transfer-events",
    "transforms.outbox.table.field.event.id": "event_id",
    "transforms.outbox.table.field.event.type": "aggregate_type",
    "transforms.outbox.table.field.event.payload": "payload"
  }
}'
echo -e "\n[Connector Registered]"
echo ""

echo "2. Waiting 5 seconds for Connector to initialize..."
sleep 5
echo ""

echo "3. Triggering P2P Transfer in Ledger Service..."
curl -s -X POST http://localhost:8080/api/v1/ledger/transfer \
-H "Content-Type: application/json" \
-d '{
  "sourceAccountId": "acc_001",
  "destinationAccountId": "acc_002",
  "amount": 1000,
  "idempotencyKey": "uuid-1234-abcd"
}'
echo -e "\n[Transfer Initiated]"

echo ""
echo "Done! Check your payment-service terminal to see the Kafka Consumer pick up the event and trigger the RNP Gateway."
