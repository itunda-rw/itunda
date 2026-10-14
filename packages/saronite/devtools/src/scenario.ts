import { createSaroniteMockHost, type MockHostState, type MockRequestOptions, type SaroniteMockHost } from './mock.ts';

export type SaroniteScenario = {
  /** Stable scenario ID used to correlate all calls in a reproducible run. */
  id: string;
  /** Explicitly allow or deny named permissions. Unlisted permissions remain denied. */
  permissions?: Record<string, boolean>;
  /** Capability names or capability.method keys to simulate as unavailable. */
  unsupported?: string[];
  /** Capability.method -> error message for deterministic failure injection. */
  failures?: Record<string, string>;
  lifecycle?: MockHostState['lifecycle'];
};

export type SaroniteScenarioHost = SaroniteMockHost & {
  scenario: Readonly<SaroniteScenario>;
};

const SCENARIO_ID_PATTERN = /^[a-z][a-z0-9-]{1,62}$/;

/**
 * Create a deterministic mock host for SDK demos and contract tests.
 * This only simulates the JS contract; it does not validate native platform behavior.
 */
export function createSaroniteScenarioHost(scenario: SaroniteScenario): SaroniteScenarioHost {
  if (!SCENARIO_ID_PATTERN.test(scenario.id)) {
    throw new Error('Scenario id must be 2–63 lowercase letters, digits, or hyphens and start with a letter');
  }

  const definition: SaroniteScenario = {
    ...scenario,
    permissions: { ...(scenario.permissions ?? {}) },
    unsupported: [...(scenario.unsupported ?? [])],
    failures: { ...(scenario.failures ?? {}) },
    lifecycle: scenario.lifecycle ?? 'visible',
  };
  const host = createSaroniteMockHost({
    permissions: definition.permissions,
    lifecycle: definition.lifecycle,
  });
  let sequence = 0;

  return {
    ...host,
    scenario: definition,
    request<T>(capability: string, method: string, handler: () => T, options: MockRequestOptions = {}) {
      sequence += 1;
      const key = `${capability}.${method}`;
      const correlationId = options.correlationId ?? `${definition.id}-${sequence}`;
      if (definition.unsupported?.includes(capability) || definition.unsupported?.includes(key)) {
        return host.request(capability, method, handler, { ...options, supported: false, correlationId });
      }
      const failure = definition.failures?.[key];
      if (failure !== undefined) {
        return host.request(capability, method, () => { throw new Error(failure); }, { ...options, correlationId });
      }
      return host.request(capability, method, handler, { ...options, correlationId });
    },
  };
}
