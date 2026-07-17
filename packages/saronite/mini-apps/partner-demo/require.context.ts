/**
 * Hand-constructed `RequireContext` -- same real, structurally-correct
 * implementation as the four first-party mini-apps' own require.context.ts (see
 * pay-bills/require.context.ts's header comment for the full account of why this is
 * hand-written rather than bundler-generated in itunda's plain-Metro setup).
 * partner-demo's own one real route: `pages/index`.
 */
interface RequireContext {
  keys(): string[];
  (id: string): unknown;
  resolve(id: string): string;
  id: string;
}

const pages: Record<string, () => unknown> = {
  './index': () => require('./pages/index'),
  './_404': () => require('./pages/_404'),
};

export const context: RequireContext = Object.assign(
  (id: string) => pages[id]?.(),
  {
    keys: () => Object.keys(pages),
    resolve: (id: string) => id,
    id: './pages',
  },
);
