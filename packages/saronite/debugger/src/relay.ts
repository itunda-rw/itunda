import { isSaroniteDebugMessage, type SaroniteDebugMessage, type SaroniteDebugTransport } from '@itunda/saronite-debug-protocol';

export interface SaroniteRelayFrame {
  sessionId: string;
  message: SaroniteDebugMessage;
}

export function encodeSaroniteRelayFrame(frame: SaroniteRelayFrame): string {
  if (!frame.sessionId || !isSaroniteDebugMessage(frame.message)) throw new TypeError('invalid relay frame');
  return JSON.stringify(frame);
}

export function decodeSaroniteRelayFrame(value: unknown): SaroniteRelayFrame | undefined {
  if (typeof value !== 'string') return undefined;
  try {
    const frame = JSON.parse(value) as Record<string, unknown>;
    if (typeof frame.sessionId !== 'string' || !isSaroniteDebugMessage(frame.message)) return undefined;
    return { sessionId: frame.sessionId, message: frame.message };
  } catch {
    return undefined;
  }
}

export function createWebSocketDebugTransport(
  socket: { send(data: string): void; close?(): void },
  sessionId: string,
): SaroniteDebugTransport {
  if (!socket || typeof socket.send !== 'function' || !sessionId) throw new TypeError('invalid relay socket');
  return {
    send(message) {
      socket.send(encodeSaroniteRelayFrame({ sessionId, message }));
    },
    close() {
      socket.close?.();
    },
  };
}
