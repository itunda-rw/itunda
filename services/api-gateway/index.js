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
app.use('/api/v1/payments', createProxyMiddleware({
    target: 'http://localhost:8081',
    changeOrigin: true
}));

app.use('/api/v1/ledger', createProxyMiddleware({
    target: 'http://localhost:8082',
    changeOrigin: true
}));

app.use('/api/v1', createProxyMiddleware({
    target: 'http://localhost:4001',
    changeOrigin: true
}));

app.get('/health', (req, res) => {
    res.status(200).json({ status: 'UP', service: 'Itunda API Gateway (Node.js)' });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`🚀 Itunda API Gateway running on port ${PORT}`);
});
