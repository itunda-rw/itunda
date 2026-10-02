/**
 * Saronite host protocol.
 *
 * Platform-neutral wire contract shared by the mini-app SDK and native host.
 * Keep this file free of React Native, browser, Android, and iOS dependencies.
 *
 * The shape intentionally separates:
 * - request/response correlation
 * - lifecycle events
 * - permission failures
 * - capability failures
 * - protocol/version negotiation
 *
 * Itunda owns this protocol; it is inspired by the public shape of Toss's
 * mini-app tooling, not copied from private Toss code.
 */

export const SARONITE_PROTOCOL_VERSION = 1 as const;
export const SARONITE_PROTOCOL_NAME = 'saronite' as const;

export type SaroniteProtocolVersion = typeof SARONITE_PROTOCOL_VERSION;

export type SaroniteLifecycle =
  | 'created'
  | 'visible'
  | 'hidden'
  | 'suspended'
  | 'destroyed';

export type SaroniteRequestKind = 'request';
export type SaroniteResponseKind = 'response';
export type SaroniteEventKind = 'event';

export type SaroniteMessageKind =
  | SaroniteRequestKind
  | SaroniteResponseKind
  | SaroniteEventKind;

export interface SaroniteProtocolEnvelope {
  protocol: typeof SARONITE_PROTOCOL_NAME;
  version: SaroniteProtocolVersion;
  kind: SaroniteMessageKind;
  requestId: string;
  timestamp: number;
}

export interface SaroniteRequest<TPayload = unknown>
  extends SaroniteProtocolEnvelope {
  kind: SaroniteRequestKind;
  capability: string;
  method: string;
  payload: TPayload;
  permission?: string;
}

export interface SaroniteResponse<TPayload = unknown>
  extends SaroniteProtocolEnvelope {
  kind: SaroniteResponseKind;
  ok: boolean;
  payload?: TPayload;
  error?: SaroniteError;
}

export interface SaroniteEvent<TPayload = unknown>
  extends SaroniteProtocolEnvelope {
  kind: SaroniteEventKind;
  event: string;
  payload: TPayload;
}

export type SaroniteErrorCode =
  | 'UNSUPPORTED'
  | 'PERMISSION_DENIED'
  | 'INVALID_REQUEST'
  | 'NOT_AUTHENTICATED'
  | 'NOT_AUTHORIZED'
  | 'LIFECYCLE_BLOCKED'
  | 'NATIVE_FAILURE'
  | 'NETWORK_FAILURE'
  | 'TIMEOUT'
  | 'INTERNAL_ERROR'
  | 'PROTOCOL_MISMATCH';

export interface SaroniteError {
  code: SaroniteErrorCode;
  message: string;
  capability?: string;
  method?: string;
  retryable?: boolean;
  details?: Record<string, unknown>;
}

export interface SaroniteLifecycleEvent {
  lifecycle: SaroniteLifecycle;
}

export interface SaronitePermissionState {
  permission: string;
  granted: boolean;
}

export interface SaroniteProtocolHello {
  protocol: typeof SARONITE_PROTOCOL_NAME;
  version: SaroniteProtocolVersion;
  runtime: 'mini-app' | 'host' | 'devtools';
}

export function createRequestId(prefix = 'req'): string {
  const random =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}_${random}`;
}

export function createEnvelope(
  kind: SaroniteMessageKind,
  requestId = createRequestId(),
): SaroniteProtocolEnvelope {
  return {
    protocol: SARONITE_PROTOCOL_NAME,
    version: SARONITE_PROTOCOL_VERSION,
    kind,
    requestId,
    timestamp: Date.now(),
  };
}

export function isSaroniteProtocolMessage(
  value: unknown,
): value is SaroniteProtocolEnvelope {
  if (!value || typeof value !== 'object') return false;
  const message = value as Record<string, unknown>;
  return (
    message.protocol === SARONITE_PROTOCOL_NAME &&
    message.version === SARONITE_PROTOCOL_VERSION &&
    (message.kind === 'request' ||
      message.kind === 'response' ||
      message.kind === 'event') &&
    typeof message.requestId === 'string' &&
    typeof message.timestamp === 'number'
  );
}
