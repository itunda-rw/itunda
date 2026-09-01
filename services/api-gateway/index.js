const express = require('express');
const { randomUUID } = require('node:crypto');
const { createProxyMiddleware } = require('http-proxy-middleware');
const rateLimit = require('express-rate-limit');
const cors = require('cors');
const promClient = require('prom-client');

const app = express();
app.disable('x-powered-by');
const DEFAULT_CORS_ALLOWED_ORIGINS = [
    'http://localhost:3000',
    'http://localhost:5000',
    'http://localhost:5001',
    'http://localhost:5002',
    'http://localhost:5003',
    'http://localhost:5004',
    'http://localhost:5005',
];

function parseAllowedOrigins(value) {
    const origins = (value || DEFAULT_CORS_ALLOWED_ORIGINS.join(','))
        .split(',')
        .map((origin) => origin.trim())
        .filter(Boolean);
    return new Set(origins);
}

const corsAllowedOrigins = parseAllowedOrigins(process.env.CORS_ALLOWED_ORIGINS);
function isAllowedCorsOrigin(origin) {
    // Native apps, same-origin requests, health probes, and curl do not send an
    // Origin header. Browser requests must be explicitly configured.
    return !origin || corsAllowedOrigins.has(origin);
}

function setHeader(target, name, value) {
    const set = typeof target.setHeader === 'function'
        ? (name, value) => target.setHeader(name, value)
        : (name, value) => {
            target.headers = target.headers || {};
            target.headers[name.toLowerCase()] = value;
        };
    set(name, value);
}

