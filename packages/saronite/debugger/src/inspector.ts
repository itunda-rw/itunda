import type { SaroniteDebugMessage } from '@itunda/saronite-debug-protocol';
import type { SaroniteDebugSessionManager } from './session.js';

export interface SaroniteDebugInspector {
  sessions(): ReturnType<SaroniteDebugSessionManager['inspect']>;
  command(
    sessionId: string,
    authToken: string,
    command: Extract<SaroniteDebugMessage, { type: 'command' }>,
  ): boolean;
}

export function createSaroniteDebugInspector(manager: SaroniteDebugSessionManager): SaroniteDebugInspector {
  return {
    sessions: () => manager.inspect(),
    command: (sessionId, authToken, command) => manager.sendCommand(sessionId, authToken, command),
  };
}
