const assert = require('node:assert/strict');

const originalFetch = global.fetch;
const docs = {
  'http://backend/v3/api-docs': {
    openapi: '3.0.3',
    paths: {
      '/api/v1/example': {
        get: {
          security: [{ bearerAuth: [] }],
          responses: { '200': { description: 'ok' } },
        },
      },
    },
    components: {
      schemas: { Example: { type: 'object' } },
      securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer' } },
    },
  },
  'http://payments/v3/api-docs': {
    openapi: '3.0.3',
    paths: {
      '/api/v1/payments': {
        post: {
          security: [{ bearerAuth: [] }],
          responses: { '200': { description: 'ok' } },
        },
      },
    },
    components: {
      schemas: { Payment: { type: 'object' } },
      securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer' } },
    },
  },
};

global.fetch = async (url) => {
  const document = docs[url];
  if (!document) return new Response('not found', { status: 404 });
  return new Response(JSON.stringify(document), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
};

process.env.BACKEND_URL = 'http://backend';
process.env.PAYMENT_SERVICE_URL = 'http://payments';

const { buildOpenApiDocument } = require('./openapi');

async function main() {
  const document = await buildOpenApiDocument();

  assert.equal(document.openapi, '3.0.3');
  assert.equal(document['x-itunda-contract-source'], 'runtime-service-openapi');
  assert.ok(document.paths['/api/v1/example']);
  assert.ok(document.paths['/api/v1/payments']);
  assert.ok(document.components.schemas.backend__Example);
  assert.ok(document.components.schemas.payments__Payment);
  assert.ok(document.components.securitySchemes.backend__bearerAuth);
  assert.ok(document.components.securitySchemes.payments__bearerAuth);
  assert.deepEqual(document.paths['/api/v1/example'].get.security, [{ backend__bearerAuth: [] }]);
  assert.deepEqual(document.paths['/api/v1/payments'].post.security, [{ payments__bearerAuth: [] }]);

  console.log('OpenAPI aggregation tests passed.');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
}).finally(() => {
  global.fetch = originalFetch;
});
