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
  return message.version === SARONITE_DEBUG_PROTOCOL_VERSION && typeof message.type === 'string';
}
