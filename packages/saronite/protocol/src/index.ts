export const SARONITE_PROTOCOL_VERSION = 1 as const;

export type SaroniteMessageKind = 'request' | 'response' | 'event';

export type SaroniteLifecycle =
  | 'installing'
  | 'installed'
  | 'launching'
  | 'visible'
  | 'hidden'
  | 'suspended'
  | 'terminated'
  | 'failed';

export type SaronitePermissionState = 'requested' | 'granted' | 'denied' | 'revoked';

export type SaroniteErrorCode =
  | 'INVALID_REQUEST'
  | 'UNSUPPORTED_VERSION'
  | 'UNKNOWN_CAPABILITY'
  | 'PERMISSION_DENIED'
  | 'UNSUPPORTED'
  | 'INVALID_STATE'
  | 'TIMEOUT'
  | 'INTERNAL_ERROR';

export type SaroniteRequest<T = unknown> = {
  protocolVersion: typeof SARONITE_PROTOCOL_VERSION;
  kind: 'request';
  id: string;
  capability: string;
  method: string;
  payload?: T;
  timeoutMs?: number;
};

export type SaroniteResponse<T = unknown> = {
  protocolVersion: typeof SARONITE_PROTOCOL_VERSION;
  kind: 'response';
  id: string;
  ok: boolean;
  result?: T;
  error?: {
    code: SaroniteErrorCode;
    message: string;
    detail?: unknown;
  };
};

export type SaroniteEvent<T = unknown> = {
  protocolVersion: typeof SARONITE_PROTOCOL_VERSION;
  kind: 'event';
  id: string;
  event: string;
  lifecycle?: SaroniteLifecycle;
  permission?: {
    name: string;
    state: SaronitePermissionState;
  };
  payload?: T;
};

export type SaroniteMessage =
  | SaroniteRequest
  | SaroniteResponse
  | SaroniteEvent;

export function isSaroniteMessage(value: unknown): value is SaroniteMessage {
  if (!value || typeof value !== 'object') return false;
  const message = value as Record<string, unknown>;
  return (
    message.protocolVersion === SARONITE_PROTOCOL_VERSION &&
    (message.kind === 'request' ||
      message.kind === 'response' ||
      message.kind === 'event') &&
    typeof message.id === 'string' &&
    message.id.length > 0
  );
}

export function createCorrelationId(prefix = 'srn'): string {
  const random =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : Math.random().toString(36).slice(2);
  return `${prefix}_${random}`;
}
