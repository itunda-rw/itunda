import { isSaroniteDebugMessage, type SaroniteDebugMessage, type SaroniteDebugTransport } from '@itunda/saronite-debug-protocol';

export interface SaroniteDebugSession {
  readonly sessionId: string;
  readonly authToken: string;
  readonly platform: 'android' | 'ios' | 'web';
  readonly appId?: string;
  readonly createdAt: number;
  readonly messageCount: number;
}

export interface SaroniteDebugSessionManager {
  createSession(input: { platform: SaroniteDebugSession['platform']; appId?: string }): { sessionId: string; authToken: string };
  authenticate(sessionId: string, authToken: string): boolean;
  attach(sessionId: string, authToken: string, transport: SaroniteDebugTransport): void;
  detach(sessionId: string): void;
  receive(sessionId: string, authToken: string, value: unknown): boolean;
  sendCommand(sessionId: string, authToken: string, command: Extract<SaroniteDebugMessage, { type: 'command' }>): boolean;
  inspect(): SaroniteDebugSession[];
}

interface MutableSession extends SaroniteDebugSession {
  token: string;
  transport?: SaroniteDebugTransport;
}

function randomToken(): string {
  const bytes = new Uint8Array(24);
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes);
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  }
  return `dev-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function randomSessionId(): string {
  return `saronite-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function createSaroniteDebugSessionManager(): SaroniteDebugSessionManager {
  const sessions = new Map<string, MutableSession>();

  return {
    createSession(input) {
      const sessionId = randomSessionId();
      const authToken = randomToken();
      sessions.set(sessionId, {
        sessionId,
        authToken,
        token: authToken,
        platform: input.platform,
        appId: input.appId,
        createdAt: Date.now(),
        messageCount: 0,
      });
      return { sessionId, authToken };
    },

    authenticate(sessionId, authToken) {
      const session = sessions.get(sessionId);
      return Boolean(session && session.token === authToken);
    },

    attach(sessionId, authToken, transport) {
      if (!this.authenticate(sessionId, authToken)) throw new Error('invalid debug session');
      sessions.get(sessionId)!.transport = transport;
    },

    detach(sessionId) {
      const session = sessions.get(sessionId);
      session?.transport?.close?.();
      sessions.delete(sessionId);
    },

    receive(sessionId, authToken, value) {
      if (!this.authenticate(sessionId, authToken) || !isSaroniteDebugMessage(value)) return false;
      const session = sessions.get(sessionId)!;
      session.messageCount += 1;
      return true;
    },

    sendCommand(sessionId, authToken, command) {
      if (!this.authenticate(sessionId, authToken)) return false;
      const session = sessions.get(sessionId);
      if (!session?.transport) return false;
      session.transport.send(command);
      return true;
    },

    inspect() {
      return [...sessions.values()].map(({ token: _token, authToken: _authToken, ...safe }) => safe);
    },
  };
}
