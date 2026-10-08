import { createSaroniteDebugConsole, isSaroniteDebugEnabled } from './index';

export function autoMountSaroniteDebugConsole(): void {
  if (!isSaroniteDebugEnabled(window)) return;
  const mount = () => createSaroniteDebugConsole().mount();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount, { once: true });
  else mount();
}
autoMountSaroniteDebugConsole();