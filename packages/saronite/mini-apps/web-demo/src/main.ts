import { createSaroniteMockHost, renderSaroniteDevPanel } from '@itunda/saronite-devtools';
import './style.css';

type DemoState = {
  result: string;
};

const host = createSaroniteMockHost({
  permissions: {
    'identity:read': true,
  },
});

const state: DemoState = {
  result: 'Ready. Run a capability call.',
};

const app = document.querySelector<HTMLDivElement>('#app')!;

app.innerHTML = `
  <section class="shell">
    <div class="brand-mark" aria-hidden="true">◆</div>
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
  const identity = host.request(
    'identity',
    'getCurrentIdentity',
    () => ({ id: 'demo-user', verified: true }),
    { permission: 'identity:read' },
  );
  setResult(identity ? JSON.stringify(identity) : 'Identity permission denied.');
};

document.querySelector<HTMLButtonElement>('#location')!.onclick = () => {
  const location = host.request(
    'location',
    'getCurrentLocation',
    () => ({ latitude: -1.9441, longitude: 30.0619 }),
    { permission: 'location:read' },
  );
  setResult(location ? JSON.stringify(location) : 'Location permission denied.');
};

if (import.meta.env.DEV) {
  renderSaroniteDevPanel(host, document.body);
}
