export const SARONITE_DEBUG_PROTOCOL_VERSION = 1 as const;

export type SaroniteDebugMessage =
  | { version: 1; type: 'hello'; sessionId: string; platform: 'android' | 'ios' | 'web'; appId?: string }
  | { version: 1; type: 'log'; id: string; timestamp: number; direction: 'request' | 'response' | 'event'; capability: string; method: string; payload?: unknown; durationMs?: number; error?: string }
  | { version: 1; type: 'state'; state: Record<string, unknown> }
  | { version: 1; type: 'command'; id: string; command: 'reset' | 'clearLogs' | 'setLatency' | 'setPermission'; payload?: unknown }
  | { version: 1; type: 'commandResult'; id: string; ok: boolean; result?: unknown; error?: string };

export interface SaroniteDebugTransport {
  send(message: SaroniteDebugMessage): void;
  close?(): void;
}

export function isSaroniteDebugMessage(value: unknown): value is SaroniteDebugMessage {
  if (!value || typeof value !== 'object') return false;
  const message = value as Record<string, unknown>;
  if (message.version !== SARONITE_DEBUG_PROTOCOL_VERSION || typeof message.type !== 'string') return false;
  if (message.type === 'hello') return typeof message.sessionId === 'string' && ['android', 'ios', 'web'].includes(String(message.platform));
  if (message.type === 'log') return typeof message.id === 'string' && typeof message.timestamp === 'number' && ['request', 'response', 'event'].includes(String(message.direction)) && typeof message.capability === 'string' && typeof message.method === 'string';
  if (message.type === 'state') return typeof message.state === 'object' && message.state !== null && !Array.isArray(message.state);
  if (message.type === 'command') return typeof message.id === 'string' && ['reset', 'clearLogs', 'setLatency', 'setPermission'].includes(String(message.command));
  if (message.type === 'commandResult') return typeof message.id === 'string' && typeof message.ok === 'boolean';
  return false;
}


export function createSaroniteDebugTransport(
  send: (message: SaroniteDebugMessage) => void,
  close?: () => void,
): SaroniteDebugTransport {
  return { send, close };
}

export function installSaroniteDebugTransport(
  transport: SaroniteDebugTransport | undefined,
): void {
  (globalThis as typeof globalThis & {
    __saroniteDebugTransport?: SaroniteDebugTransport;
  }).__saroniteDebugTransport = transport;
}

export function clearSaroniteDebugTransport(): void {
  delete (globalThis as typeof globalThis & {
    __saroniteDebugTransport?: SaroniteDebugTransport;
  }).__saroniteDebugTransport;
}
