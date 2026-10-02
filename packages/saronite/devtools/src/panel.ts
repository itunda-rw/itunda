import type { MockCall, SaroniteMockHost } from './mock';

export function renderSaroniteDevPanel(host: SaroniteMockHost, mount: HTMLElement): () => void {
  const root = document.createElement('aside');
  root.setAttribute('data-saronite-devtools', '');
  root.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;width:320px;max-height:420px;overflow:auto;padding:16px;border:1px solid rgba(116,114,244,.22);border-radius:20px;background:rgba(255,255,255,.94);backdrop-filter:blur(18px);box-shadow:0 16px 48px rgba(25,31,40,.16);font:13px/1.45 system-ui,sans-serif;color:#191F28';
  const render = () => {
    const calls: MockCall[] = host.state.calls.slice(-20).reverse();
    root.innerHTML = '';
    const title = document.createElement('strong');
    title.textContent = 'Saronite DevTools';
    root.append(title);
    const lifecycle = document.createElement('div');
    lifecycle.textContent = 'Lifecycle: ' + host.state.lifecycle;
    lifecycle.style.margin = '6px 0 10px';
    root.append(lifecycle);
    const clear = document.createElement('button');
    clear.textContent = 'Clear';
    clear.onclick = () => { host.clearCalls(); render(); };
    root.append(clear);
    const list = document.createElement('ol');
    list.style.paddingLeft = '20px';
    for (const call of calls) {
      const item = document.createElement('li');
      item.textContent = call.capability + '.' + call.method + ' — ' + call.status;
      if (call.detail) item.title = call.detail;
      list.append(item);
    }
    root.append(list);
  };
  render();
  mount.append(root);
  const timer = window.setInterval(render, 500);
  return () => { window.clearInterval(timer); root.remove(); };
}
