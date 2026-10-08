export interface SaroniteDebugHost {
  attach(transport: { send(message: unknown): void }): void;
  detach(): void;
  inspect(): { attached: boolean };
}

export function createSaroniteDebugger(): SaroniteDebugHost {
  let attached = false;
  return {
    attach(transport) { if (!transport || typeof transport.send !== 'function') throw new TypeError('invalid debug transport'); attached = true; },
    detach() { attached = false; },
    inspect() { return { attached }; },
  };
}