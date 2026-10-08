import type { SaroniteDebugMessage, SaroniteDebugTransport } from '@itunda/saronite-debug-protocol';

export interface DebugConsoleOptions { target?: Window & typeof globalThis; appId?: string; sessionId?: string; transport?: SaroniteDebugTransport; }
export interface SaroniteDebugConsole { readonly sessionId: string; readonly transport: SaroniteDebugTransport; mount(): void; unmount(): void; close(): void; }

type DebugWindow = Window & typeof globalThis & {
  __SARONITE_DEBUG_BUILD__?: boolean;
  __saronite?: { state: Readonly<Record<string, unknown>>; subscribe?: (listener: (state: Readonly<Record<string, unknown>>) => void) => () => void };
};

export function isSaroniteDebugEnabled(target: DebugWindow = window): boolean {
  if (target.__SARONITE_DEBUG_BUILD__ !== true) return false;
  const params = new URLSearchParams(target.location?.search ?? '');
  return params.has('debug') || params.has('relay');
}

function createSessionId(): string { return `saronite-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`; }
function createLocalTransport(target: DebugWindow): SaroniteDebugTransport {
  return { send(message) { target.dispatchEvent(new CustomEvent('saronite:debug', { detail: message })); } };
}
function formatPayload(payload: unknown): string { if (payload === undefined) return ''; try { return JSON.stringify(payload); } catch { return '[unserializable]'; } }

export function createSaroniteDebugConsole(options: DebugConsoleOptions = {}): SaroniteDebugConsole {
  const target = (options.target ?? window) as DebugWindow;
  const sessionId = options.sessionId ?? createSessionId();
  const transport = options.transport ?? createLocalTransport(target);
  let root: HTMLDivElement | undefined;
  let unsubscribe: (() => void) | undefined;
  let onDebugEvent: ((event: Event) => void) | undefined;

  const mount = () => {
    if (!isSaroniteDebugEnabled(target) || root) return;
    root = target.document.createElement('div');
    root.dataset.saroniteDebugConsole = 'true';
    root.style.cssText = 'position:fixed;right:12px;bottom:12px;z-index:2147483647;width:min(420px,calc(100vw - 24px));max-height:45vh;overflow:auto;font:12px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;background:rgba(20,20,28,.94);color:#fff;border-radius:16px;box-shadow:0 12px 40px rgba(0,0,0,.28);padding:10px;backdrop-filter:blur(14px)';
    const header = target.document.createElement('div');
    header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;font-weight:700';
    header.textContent = 'Saronite Debug';
    const close = target.document.createElement('button');
    close.textContent = '×'; close.setAttribute('aria-label','Close debug console');
    close.style.cssText = 'border:0;background:transparent;color:inherit;font-size:20px;cursor:pointer';
    close.onclick = () => unmount(); header.appendChild(close); root.appendChild(header);
    const body = target.document.createElement('pre');
    body.style.cssText = 'white-space:pre-wrap;margin:0;max-height:34vh;overflow:auto;opacity:.9'; root.appendChild(body);
    const write = (line: string) => { body.textContent = (body.textContent ? body.textContent + '\\n' : '') + line; body.scrollTop = body.scrollHeight; };
    emit({ version: 1, type: 'hello', sessionId, platform: 'web', appId: options.appId }); write(`session ${sessionId}`);
    const host = target.__saronite;
    if (host?.subscribe) unsubscribe = host.subscribe(state => emit({ version: 1, type: 'state', state: state as Record<string, unknown> }));
    onDebugEvent = event => {
      const detail = (event as CustomEvent<SaroniteDebugMessage>).detail;
      if (!detail || detail.version !== 1 || detail.type !== 'log') return;
      write(`[${detail.direction}] ${detail.capability}.${detail.method}${detail.error ? ` — ${detail.error}` : ''}${detail.durationMs !== undefined ? ` (${detail.durationMs}ms)` : ''}${detail.payload !== undefined ? ` ${formatPayload(detail.payload)}` : ''}`);
    };
    target.addEventListener('saronite:debug', onDebugEvent);
  };
  const unmount = () => { unsubscribe?.(); unsubscribe = undefined; if (onDebugEvent) target.removeEventListener('saronite:debug', onDebugEvent); onDebugEvent = undefined; root?.remove(); root = undefined; };
  const emit = (message: SaroniteDebugMessage) => transport.send(message);
  return { sessionId, transport, mount, unmount, close: unmount };
}