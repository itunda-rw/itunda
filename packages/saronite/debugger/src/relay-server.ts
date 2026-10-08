import { createServer, type IncomingMessage, type Server as HttpServer } from 'node:http';
import type { Socket } from 'node:net';
import { createSaroniteDebugSessionManager, type SaroniteDebugSessionManager } from './session.js';

export interface SaroniteRelayServerOptions {
  host?: string;
  port?: number;
  manager?: SaroniteDebugSessionManager;
}

export interface SaroniteRelayServer {
  readonly manager: SaroniteDebugSessionManager;
  readonly server: HttpServer;
  listen(): Promise<{ host: string; port: number }>;
  close(): Promise<void>;
}

/**
 * Development-only HTTP relay bootstrap.
 *
 * This intentionally exposes only a local health/session endpoint. WebSocket
 * upgrade handling is kept out until a dedicated WebSocket implementation is
 * selected; no production listener should import this module.
 */
export function createSaroniteRelayServer(options: SaroniteRelayServerOptions = {}): SaroniteRelayServer {
  const host = options.host ?? '127.0.0.1';
  const port = options.port ?? 0;
  const manager = options.manager ?? createSaroniteDebugSessionManager();

  const server = createServer((request: IncomingMessage, response) => {
    if (request.method !== 'GET' || request.url !== '/health') {
      response.writeHead(404, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ ok: false, error: 'NOT_FOUND' }));
      return;
    }
    response.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    response.end(JSON.stringify({ ok: true, service: 'saronite-debug-relay', sessions: manager.inspect().length }));
  });

  server.on('upgrade', (_request: IncomingMessage, socket: Socket) => {
    socket.write('HTTP/1.1 426 Upgrade Required\\r\\nConnection: close\\r\\n\\r\\n');
    socket.destroy();
  });

  return {
    manager,
    server,
    listen() {
      return new Promise((resolve, reject) => {
        const onError = (error: Error) => reject(error);
        server.once('error', onError);
        server.listen(port, host, () => {
          server.off('error', onError);
          const address = server.address();
          if (!address || typeof address === 'string') {
            reject(new Error('relay server did not expose a TCP address'));
            return;
          }
          resolve({ host, port: address.port });
        });
      });
    },
    close() {
      return new Promise((resolve, reject) => {
        server.close((error) => error ? reject(error) : resolve());
      });
    },
  };
}
