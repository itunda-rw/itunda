import type { SaroniteDebugMessage } from '@itunda/saronite-debug-protocol';

export interface SaroniteRelayClient {
  send(message: SaroniteDebugMessage): void;
  close(): void;
}

export interface SaroniteRelayClientOptions {
  host: string;
  port: number;
  relayToken: string;
  sessionId: string;
  authToken: string;
  socket: { write(data: string): void; end(): void };
}

export function createSaroniteRelayClient(options: SaroniteRelayClientOptions): SaroniteRelayClient {
  if (!options.relayToken || !options.sessionId || !options.authToken) {
    throw new TypeError('relay credentials are required');
  }
  return {
    send(message) {
      options.socket.write(JSON.stringify({
        relayToken: options.relayToken,
        sessionId: options.sessionId,
        authToken: options.authToken,
        message,
      }) + '\n');
    },
    close() {
      options.socket.end();
    },
  };
}
