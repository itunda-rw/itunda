// @toss/use-funnel@1.4.2 unconditionally imports `useRouter` from `next/router.js`
// at its module top level, even though this app doesn't use Next.js and never
// passes any of use-funnel's URL-sync options (the package's own source marks this
// whole API "@deprecated -- moved to the @use-funnel library"). Pulling in all of
// Next.js just to satisfy that one unused import would be a heavyweight, wrong fix
// for a plain Vite SPA -- this stub is aliased in vite.config.ts instead. Route
// state here is plain React state via useFunnel's returned setter, never touches
// this router, so a minimal no-op shape is enough.
export function useRouter() {
  return {
    query: {},
    pathname: '',
    push: () => Promise.resolve(true),
    replace: () => Promise.resolve(true),
  };
}

// @toss/use-query-param's waitForRouterReady.mjs does `import Router from
// 'next/router.js'; Router.ready(resolve)` -- the real Next.js Router.ready(cb)
// calls back once routing is ready (immediately if already ready). There's no
// real router here, so calling back immediately is the correct no-op behavior.
export default {
  ready(callback: () => void) {
    callback();
  },
};
