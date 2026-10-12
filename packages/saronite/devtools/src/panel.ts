import type { MockCall, SaroniteMockHost } from './mock';

const PERMISSIONS = ['identity:read', 'location:read'] as const;

export function renderSaroniteDevPanel(host: SaroniteMockHost, mount: HTMLElement): () => void {
  const root = document.createElement('aside');
  root.setAttribute('data-saronite-devtools', '');
  root.setAttribute('aria-label', 'Saronite developer tools');
  root.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;width:min(360px,calc(100vw - 32px));max-height:min(560px,calc(100vh - 32px));overflow:auto;padding:16px;border:1px solid rgba(116,114,244,.22);border-radius:20px;background:rgba(255,255,255,.96);backdrop-filter:blur(18px);box-shadow:0 16px 48px rgba(25,31,40,.16);font:13px/1.45 system-ui,sans-serif;color:#191F28';
  const buttonStyle = 'border:1px solid rgba(116,114,244,.28);border-radius:10px;padding:6px 9px;background:#fff;color:#191F28;cursor:pointer;font:inherit';

  const render = () => {
    const calls: MockCall[] = host.state.calls.slice(-20).reverse();
    root.replaceChildren();

    const heading = document.createElement('strong');
    heading.textContent = 'Saronite DevTools';
    heading.style.fontSize = '14px';
    root.append(heading);

    const lifecycleRow = document.createElement('div');
    lifecycleRow.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px;margin:10px 0';
    const lifecycle = document.createElement('span');
    lifecycle.textContent = 'Lifecycle: ' + host.state.lifecycle;
    lifecycle.setAttribute('aria-live', 'polite');
    const lifecycleButton = document.createElement('button');
    lifecycleButton.type = 'button';
    lifecycleButton.style.cssText = buttonStyle;
    lifecycleButton.textContent = host.state.lifecycle === 'visible' ? 'Hide app' : 'Show app';
    lifecycleButton.onclick = () => host.setLifecycle(host.state.lifecycle === 'visible' ? 'hidden' : 'visible');
    lifecycleRow.append(lifecycle, lifecycleButton);
    root.append(lifecycleRow);

    const permissionHeading = document.createElement('div');
    permissionHeading.textContent = 'Mock permissions';
    permissionHeading.style.cssText = 'font-weight:600;margin:8px 0 4px';
    root.append(permissionHeading);

    for (const permission of PERMISSIONS) {
      const row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px;margin:4px 0';
      const label = document.createElement('span');
      label.textContent = permission;
      const granted = host.state.permissions[permission] === true;
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.style.cssText = buttonStyle;
      toggle.textContent = granted ? 'Granted' : 'Denied';
      toggle.setAttribute('aria-pressed', String(granted));
      toggle.setAttribute('aria-label', permission + ': ' + (granted ? 'granted; revoke' : 'denied; grant'));
      toggle.onclick = () => host.setPermission(permission, !granted);
      row.append(label, toggle);
      root.append(row);
    }

    const callsHeader = document.createElement('div');
    callsHeader.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px;margin:12px 0 6px';
    const callsTitle = document.createElement('strong');
    callsTitle.textContent = 'Recent calls (' + host.state.calls.length + ')';
    const clear = document.createElement('button');
    clear.type = 'button';
    clear.textContent = 'Clear';
    clear.style.cssText = buttonStyle;
    clear.onclick = () => host.clearCalls();
    callsHeader.append(callsTitle, clear);
    root.append(callsHeader);

    const list = document.createElement('ol');
    list.style.cssText = 'padding-left:20px;margin:0';
    for (const call of calls) {
      const item = document.createElement('li');
      item.style.cssText = 'margin:7px 0;overflow-wrap:anywhere';
      const summary = document.createElement('div');
      summary.textContent = call.capability + '.' + call.method + ' — ' + call.status;
      const detail = document.createElement('div');
      detail.style.cssText = 'color:#5E6472;font-size:12px';
      detail.textContent = call.detail ? call.detail : new Date(call.timestamp).toLocaleTimeString();
      item.append(summary, detail);
      list.append(item);
    }
    if (calls.length === 0) {
      const empty = document.createElement('p');
      empty.textContent = 'No capability calls yet.';
      empty.style.color = '#5E6472';
      root.append(empty);
    } else {
      root.append(list);
    }
  };

  render();
  mount.append(root);
  const timer = window.setInterval(render, 500);
  return () => { window.clearInterval(timer); root.remove(); };
}
