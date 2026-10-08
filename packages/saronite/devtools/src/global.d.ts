import type { SaroniteDevTools } from './index';

declare global {
  interface Window {
    __saronite?: SaroniteDevTools;
  }
}

export {};
