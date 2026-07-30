const assert = require('node:assert/strict');
const http = require('node:http');

// Make the proxy failure deterministic: no local service can bind privileged
// port 1, so the test exercises the gateway's bounded 504/error-metric path.
process.env.PAYMENT_SERVICE_URL = 'http://127.0.0.1:1';

function request(port, path, headers = {}) {
    return new Promise((resolve, reject) => {
        http.get({ hostname: '127.0.0.1', port, path, headers }, (response) => {
            let body = '';
            response.on('data', (chunk) => { body += chunk; });
            response.on('end', () => resolve({ status: response.statusCode, body, headers: response.headers }));
        }).on('error', reject);
    });
}

function upgrade(port, path) {
    return new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port,
            path,
            headers: {
                Connection: 'Upgrade',
                Upgrade: 'websocket',
                'Sec-WebSocket-Key': 'dGhlIHNhbXBsZSBub25jZQ==',
                'Sec-WebSocket-Version': '13',
            },
        });
        req.on('upgrade', (response, socket) => {
            socket.destroy();
            resolve(response.statusCode);
        });
        req.on('error', reject);
        req.end();
    });
}

async function main() {
    let receivedWebSocketPath;
    let receivedWebSocketRequestId;
    const backend = http.createServer((req, res) => {
        res.setHeader('Cache-Control', 'public, max-age=3600');
        res.end(JSON.stringify({ path: req.url }));
    });
    backend.on('upgrade', (req, socket) => {
        receivedWebSocketPath = req.url;
        receivedWebSocketRequestId = req.headers['x-request-id'];
        socket.write('HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n');
        socket.end();
    });
    await new Promise((resolve) => backend.listen(0, '127.0.0.1', resolve));
    process.env.BACKEND_URL = `http://127.0.0.1:${backend.address().port}`;
    const { isAllowedCorsOrigin, isOperationalEndpoint, metricRoute, parseAllowedOrigins, parsePositiveTimeout, upstreamErrorCode, upstreamFailureResponse, isMessagingWebSocketUpgrade, requestIdFor, setSecurityHeaders, setResponseCachePolicy, startServer } = require('./index');

    assert.equal(isOperationalEndpoint({ path: '/health' }), true);
    assert.equal(isOperationalEndpoint({ path: '/metrics' }), true);
    assert.equal(isOperationalEndpoint({ path: '/api/v1/payments' }), false);
    assert.deepEqual([...parseAllowedOrigins('https://app.example, http://localhost:5000')], ['https://app.example', 'http://localhost:5000']);
    assert.equal(isAllowedCorsOrigin('http://localhost:5000'), true);
    assert.equal(isAllowedCorsOrigin('https://untrusted.example'), false);
    assert.equal(metricRoute('/api/v1/payments/123'), '/api/v1/payments');
    assert.equal(parsePositiveTimeout('15000', 1), 15000);
    assert.equal(parsePositiveTimeout('999', 15000), 15000);
    assert.equal(upstreamErrorCode({ code: 'ETIMEDOUT' }), 'ETIMEDOUT');
    assert.equal(upstreamErrorCode({ code: 'unexpected' }), 'OTHER');
    assert.deepEqual(upstreamFailureResponse({ code: 'ETIMEDOUT' }), { status: 504, error: 'UPSTREAM_TIMEOUT' });
    assert.deepEqual(upstreamFailureResponse({ code: 'ECONNREFUSED' }), { status: 503, error: 'UPSTREAM_UNAVAILABLE', retryAfterSeconds: 5 });
    assert.equal(isMessagingWebSocketUpgrade('/ws/messaging?token=redacted'), true);
    assert.equal(isMessagingWebSocketUpgrade('/api/v1/messages'), false);
    const requestLike = {};
    assert.equal(requestIdFor(requestLike), requestIdFor(requestLike));
    const headerTarget = { headers: {}, setHeader(key, value) { this.headers[key] = value; } };
    setSecurityHeaders(headerTarget);
    assert.equal(headerTarget.headers['X-Content-Type-Options'], 'nosniff');
    assert.equal(headerTarget.headers['Referrer-Policy'], 'no-referrer');
    setResponseCachePolicy({ path: '/api/v1/wallets' }, headerTarget);
    assert.equal(headerTarget.headers['Cache-Control'], 'no-store, private');

    const server = startServer(0, '127.0.0.1');
    await new Promise((resolve) => server.on('listening', resolve));
    try {
        const port = server.address().port;
        const health = await request(port, '/health');
        assert.equal(health.status, 200);
        assert.match(health.headers['x-request-id'], /^[0-9a-f-]{36}$/);
        assert.equal(health.headers['x-content-type-options'], 'nosniff');
        assert.equal(health.headers['referrer-policy'], 'no-referrer');
        assert.equal(health.headers['x-frame-options'], 'DENY');
        assert.equal(health.headers['permissions-policy'], 'camera=(), microphone=(), geolocation=()');
        assert.equal(health.headers['x-powered-by'], undefined);
        const allowedCors = await request(port, '/health', { Origin: 'http://localhost:5000' });
        assert.equal(allowedCors.headers['access-control-allow-origin'], 'http://localhost:5000');
        const deniedCors = await request(port, '/health', { Origin: 'https://untrusted.example' });
        assert.equal(deniedCors.headers['access-control-allow-origin'], undefined);

        const failedProxy = await request(port, '/api/v1/payments/health');
        assert.equal(failedProxy.status, 503);
        assert.match(failedProxy.body, /UPSTREAM_UNAVAILABLE/);
        assert.equal(failedProxy.headers['retry-after'], '5');
        const proxiedApi = await request(port, '/api/v1/profile');
        assert.equal(proxiedApi.status, 200);
        assert.equal(proxiedApi.headers['cache-control'], 'no-store, private');

        const upgradeStatus = await upgrade(port, '/ws/messaging?token=redacted');
        assert.equal(upgradeStatus, 101);
        assert.equal(receivedWebSocketPath, '/ws/messaging?token=redacted');
        assert.match(receivedWebSocketRequestId, /^[0-9a-f-]{36}$/);

        const metrics = await request(port, '/metrics');
        assert.equal(metrics.status, 200);
        assert.match(metrics.body, /itunda_gateway_upstream_failures_total\{route="\/api\/v1\/payments",error_code="ECONNREFUSED"\} 1/);
    } finally {
        await new Promise((resolve) => server.close(resolve));
        await new Promise((resolve) => backend.close(resolve));
    }
}

main().then(
    () => console.log('API gateway tests passed.'),
    (error) => {
        console.error(error);
        process.exitCode = 1;
    },
);