function setSecurityHeaders(target) {
    setHeader(target, 'X-Content-Type-Options', 'nosniff');
    setHeader(target, 'Referrer-Policy', 'no-referrer');
    setHeader(target, 'X-Frame-Options', 'DENY');
    setHeader(target, 'Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
}

function setResponseCachePolicy(req, target) {
    // Financial, account, and admin API responses must never be stored by a browser
    // or an intermediary. Static map routes deliberately retain their upstream cache
    // policy because they do not carry account data.
    if (req.path.startsWith('/api/v1/')) {
        setHeader(target, 'Cache-Control', 'no-store, private');
    }
}

// Only the Istio sidecar (loopback) and Kubernetes pod network may supply a
// forwarding chain. `true` trusts every hop and lets a direct caller choose its
// own X-Forwarded-For value, which defeats IP-based rate limits.
const TRUST_PROXY_CIDRS = process.env.TRUST_PROXY_CIDRS || 'loopback, 10.244.0.0/16';
app.set('trust proxy', TRUST_PROXY_CIDRS);
app.use(cors({
    origin: (origin, callback) => callback(null, isAllowedCorsOrigin(origin)),
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Authorization', 'Content-Type', 'Idempotency-Key'],
    exposedHeaders: ['X-Request-ID'],
    maxAge: 600,
}));
app.use((req, res, next) => {
    // These API-safe headers apply at the edge, including health and error responses.
    // HSTS is intentionally configured at the HTTPS terminator, never on this
    // clear-text in-cluster listener.
    setSecurityHeaders(res);
    setResponseCachePolicy(req, res);
    next();
});

function requestIdFor(req) {
    if (!req.itundaRequestId) req.itundaRequestId = randomUUID();
    return req.itundaRequestId;
}

app.use((req, res, next) => {
    res.setHeader('X-Request-ID', requestIdFor(req));
    next();
});

function isOperationalEndpoint(req) {
    return req.path === '/health' || req.path === '/metrics';
}

// Real rate limiting (2026-07-25) -- this gateway is now reachable from the
// public internet (bore.pub tunnel -> Istio ingress -> here), so it needs a
// first line of defense before it's treated as a shareable demo URL. Istio's
// Istio's ingressgateway sets X-Forwarded-For, and only the explicitly trusted
// mesh hops above are allowed to extend that chain. Traffic without an original
// client address (for example a raw TCP tunnel) still collapses to one key.
app.use(rateLimit({
    windowMs: 60 * 1000,
    limit: 300,
    standardHeaders: true,
    legacyHeaders: false,
    // Kubernetes probes and Prometheus scrapes must remain available when a
    // public caller exhausts the shared limiter key.
    skip: isOperationalEndpoint,
}));
const moneyMovementLimiter = rateLimit({
    windowMs: 60 * 1000,
    limit: 30,
    standardHeaders: true,
    legacyHeaders: false,
});

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
const upstreamFailures = new promClient.Counter({
    name: 'itunda_gateway_upstream_failures_total',
    help: 'Failed gateway attempts to reach an upstream service',
    // Keep this intentionally small and predictable: raw error messages (and
    // arbitrary DNS names) would turn a scrape-time metric into a cardinality
    // incident during an outage.
    labelNames: ['route', 'error_code'],
    registers: [metricsRegistry]
});

function metricRoute(path) {
    if (path === '/health' || path === '/metrics') return path;
    if (path.startsWith('/api/v1/')) {
        const resource = path.split('/')[3];
        return resource ? `/api/v1/${resource}` : '/api/v1';
    }
    for (const prefix of ['/tiles', '/glyphs', '/osrm', '/geocode']) {
        if (path === prefix || path.startsWith(`${prefix}/`)) return prefix;
    }
    return 'other';
}

app.use((req, res, next) => {
    const route = metricRoute(req.path);
    const stop = httpRequestDuration.startTimer({ method: req.method });
    res.on('finish', () => stop({ route, status_code: res.statusCode }));
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
// Real, first independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up to the 2026-07-11 microservices decision.
// Same routing-precedence reasoning as PAYMENT_SERVICE_URL/LEDGER_SERVICE_URL above:
// registered before the /api/v1 catch-all to services/backend, which no longer
// serves /api/v1/card/** at all (the :card Gradle module was removed from :app).
const CARD_SERVICE_URL = process.env.CARD_SERVICE_URL || 'http://localhost:4002';
// Real, second independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up. Same routing-precedence reasoning as
// CARD_SERVICE_URL above: registered before the /api/v1 catch-all to
// services/backend, which no longer serves /api/v1/insurance/** or
// /api/v1/system/insurance-claims at all (the :insurance Gradle module was
// removed from :app). The admin route lives under the shared /api/v1/system/**
// prefix other unrelated modules also use, so only its own specific sub-path is
// routed here -- never the whole /api/v1/system prefix.
const INSURANCE_SERVICE_URL = process.env.INSURANCE_SERVICE_URL || 'http://localhost:4003';
// Real, third independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up. Same routing-precedence reasoning as
// CARD_SERVICE_URL/INSURANCE_SERVICE_URL above. Covers all three of :agents'
// own route prefixes (/api/v1/agent singular -- AgentOperatorController,
// /api/v1/agents plural -- AgentDiscoveryController, /api/v1/float-marketplace)
// plus its admin sub-path under the shared /api/v1/system/** prefix.
const AGENTS_SERVICE_URL = process.env.AGENTS_SERVICE_URL || 'http://localhost:4004';
function parsePositiveTimeout(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    return Number.isInteger(parsed) && parsed >= 1000 ? parsed : fallback;
}
const UPSTREAM_TIMEOUT_MS = parsePositiveTimeout(process.env.UPSTREAM_TIMEOUT_MS, 15000);

function upstreamErrorCode(error) {
    switch (error && error.code) {
    case 'ECONNREFUSED':
    case 'ECONNRESET':
    case 'ETIMEDOUT':
    case 'EAI_AGAIN':
    case 'ENOTFOUND':
        return error.code;
    default:
        return 'OTHER';
    }
}

function upstreamFailureResponse(error) {
    if (error && error.code === 'ETIMEDOUT') {
        return { status: 504, error: 'UPSTREAM_TIMEOUT' };
    }
    return { status: 503, error: 'UPSTREAM_UNAVAILABLE', retryAfterSeconds: 5 };
}

// App-layer circuit breaker (2026-08-29). Toss's own real Gateway architecture runs
// circuit breaking at two layers -- infra (Istio) and app (Resilience4j) -- kept
// deliberately separate because Istio's own granularity alone was judged too coarse
// (docs/TOSS_ARCHITECTURE_FACTS.md §8). This gateway already has the infra layer (an
// Istio sidecar sits in front of it -- see the `trust proxy` CIDR comment above) but
// had no app layer at all: every request to a downed upstream burned the full
// UPSTREAM_TIMEOUT_MS one at a time, forever, instead of failing fast once the
// upstream is known to be down. Scoped per upstream target (there are few enough of
// them that target-level granularity is the right size), not per route.
function parsePositiveCount(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    return Number.isInteger(parsed) && parsed >= 1 ? parsed : fallback;
}
const CIRCUIT_FAILURE_THRESHOLD = parsePositiveCount(process.env.CIRCUIT_FAILURE_THRESHOLD, 5);
const CIRCUIT_OPEN_DURATION_MS = parsePositiveTimeout(process.env.CIRCUIT_OPEN_DURATION_MS, 30000);

const circuits = new Map(); // target -> { state, consecutiveFailures, openedAt }
function circuitFor(target) {
    let circuit = circuits.get(target);
    if (!circuit) {
        circuit = { state: 'CLOSED', consecutiveFailures: 0, openedAt: 0 };
        circuits.set(target, circuit);
    }
    return circuit;
}

const circuitStateGauge = new promClient.Gauge({
    name: 'itunda_gateway_circuit_state',
    help: 'Circuit breaker state per upstream target (0=closed, 1=open, 2=half-open)',
    labelNames: ['target'],
    registers: [metricsRegistry],
});
const circuitShortCircuits = new promClient.Counter({
    name: 'itunda_gateway_circuit_short_circuited_total',
    help: "Requests rejected immediately because the target upstream's circuit was open",
    labelNames: ['target'],
    registers: [metricsRegistry],
});

function setCircuitState(target, circuit, state) {
    circuit.state = state;
    circuitStateGauge.set({ target }, state === 'OPEN' ? 1 : state === 'HALF_OPEN' ? 2 : 0);
}

// Only connection-level failures count against the breaker -- the same
// onError-vs-onProxyRes distinction upstreamFailures already draws. A real HTTP
// 4xx/5xx business response from a healthy, reachable upstream must never trip it.
function recordCircuitFailure(target) {
    const circuit = circuitFor(target);
    circuit.consecutiveFailures += 1;
    if (circuit.consecutiveFailures >= CIRCUIT_FAILURE_THRESHOLD) {
        circuit.openedAt = Date.now();
        setCircuitState(target, circuit, 'OPEN');
    }
}

function recordCircuitSuccess(target) {
    const circuit = circuitFor(target);
    circuit.consecutiveFailures = 0;
    if (circuit.state !== 'CLOSED') setCircuitState(target, circuit, 'CLOSED');
}

// Gate placed in front of the real proxy middleware for a given target. When open,
// rejects the request without the upstream ever being attempted.
function circuitGate(target) {
    return (req, res, next) => {
        const circuit = circuitFor(target);
        if (circuit.state === 'OPEN') {
            if (Date.now() - circuit.openedAt < CIRCUIT_OPEN_DURATION_MS) {
                circuitShortCircuits.inc({ target });
                res.set('Retry-After', String(Math.ceil(CIRCUIT_OPEN_DURATION_MS / 1000)));
                res.status(503).json({ success: false, error: 'UPSTREAM_CIRCUIT_OPEN' });
                return;
            }
            // Cooldown elapsed -- let exactly one trial request through rather than
            // resetting straight to CLOSED, so a still-down upstream re-opens on that
            // single trial instead of needing a whole new failure streak to notice.
            setCircuitState(target, circuit, 'HALF_OPEN');
        }
        next();
    };
}

function upstreamProxy(target, options = {}) {
    const { onProxyReq: userOnProxyReq, onProxyRes: userOnProxyRes, ...proxyOptions } = options;
    const proxyMiddleware = createProxyMiddleware({
        target,
        changeOrigin: true,
        // Keep the caller/gateway timeout bounded and consistent. Payment state is
        // reconciled through idempotency and durable events rather than a client
        // waiting indefinitely on a stalled upstream socket.
        timeout: UPSTREAM_TIMEOUT_MS,
        proxyTimeout: UPSTREAM_TIMEOUT_MS,
        onError: (error, req, res) => {
            upstreamFailures.inc({
                route: metricRoute(req.path),
                error_code: upstreamErrorCode(error),
            });
            recordCircuitFailure(target);
            if (!res.headersSent) {
                const failure = upstreamFailureResponse(error);
                if (failure.retryAfterSeconds) res.set('Retry-After', String(failure.retryAfterSeconds));
                res.status(failure.status).json({ success: false, error: failure.error });
            }
        },
        onProxyReq: (proxyReq, req, res) => {
            proxyReq.setHeader('X-Request-ID', requestIdFor(req));
            if (userOnProxyReq) userOnProxyReq(proxyReq, req, res);
        },
        onProxyRes: (proxyRes, req, res) => {
            // A response of any status code proves the upstream is reachable.
            recordCircuitSuccess(target);
            // Proxy response headers can replace Express's pre-set values, so enforce
            // the invariant on the upstream response itself as well.
            setSecurityHeaders(proxyRes);
            setResponseCachePolicy(req, proxyRes);
            if (userOnProxyRes) userOnProxyRes(proxyRes, req, res);
        },
        ...proxyOptions,
    });
    return [circuitGate(target), proxyMiddleware];
}

function isMessagingWebSocketUpgrade(url) {
    return typeof url === 'string' && url.split('?', 1)[0] === '/ws/messaging';
}

// WebSocket upgrades bypass Express middleware, so the ordinary /api/v1 proxy
// cannot carry the real-time messaging path. Keep a dedicated proxy and attach
// it directly to the Node HTTP server below; this also preserves the path for
// Spring's /ws/messaging handler instead of rewriting it as an API request.
// Deliberately not behind the circuitGate above: each call is one long-lived
// connection rather than a repeated request/response, so a per-request breaker
// model doesn't fit it -- a downed backend still fails each upgrade attempt on
// its own bounded timeout via onError below.
const messagingWebSocketProxy = createProxyMiddleware({
    target: BACKEND_URL,
    changeOrigin: true,
    ws: true,
    // The browser handshake currently contains a JWT query parameter. Do not let
    // http-proxy-middleware format a failed upgrade URL into process logs; the
    // bounded metric below retains the operational signal without a credential.
    logLevel: 'silent',
    onProxyReqWs: (proxyReq, req) => {
        proxyReq.setHeader('X-Request-ID', requestIdFor(req));
    },
    onError: (error, req, socket) => {
        upstreamFailures.inc({
            route: '/ws/messaging',
            error_code: upstreamErrorCode(error),
        });
        socket.destroy();
    },
});

app.use('/api/v1/payments', moneyMovementLimiter, upstreamProxy(PAYMENT_SERVICE_URL));

app.use('/api/v1/ledger', moneyMovementLimiter, upstreamProxy(LEDGER_SERVICE_URL));

app.use('/api/v1/card', moneyMovementLimiter, upstreamProxy(CARD_SERVICE_URL));
app.use('/api/v1/system/insurance-claims', moneyMovementLimiter, upstreamProxy(INSURANCE_SERVICE_URL));
app.use('/api/v1/insurance', moneyMovementLimiter, upstreamProxy(INSURANCE_SERVICE_URL));
app.use('/api/v1/system/agents', moneyMovementLimiter, upstreamProxy(AGENTS_SERVICE_URL));
app.use('/api/v1/agent', moneyMovementLimiter, upstreamProxy(AGENTS_SERVICE_URL));
app.use('/api/v1/agents', moneyMovementLimiter, upstreamProxy(AGENTS_SERVICE_URL));
app.use('/api/v1/float-marketplace', moneyMovementLimiter, upstreamProxy(AGENTS_SERVICE_URL));

app.use('/api/v1', upstreamProxy(BACKEND_URL));

// Real self-hosted Maps geo-stack proxy (2026-07-24) -- rides this same gateway's
// existing public tunnel instead of needing a separate bore.pub tunnel per service.
// OSRM/Nominatim/tiles/glyphs all listen on itunda-dc-a's own host network (not a
// k8s Service), reachable from this pod via the node's IP.
const TILES_URL = process.env.TILES_URL || 'http://192.168.252.4:8090';
const GLYPHS_URL = process.env.GLYPHS_URL || 'http://192.168.252.4:8091';
const OSRM_CAR_URL = process.env.OSRM_CAR_URL || 'http://192.168.252.4:5000';
const OSRM_FOOT_URL = process.env.OSRM_FOOT_URL || 'http://192.168.252.4:5001';
const NOMINATIM_URL = process.env.NOMINATIM_URL || 'http://192.168.252.4:8088';

app.use('/tiles', upstreamProxy(TILES_URL, { pathRewrite: { '^/tiles': '' } }));
app.use('/glyphs', upstreamProxy(GLYPHS_URL, { pathRewrite: { '^/glyphs': '' } }));
app.use('/osrm/foot', upstreamProxy(OSRM_FOOT_URL, { pathRewrite: { '^/osrm/foot': '' } }));
app.use('/osrm', upstreamProxy(OSRM_CAR_URL, { pathRewrite: { '^/osrm': '' } }));
app.use('/geocode', upstreamProxy(NOMINATIM_URL, { pathRewrite: { '^/geocode': '' } }));

app.get('/health', (req, res) => {
    res.status(200).json({ status: 'UP', service: 'Itunda API Gateway (Node.js)' });
});

const PORT = process.env.PORT || 3000;
function startServer(port = PORT, host) {
    const server = host ? app.listen(port, host) : app.listen(port);
    server.on('upgrade', (req, socket, head) => {
        if (isMessagingWebSocketUpgrade(req.url)) {
            requestIdFor(req);
            messagingWebSocketProxy.upgrade(req, socket, head);
        } else {
            socket.destroy();
        }
    });
    return server;
}

if (require.main === module) {
    startServer(PORT).on('listening', () => {
        console.log(`🚀 Itunda API Gateway running on port ${PORT}`);
    });
}

module.exports = { app, metricRoute, parseAllowedOrigins, isAllowedCorsOrigin, parsePositiveTimeout, upstreamErrorCode, upstreamFailureResponse, isOperationalEndpoint, isMessagingWebSocketUpgrade, requestIdFor, setSecurityHeaders, setResponseCachePolicy, startServer };
