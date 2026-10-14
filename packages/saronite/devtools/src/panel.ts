import type { MockCall, SaroniteMockHost } from './mock';

const BRAND = '#8F89FF';
const TEXT = '#191F28';
const MUTED = '#596273';
const BORDER = '#E5E7EB';

function button(label: string, onClick: () => void): HTMLButtonElement {
  const element = document.createElement('button');
  element.type = 'button';
  element.textContent = label;
  element.style.cssText = `border:1px solid ${BORDER};border-radius:10px;padding:7px 10px;background:#fff;color:${TEXT};font:inherit;cursor:pointer;min-height:34px`;
  element.addEventListener('click', onClick);
  return element;
}

function sectionTitle(label: string): HTMLHeadingElement {
  const element = document.createElement('h3');
  element.textContent = label;
  element.style.cssText = 'font-size:12px;letter-spacing:.04em;text-transform:uppercase;margin:16px 0 8px;color:' + MUTED;
  return element;
}

/** Mount the development-only Saronite inspector. Unsubscribe and remove it on teardown. */
export function renderSaroniteDevPanel(host: SaroniteMockHost, mount: HTMLElement): () => void {
  const root = document.createElement('aside');
  root.setAttribute('data-saronite-devtools', '');
  root.setAttribute('aria-label', 'Saronite development tools');
  root.style.cssText = `position:fixed;right:16px;bottom:16px;z-index:2147483647;width:min(380px,calc(100vw - 32px));max-height:min(76vh,620px);overflow:auto;padding:16px;border:1px solid ${BORDER};border-radius:20px;background:#fff;box-shadow:0 12px 40px rgba(25,31,40,.16);font:13px/1.45 system-ui,sans-serif;color:${TEXT}`;

  const render = () => {
    const calls: MockCall[] = host.state.calls.slice(-30).reverse();
    root.replaceChildren();

    const heading = document.createElement('div');
    heading.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:12px';
    const title = document.createElement('strong');
    title.textContent = 'Saronite DevTools';
    title.style.fontSize = '15px';
    const environment = document.createElement('span');
    environment.textContent = 'MOCK HOST';
    environment.style.cssText = `font-size:10px;font-weight:700;letter-spacing:.06em;color:${TEXT};background:#F1F0FF;border-radius:6px;padding:4px 6px`;
    heading.append(title, environment);
    root.append(heading);

    const notice = document.createElement('p');
    notice.textContent = 'Development simulation only — not proof of native runtime behavior.';
    notice.style.cssText = `margin:8px 0;color:${MUTED};font-size:12px`;
    root.append(notice);

    root.append(sectionTitle('Lifecycle'));
    const lifecycleRow = document.createElement('div');
    lifecycleRow.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px';
    const lifecycleValue = document.createElement('span');
    lifecycleValue.textContent = host.state.lifecycle === 'visible' ? 'Visible' : 'Hidden';
    lifecycleValue.setAttribute('aria-live', 'polite');
    const lifecycleButton = button(host.state.lifecycle === 'visible' ? 'Simulate hide' : 'Simulate show', () => {
      host.setLifecycle(host.state.lifecycle === 'visible' ? 'hidden' : 'visible');
    });
    lifecycleRow.append(lifecycleValue, lifecycleButton);
    root.append(lifecycleRow);

    root.append(sectionTitle('Permissions'));
    const permissions = new Set([
      'identity:read',
      'location:read',
      'camera:use',
      ...Object.keys(host.state.permissions),
    ]);
    for (const permission of [...permissions].sort()) {
      const row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px;padding:5px 0';
      const name = document.createElement('code');
      name.textContent = permission;
      name.style.cssText = 'font-size:12px;overflow-wrap:anywhere';
      const granted = host.state.permissions[permission] === true;
      const toggle = button(granted ? 'Granted' : 'Denied', () => host.setPermission(permission, !granted));
      toggle.setAttribute('aria-label', `${permission}: ${granted ? 'granted' : 'denied'}. Toggle permission`);
      if (granted) {
        toggle.style.borderColor = BRAND;
        toggle.style.background = '#F1F0FF';
      }
      row.append(name, toggle);
      root.append(row);
    }

    const callsHeading = document.createElement('div');
    callsHeading.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:16px';
    const callsTitle = document.createElement('h3');
    callsTitle.textContent = `Request log (${host.state.calls.length})`;
    callsTitle.style.cssText = 'font-size:12px;letter-spacing:.04em;text-transform:uppercase;margin:0;color:' + MUTED;
    callsHeading.append(callsTitle, button('Clear log', () => host.clearCalls()));
    root.append(callsHeading);

    if (calls.length === 0) {
      const empty = document.createElement('p');
      empty.textContent = 'No mock calls yet. Trigger a capability in the demo to inspect its result.';
      empty.style.cssText = `color:${MUTED};font-size:12px;margin:10px 0`;
      root.append(empty);
    }

    const list = document.createElement('ol');
    list.style.cssText = 'padding-left:20px;margin:10px 0 0';
    for (const call of calls) {
      const item = document.createElement('li');
      item.style.cssText = 'padding:7px 0;border-bottom:1px solid #F1F2F4;overflow-wrap:anywhere';
      const label = document.createElement('div');
      label.textContent = `${call.capability}.${call.method}`;
      label.style.fontWeight = '600';
      const status = document.createElement('span');
      status.textContent = call.status.toUpperCase();
      status.style.cssText = `display:inline-block;margin:4px 6px 0 0;padding:2px 5px;border-radius:5px;background:${call.status === 'ok' ? '#F1F0FF' : '#F3F4F6'};font-size:10px;font-weight:700`;
      const meta = document.createElement('div');
      meta.textContent = `${call.requestId}${call.correlationId ? ` · trace ${call.correlationId}` : ''} · ${new Date(call.timestamp).toLocaleTimeString()}`;
      meta.style.cssText = `font-size:10px;color:${MUTED};overflow-wrap:anywhere`;
      item.append(label, status, meta);
      if (call.detail) {
        const detail = document.createElement('div');
        detail.textContent = call.detail;
        detail.style.cssText = `margin-top:4px;color:${MUTED};font-size:11px;overflow-wrap:anywhere`;
        item.append(detail);
      }
      list.append(item);
    }
    root.append(list);
  };

  const unsubscribe = host.subscribe(render);
  render();
  mount.append(root);
  return () => {
    unsubscribe();
    root.remove();
  };
}
