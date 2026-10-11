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
  width: min(340px, calc(100vw - 32px));
  max-height: min(520px, calc(100vh - 32px));
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
[data-saronite-devtools] button[aria-pressed="true"] {
  background: var(--saronite-panel-brand);
  color: #fff;
}
[data-saronite-devtools] button:focus-visible {
  outline: 2px solid var(--saronite-panel-brand);
  outline-offset: 3px;
}
[data-saronite-devtools] .saronite-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 12px;
}
[data-saronite-devtools] .saronite-section {
  margin: 12px 0 4px;
  font-weight: 700;
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
  root.setAttribute('aria-label', 'Saronite developer tools');
  const styles = document.createElement('style');
  styles.setAttribute('data-saronite-devtools-styles', '');
  styles.textContent = PANEL_STYLES;
  document.head.append(styles);

  let lastSnapshot = '';
  const render = () => {
    const calls: MockCall[] = host.state.calls.slice(-20).reverse();
    const snapshot = JSON.stringify({
      calls,
      permissions: host.state.permissions,
      lifecycle: host.state.lifecycle,
    });
    // Poll for SDK calls without replacing focused controls every 500ms.
    if (snapshot === lastSnapshot) return;
    lastSnapshot = snapshot;

    root.replaceChildren();
    const title = document.createElement('strong');
    title.textContent = 'Saronite DevTools';
    root.append(title);

    const lifecycleLabel = document.createElement('div');
    lifecycleLabel.className = 'saronite-section';
    lifecycleLabel.textContent = 'Lifecycle';
    root.append(lifecycleLabel);
    const lifecycleControls = document.createElement('div');
    lifecycleControls.className = 'saronite-controls';
    const lifecycleButton = document.createElement('button');
    lifecycleButton.type = 'button';
    lifecycleButton.textContent = host.state.lifecycle === 'visible' ? 'Visible' : 'Set visible';
    lifecycleButton.setAttribute('aria-pressed', String(host.state.lifecycle === 'visible'));
    lifecycleButton.onclick = () => {
      host.setLifecycle('visible');
      render();
    };
    lifecycleControls.append(lifecycleButton);
    const hiddenButton = document.createElement('button');
    hiddenButton.type = 'button';
    hiddenButton.textContent = 'Hidden';
    hiddenButton.setAttribute('aria-pressed', String(host.state.lifecycle === 'hidden'));
    hiddenButton.onclick = () => {
      host.setLifecycle('hidden');
      render();
    };
    lifecycleControls.append(hiddenButton);
    root.append(lifecycleControls);

    const permissionsLabel = document.createElement('div');
    permissionsLabel.className = 'saronite-section';
    permissionsLabel.textContent = 'Mock permissions';
    root.append(permissionsLabel);
    const permissionControls = document.createElement('div');
    permissionControls.className = 'saronite-controls';
    for (const permission of ['identity:read', 'location:read']) {
      const granted = host.state.permissions[permission] === true;
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = permission + (granted ? ' · granted' : ' · denied');
      button.setAttribute('aria-pressed', String(granted));
      button.setAttribute('aria-label', permission + (granted ? ', granted. Click to revoke.' : ', denied. Click to grant.'));
      button.onclick = () => {
        host.setPermission(permission, !granted);
        render();
      };
      permissionControls.append(button);
    }
    root.append(permissionControls);

    const clear = document.createElement('button');
    clear.type = 'button';
    clear.textContent = 'Clear call log';
    clear.onclick = () => { host.clearCalls(); render(); };
    root.append(clear);

    const listLabel = document.createElement('div');
    listLabel.className = 'saronite-section';
    listLabel.textContent = 'Recent SDK calls';
    root.append(listLabel);
    const list = document.createElement('ol');
    list.style.paddingLeft = '20px';
    for (const call of calls) {
      const item = document.createElement('li');
      item.textContent = call.capability + '.' + call.method + ' — ' + call.status;
      if (call.detail) item.title = call.detail;
      list.append(item);
    }
    if (calls.length === 0) {
      const empty = document.createElement('li');
      empty.textContent = 'No calls yet';
      list.append(empty);
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
