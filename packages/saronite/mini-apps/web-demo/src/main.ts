import type { SaroniteMockHost } from '@itunda/saronite-devtools';
import './style.css';
import canonicalItundaSymbolUrl from '../../../../packages/brand-assets/canonical/itunda-brand/symbol/itunda-symbol.svg';

type DemoState = {
  result: string;
};

let host: SaroniteMockHost | undefined;

const state: DemoState = {
  result: 'Ready. Run a capability call.',
};

const app = document.querySelector<HTMLDivElement>('#app')!;

app.innerHTML = `
  <section class="shell">
    <img class="brand-mark" src="${canonicalItundaSymbolUrl}" alt="" aria-hidden="true" />
    <p class="eyebrow">Apps in Itunda</p>
    <h1>Saronite Web Mini-App</h1>
    <p class="lede">
      Browser development uses the Saronite mock host. The production runtime stays
      on the native Saronite bridge.
    </p>

    <div class="card">
      <div>
        <strong>Identity</strong>
        <span>permission: identity:read</span>
      </div>
      <button id="identity">Read identity</button>
    </div>

    <div class="card">
      <div>
        <strong>Location</strong>
        <span>permission: location:read</span>
      </div>
      <button id="location">Read location</button>
    </div>

    <div class="result" aria-live="polite">
      <span>Result</span>
      <output id="result"></output>
    </div>
  </section>
`;

const result = document.querySelector<HTMLOutputElement>('#result')!;

function setResult(value: string) {
  state.result = value;
  result.textContent = value;
}

document.querySelector<HTMLButtonElement>('#identity')!.onclick = () => {
  const identity = host?.request(
    'identity',
    'getCurrentIdentity',
    () => ({ id: 'demo-user', verified: true }),
    { permission: 'identity:read' },
  );
  setResult(identity ? JSON.stringify(identity) : 'Identity permission denied.');
};

document.querySelector<HTMLButtonElement>('#location')!.onclick = () => {
  const location = host?.request(
    'location',
    'getCurrentLocation',
    () => ({ latitude: -1.9441, longitude: 30.0619 }),
    { permission: 'location:read' },
  );
  setResult(location ? JSON.stringify(location) : 'Location permission denied.');
};

if (import.meta.env.DEV) {
  void import('@itunda/saronite-devtools').then(({ createSaroniteMockHost, renderSaroniteDevPanel }) => {
    host = createSaroniteMockHost({ permissions: { 'identity:read': true } });
    renderSaroniteDevPanel(host, document.body);
  });
} else {
  setResult('Production host runtime is required; browser mock is development-only.');
}
