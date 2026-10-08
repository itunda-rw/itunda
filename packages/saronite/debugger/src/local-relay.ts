import { createServer, type Server } from 'node:net';
import { randomBytes } from 'node:crypto';
import type { SaroniteDebugMessage } from '@itunda/saronite-debug-protocol';
import { createSaroniteDebugInspector } from './inspector.js';
import { createSaroniteDebugSessionManager, type SaroniteDebugSessionManager } from './session.js';

export interface SaroniteLocalRelay {
  readonly manager: SaroniteDebugSessionManager;
  readonly host: string;
  readonly port: number;
  readonly token: string;
  readonly inspector: ReturnType<typeof createSaroniteDebugInspector>;
  start(): Promise<void>;
  stop(): Promise<void>;
}

export function createSaroniteLocalRelay(host = '127.0.0.1', port = 0): SaroniteLocalRelay {
  const token = randomBytes(24).toString('hex');
  const manager = createSaroniteDebugSessionManager();
  const inspector = createSaroniteDebugInspector(manager);
  const server: Server = createServer((socket) => {
    let buffer = '';
    socket.setEncoding('utf8');
    socket.on('data', (chunk) => {
      buffer += chunk;
      let newline = buffer.indexOf('\n');
      while (newline >= 0) {
        const line = buffer.slice(0, newline);
        buffer = buffer.slice(newline + 1);
        newline = buffer.indexOf('\n');
        try {
          const frame = JSON.parse(line) as { relayToken?: string; sessionId?: string; authToken?: string; message?: unknown };
          if (frame.relayToken !== token || !frame.sessionId || !frame.authToken || !frame.message) continue;
          if (manager.receive(frame.sessionId, frame.authToken, frame.message)) {
            socket.write(JSON.stringify({ ok: true }) + '\n');
          } else {
            socket.write(JSON.stringify({ ok: false, error: 'UNAUTHORIZED_OR_INVALID_MESSAGE' }) + '\n');
          }
        } catch {
          socket.write(JSON.stringify({ ok: false, error: 'INVALID_FRAME' }) + '\n');
        }
      }
    });
  });

  let activePort = port;
  return {
    manager,
    host,
    get port() { return activePort; },
    token,
    inspector,
    async start() {
      await new Promise<void>((resolve, reject) => {
        server.once('error', reject);
        server.listen(port, host, () => {
          const address = server.address();
          activePort = typeof address === 'object' && address ? address.port : port;
          resolve();
        });
      });
    },
    async stop() {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    },
  };
}
