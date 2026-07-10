rootProject.name = "itunda-microservices"

include("ledger-service:ledger-domain")
include("ledger-service:ledger-db")
include("ledger-service:ledger-api")
include("payment-service:payment-domain")
include("payment-service:payment-db")
include("payment-service:payment-api")
include("core-libs")
