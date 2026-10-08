import type { SaroniteDebugSessionManager } from './session.js';

export type SaroniteRelayAction = 'createSession' | 'inspect' | 'attach' | 'message';

export interface SaroniteRelayRequest {
  relayToken: string;
  action: SaroniteRelayAction;
  sessionId?: string;
  authToken?: string;
  platform?: 'android' | 'ios' | 'web';
  appId?: string;
  message?: unknown;
}

export function handleSaroniteRelayRequest(
  manager: SaroniteDebugSessionManager,
  request: SaroniteRelayRequest,
  relayToken: string,
): Record<string, unknown> {
  if (request.relayToken !== relayToken) return { ok: false, error: 'INVALID_RELAY_TOKEN' };

  if (request.action === 'createSession') {
    if (!request.platform) return { ok: false, error: 'PLATFORM_REQUIRED' };
    const session = manager.createSession({ platform: request.platform, appId: request.appId });
    return { ok: true, action: 'sessionCreated', ...session };
  }

  if (request.action === 'inspect') {
    return { ok: true, action: 'sessions', sessions: manager.inspect() };
  }

  if (!request.sessionId || !request.authToken) {
    return { ok: false, error: 'SESSION_CREDENTIALS_REQUIRED' };
  }

  if (request.action === 'attach') {
    return { ok: manager.authenticate(request.sessionId, request.authToken), action: 'authenticated' };
  }

  if (request.action === 'message') {
    const accepted = manager.receive(request.sessionId, request.authToken, request.message);
    return accepted ? { ok: true, action: 'messageAccepted' } : { ok: false, error: 'UNAUTHORIZED_OR_INVALID_MESSAGE' };
  }

  return { ok: false, error: 'UNKNOWN_ACTION' };
}
