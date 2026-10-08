import type { SaroniteDevTools, SaroniteMockState } from './index';

const escape = (value: unknown) =>
  String(value).replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]!));

export function mountSaroniteDevTools(host: SaroniteDevTools = window.__saronite!): HTMLElement {
  if (!host) throw new Error('Install the Saronite browser mock before mounting DevTools');
  const existing = document.querySelector('[data-saronite-devtools]');
  if (existing) return existing as HTMLElement;

  const root = document.createElement('aside');
  root.dataset.saroniteDevtools = 'true';
  root.style.cssText = 'position:fixed;right:16px;bottom:16px;width:360px;max-height:70vh;z-index:2147483647;background:#fff;color:#17171c;border:1px solid #e8e8ee;border-radius:18px;box-shadow:0 16px 48px rgba(0,0,0,.18);font:13px/1.45 system-ui,sans-serif;overflow:hidden;';
  root.innerHTML = `<div style="display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #eee"><strong>Saronite DevTools</strong><button data-close>×</button></div><div data-body style="padding:12px;overflow:auto;max-height:calc(70vh - 48px)"></div>`;

  const render = (state: Readonly<SaroniteMockState>) => {
    const body = root.querySelector('[data-body]')!;
    body.innerHTML = `
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
        <label>Platform<select data-platform><option>web</option><option>android</option><option>ios</option></select></label>
        <label>Network<select data-network><option>online</option><option>offline</option></select></label>
        <label>Locale<input data-locale value="${escape(state.locale)}"></label>
        <label>Latency<input data-latency type="number" min="0" max="10000" value="${state.latencyMs}"></label>
      </div>
      <h4>Permissions</h4>
      <div>${Object.entries(state.permissions).map(([name,value]) => `<button data-permission="${name}" style="margin:3px;padding:5px 8px;border-radius:9px;border:1px solid #ddd;background:#fafafa">${name}: ${value}</button>`).join('')}</div>
      <h4>Capabilities</h4>
      <div style="display:flex;gap:6px;flex-wrap:wrap">
        <button data-capability="auth">Auth</button>
        <button data-capability="navigation">Navigation</button>
        <button data-capability="permissions">Permissions</button>
        <button data-capability="storage">Storage</button>
        <button data-capability="payment">Payment</button>
        <button data-capability="analytics">Analytics</button>
      </div>
      <h4>Runtime</h4>
      <div style="display:flex;gap:6px"><button data-auth>${state.authenticated?'Sign out':'Sign in'}</button><button data-reset>Reset</button><button data-clear>Clear logs</button></div>
      <h4>Logs (${state.logs.length})</h4>
      <pre style="white-space:pre-wrap;word-break:break-word;background:#f7f7fa;padding:8px;border-radius:10px">${escape(state.logs.slice(-20).map(log => `[${new Date(log.timestamp).toLocaleTimeString()}] ${log.direction} ${log.capability}.${log.method}${log.error ? ` — ${log.error}` : ''}`).join('\\n') || 'No runtime calls yet.')}</pre>
    `;
    (body.querySelector('[data-platform]') as HTMLSelectElement).value=state.platform;
    (body.querySelector('[data-network]') as HTMLSelectElement).value=state.network;
    body.querySelector('[data-platform]')!.addEventListener('change',e=>host.update({platform:(e.target as HTMLSelectElement).value as SaroniteMockState['platform']}));
    body.querySelector('[data-network]')!.addEventListener('change',e=>host.update({network:(e.target as HTMLSelectElement).value as SaroniteMockState['network']}));
    body.querySelector('[data-locale]')!.addEventListener('change',e=>host.update({locale:(e.target as HTMLInputElement).value}));
    body.querySelector('[data-latency]')!.addEventListener('change',e=>host.setLatency(Number((e.target as HTMLInputElement).value)));
    body.querySelectorAll('[data-capability]').forEach(button => button.addEventListener('click', async () => {
      const capability=(button as HTMLElement).dataset.capability!;
      await host.callCapability({ capability, method: 'inspect', payload: { source: 'devtools' } });
    }));
    body.querySelector('[data-auth]')!.addEventListener('click',()=>host.update({authenticated:!state.authenticated}));
    body.querySelector('[data-reset]')!.addEventListener('click',()=>host.reset());
    body.querySelector('[data-clear]')!.addEventListener('click',()=>host.clearLogs());
    body.querySelectorAll('[data-permission]').forEach(button=>button.addEventListener('click',()=> {
      const permission=(button as HTMLElement).dataset.permission as keyof SaroniteMockState['permissions'];
      const next=state.permissions[permission]==='granted'?'denied':state.permissions[permission]==='denied'?'prompt':'granted';
      host.setPermission(permission,next);
    }));
  };
  root.querySelector('[data-close]')!.addEventListener('click',()=>{root.remove();});
  document.body.appendChild(root);
  render(host.state);
  host.subscribe(render);
  return root;
}

export function autoMountSaroniteDevTools(): HTMLElement | undefined {
  if (typeof window === 'undefined' || !window.__saronite) return undefined;
  return mountSaroniteDevTools();
}
