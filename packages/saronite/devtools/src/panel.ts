import type { MockCall, SaroniteMockHost } from './mock';

const PANEL_STYLES = `
[data-saronite-devtools] {
  --saronite-panel-brand: #8F89FF;
  --saronite-panel-border: rgba(143, 137, 255, .28);
  --saronite-panel-bg: rgba(255, 255, 255, .94);
  --saronite-panel-text: #191F28;
  --saronite-panel-muted: #6B7684;
  --saronite-panel-control: #F4F5F8;
  box-sizing: border-box;
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 2147483647;
  width: min(320px, calc(100vw - 32px));
  max-height: min(420px, calc(100vh - 32px));
  overflow: auto;
  padding: 16px;
  border: 1px solid var(--saronite-panel-border);
  border-radius: 20px;
  background: var(--saronite-panel-bg);
  color: var(--saronite-panel-text);
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
  box-shadow: 0 16px 48px rgba(25, 31, 40, .16);
  font: 13px/1.45 system-ui, sans-serif;
}
[data-saronite-devtools] * { box-sizing: border-box; }
[data-saronite-devtools] button {
  min-height: 36px;
  padding: 7px 12px;
  border: 0;
  border-radius: 999px;
  background: var(--saronite-panel-control);
  color: var(--saronite-panel-text);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}
[data-saronite-devtools] button:focus-visible {
  outline: 2px solid var(--saronite-panel-brand);
  outline-offset: 3px;
}
[data-saronite-devtools] ol { color: var(--saronite-panel-muted); }
[data-saronite-devtools] strong { color: var(--saronite-panel-text); }
[data-theme="dark"] [data-saronite-devtools],
[data-saronite-devtools][data-theme="dark"] {
  --saronite-panel-brand: #A6A2FF;
  --saronite-panel-border: rgba(166, 162, 255, .3);
  --saronite-panel-bg: rgba(37, 37, 45, .96);
  --saronite-panel-text: #F7F7FA;
  --saronite-panel-muted: #B7BAC6;
  --saronite-panel-control: #30303A;
  box-shadow: 0 16px 48px rgba(0, 0, 0, .32);
}
@media (prefers-color-scheme: dark) {
  html:not([data-theme="light"]):not([data-theme="dark"]) [data-saronite-devtools] {
    --saronite-panel-brand: #A6A2FF;
    --saronite-panel-border: rgba(166, 162, 255, .3);
    --saronite-panel-bg: rgba(37, 37, 45, .96);
    --saronite-panel-text: #F7F7FA;
    --saronite-panel-muted: #B7BAC6;
    --saronite-panel-control: #30303A;
    box-shadow: 0 16px 48px rgba(0, 0, 0, .32);
  }
}
`;

export function renderSaroniteDevPanel(host: SaroniteMockHost, mount: HTMLElement): () => void {
  const root = document.createElement('aside');
  root.setAttribute('data-saronite-devtools', '');
  const styles = document.createElement('style');
  styles.setAttribute('data-saronite-devtools-styles', '');
  styles.textContent = PANEL_STYLES;
  document.head.append(styles);

  const render = () => {
    const calls: MockCall[] = host.state.calls.slice(-20).reverse();
    root.replaceChildren();
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
  return () => {
    window.clearInterval(timer);
    root.remove();
    styles.remove();
  };
}
