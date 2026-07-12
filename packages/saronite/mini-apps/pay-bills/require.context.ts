/**
 * Hand-constructed `RequireContext` (2026-07-12, granite-adoption stage 6).
 *
 * Real granite apps get this for free from `require.context('./pages')`, a
 * bundler-time transform granite's own ESBuild pipeline (`granite dev`/
 * `granite build`) provides. Itunda's mini-app host runs on plain
 * `react-native start` (Metro) instead -- shared with `wallet-balance`/
 * `reward-tasks`, which are deliberately not migrated this pass -- so that
 * transform isn't available here. The shape below matches
 * `@granite-js/react-native`'s real `RequireContext` interface
 * (`packages/saronite/node_modules/@granite-js/react-native/src/router/types/RequireContext.ts`,
 * read directly) structurally -- it isn't part of that package's public
 * export surface, so the type is declared locally rather than imported, but
 * TypeScript's structural typing accepts it at the `Granite.registerApp`
 * call site all the same. Simple enough to satisfy correctly by hand for a
 * single-page mini-app: a callable module loader plus `keys()`/`resolve()`/
 * `id`. This is a real, correct implementation of that contract, not a stub
 * -- `pages/index` is genuinely the only route `pay-bills` has.
 */
interface RequireContext {
  keys(): string[];
  (id: string): unknown;
  resolve(id: string): string;
  id: string;
}

const pages: Record<string, () => unknown> = {
  './index': () => require('./pages/index'),
};

export const context: RequireContext = Object.assign(
  (id: string) => pages[id]?.(),
  {
    keys: () => Object.keys(pages),
    resolve: (id: string) => id,
    id: './pages',
  },
);
