const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const cors = require('cors');

const app = express();
app.use(cors());

// Toss-style API Gateway: Route mobile requests to internal Spring Boot services.
//
// Fixed (2026-07-11): this previously only routed to the two newer microservices
// (payment-service, ledger-service) and had no route at all to services/backend --
// the canonical backend holding auth/wallet/savings/stocks/loans/insurance/bills/
// notifications/discover/contacts/system, i.e. most of the actual API surface. The
// two specific routes below are registered first so they still take precedence
// (Express matches middleware in registration order, not path specificity); the
// catch-all to services/backend is registered last as the default for everything
// else under /api/v1.
// Targets are env-configurable (defaulting to localhost for local dev) rather than
// hardcoded -- `localhost` doesn't resolve to anything inside a Kubernetes pod, and
// infra/k8s/production/*.yaml sets these to the real in-cluster Service DNS names
// (2026-07-11 fix).
const PAYMENT_SERVICE_URL = process.env.PAYMENT_SERVICE_URL || 'http://localhost:8081';
const LEDGER_SERVICE_URL = process.env.LEDGER_SERVICE_URL || 'http://localhost:8082';
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:4001';

app.use('/api/v1/payments', createProxyMiddleware({
    target: PAYMENT_SERVICE_URL,
    changeOrigin: true
}));

app.use('/api/v1/ledger', createProxyMiddleware({
    target: LEDGER_SERVICE_URL,
    changeOrigin: true
}));

app.use('/api/v1', createProxyMiddleware({
    target: BACKEND_URL,
    changeOrigin: true
}));

app.get('/health', (req, res) => {
    res.status(200).json({ status: 'UP', service: 'Itunda API Gateway (Node.js)' });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`🚀 Itunda API Gateway running on port ${PORT}`);
});
