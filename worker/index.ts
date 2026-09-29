export interface Env {
  ASSETS: Fetcher;
}

const json = (body: unknown, init: ResponseInit = {}) =>
  new Response(JSON.stringify(body), {
    ...init,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...init.headers,
    },
  });

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/api/health") {
      return json({
        ok: true,
        service: "itunda-web",
        mode: "edge-dynamic",
        timestamp: new Date().toISOString(),
      });
    }

    if (url.pathname === "/api/version") {
      return json({
        name: "Itunda",
        web: "edge",
        designSystem: "ids",
      });
    }

    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
