const DEFAULT_TIMEOUT_MS = 5000;

function serviceUrl(name, fallback) {
    return process.env[name] || fallback;
}

function openApiUpstreams() {
    return [
        ['backend', serviceUrl('BACKEND_URL', 'http://localhost:4001')],
        ['payments', serviceUrl('PAYMENT_SERVICE_URL', 'http://localhost:8081')],
        ['ledger', serviceUrl('LEDGER_SERVICE_URL', 'http://localhost:8082')],
        ['card', serviceUrl('CARD_SERVICE_URL', 'http://localhost:4002')],
        ['insurance', serviceUrl('INSURANCE_SERVICE_URL', 'http://localhost:4003')],
        ['agents', serviceUrl('AGENTS_SERVICE_URL', 'http://localhost:4004')],
        ['transit', serviceUrl('TRANSIT_SERVICE_URL', 'http://localhost:4005')],
        ['certificate', serviceUrl('CERTIFICATE_SERVICE_URL', 'http://localhost:4006')],
        ['bills', serviceUrl('BILLS_SERVICE_URL', 'http://localhost:4007')],
        ['vehicle', serviceUrl('VEHICLE_SERVICE_URL', 'http://localhost:4008')],
        ['partners', serviceUrl('PARTNERS_SERVICE_URL', 'http://localhost:4009')],
        ['identity', serviceUrl('IDENTITY_SERVICE_URL', 'http://localhost:4010')],
        ['overview', serviceUrl('OVERVIEW_SERVICE_URL', 'http://localhost:4011')],
        ['knowledge', serviceUrl('KNOWLEDGE_SERVICE_URL', 'http://localhost:4012')],
        ['notifications', serviceUrl('NOTIFICATIONS_SERVICE_URL', 'http://localhost:4013')],
        ['analytics', serviceUrl('ANALYTICS_SERVICE_URL', 'http://localhost:4014')],
        ['loans', serviceUrl('LOANS_SERVICE_URL', 'http://localhost:4015')],
    ];
}

async function fetchJson(url, timeoutMs = DEFAULT_TIMEOUT_MS) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch(url, {
            signal: controller.signal,
            headers: { Accept: 'application/json' },
        });
        if (!response.ok) {
            throw new Error(`OpenAPI upstream returned HTTP ${response.status}: ${url}`);
        }
        return await response.json();
    } finally {
        clearTimeout(timeout);
    }
}

function rewriteRefs(value, prefix) {
    if (Array.isArray(value)) {
        return value.map((item) => rewriteRefs(item, prefix));
    }
    if (!value || typeof value !== 'object') {
        if (typeof value === 'string' && value.startsWith('#/components/')) {
            return value.replace(/^#\/components\/([^/]+)\/(.+)$/, '#/components/$1/' + prefix + '$2');
        }
        return value;
    }

    const out = {};
    for (const [key, child] of Object.entries(value)) {
        if (key === 'security' && Array.isArray(child)) {
            out[key] = child.map((requirement) =>
                Object.fromEntries(
                    Object.entries(requirement).map(([scheme, scopes]) => [prefix + scheme, scopes]),
                ),
            );
        } else {
            out[key] = rewriteRefs(child, prefix);
        }
    }
    return out;
}

function mergeComponents(target, source, prefix) {
    for (const [group, entries] of Object.entries(source || {})) {
        if (!target[group]) target[group] = {};
        for (const [name, value] of Object.entries(entries || {})) {
            const renamed = `${prefix}${name}`;
            target[group][renamed] = rewriteRefs(value, prefix);
        }
    }
}

async function buildOpenApiDocument(upstreams = openApiUpstreams()) {
    const documents = await Promise.all(
        upstreams.map(async ([name, baseUrl]) => {
            const url = new URL('/v3/api-docs', baseUrl).toString();
            return [name, await fetchJson(url)];
        }),
    );

    const merged = {
        openapi: '3.0.3',
        info: {
            title: 'Itunda Public API',
            version: 'v1',
            description: 'Generated at runtime from the verified Spring/OpenAPI contracts of every API service behind the Itunda gateway. This endpoint fails closed if any registered API service cannot provide its contract.',
        },
        servers: [{ url: '/' }],
        paths: {},
        components: {},
        tags: [],
        'x-itunda-contract-source': 'runtime-service-openapi',
    };

    for (const [name, document] of documents) {
        const prefix = `${name}__`;
        for (const [path, item] of Object.entries(document.paths || {})) {
            if (merged.paths[path]) {
                throw new Error(`Duplicate OpenAPI path across services: ${path}`);
            }
            merged.paths[path] = rewriteRefs(item, prefix);
        }
        mergeComponents(merged.components, document.components, prefix);
        for (const tag of document.tags || []) {
            merged.tags.push({ ...tag, 'x-itunda-service': name });
        }
    }

    return merged;
}

module.exports = { buildOpenApiDocument, openApiUpstreams };
