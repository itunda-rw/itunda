const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const cors = require('cors');
const promClient = require('prom-client');

const app = express();
app.use(cors());

// Real Prometheus scrape target (2026-07-11, alongside services/backend's
// micrometer-registry-prometheus) -- default Node process metrics plus HTTP
// request duration, so this gateway isn't the one service in the fleet with no
// metrics surface at all.
const metricsRegistry = new promClient.Registry();
promClient.collectDefaultMetrics({ register: metricsRegistry });
const httpRequestDuration = new promClient.Histogram({
    name: 'http_request_duration_seconds',
    help: 'Duration of HTTP requests proxied by the gateway',
    labelNames: ['method', 'route', 'status_code'],
    registers: [metricsRegistry]
});
app.use((req, res, next) => {
    const stop = httpRequestDuration.startTimer({ method: req.method });
    res.on('finish', () => stop({ route: req.path, status_code: res.statusCode }));
    next();
});
app.get('/metrics', async (req, res) => {
    res.set('Content-Type', metricsRegistry.contentType);
    res.end(await metricsRegistry.metrics());
});

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

// Real self-hosted Maps geo-stack proxy (2026-07-24) -- rides this same gateway's
// existing public tunnel instead of needing a separate bore.pub tunnel per service.
// OSRM/Nominatim/tiles/glyphs all listen on itunda-dc-a's own host network (not a
// k8s Service), reachable from this pod via the node's IP.
const TILES_URL = process.env.TILES_URL || 'http://192.168.252.4:8090';
const GLYPHS_URL = process.env.GLYPHS_URL || 'http://192.168.252.4:8091';
const OSRM_CAR_URL = process.env.OSRM_CAR_URL || 'http://192.168.252.4:5000';
const OSRM_FOOT_URL = process.env.OSRM_FOOT_URL || 'http://192.168.252.4:5001';
const NOMINATIM_URL = process.env.NOMINATIM_URL || 'http://192.168.252.4:8088';

app.use('/tiles', createProxyMiddleware({ target: TILES_URL, changeOrigin: true, pathRewrite: { '^/tiles': '' } }));
app.use('/glyphs', createProxyMiddleware({ target: GLYPHS_URL, changeOrigin: true, pathRewrite: { '^/glyphs': '' } }));
app.use('/osrm/foot', createProxyMiddleware({ target: OSRM_FOOT_URL, changeOrigin: true, pathRewrite: { '^/osrm/foot': '' } }));
app.use('/osrm', createProxyMiddleware({ target: OSRM_CAR_URL, changeOrigin: true, pathRewrite: { '^/osrm': '' } }));
app.use('/geocode', createProxyMiddleware({ target: NOMINATIM_URL, changeOrigin: true, pathRewrite: { '^/geocode': '' } }));

app.get('/health', (req, res) => {
    res.status(200).json({ status: 'UP', service: 'Itunda API Gateway (Node.js)' });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`🚀 Itunda API Gateway running on port ${PORT}`);
});
