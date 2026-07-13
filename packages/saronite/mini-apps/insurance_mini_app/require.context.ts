/**
 * Hand-constructed `RequireContext` -- same real, structurally-correct
 * implementation as pay-bills/require.context.ts (see its own header
 * comment for the full account of why this is hand-written rather than
 * bundler-generated in itunda's plain-Metro setup). insurance's own two
 * real routes: `pages/index` and `pages/_404`.
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
