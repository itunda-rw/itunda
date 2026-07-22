// Enforces the silo boundaries documented in docs/MULTI_AGENT_ISOLATION.md so a
// boundary violation fails CI instead of relying on convention. Run: yarn depcruise

const MICRO_FRONTENDS = [
  'bank-mfe',
  'kyc-mfe',
  'merchant-mfe',
  'ops-mfe',
  'pay-checkout',
  'host-app',
];

const SHARED_PACKAGES = ['shared-utils', 'design-tokens', 'itunda-pay-widget'];

const noCrossMfeImportRules = MICRO_FRONTENDS.map((mfe) => ({
  name: `no-cross-mfe-import-${mfe}`,
  comment:
    `${mfe} must not import another micro-frontend's source directly -- go ` +
    'through packages/shared-utils or a real API call instead.',
  severity: 'error',
  from: { path: `^services/micro-frontends/${mfe}/` },
  to: { path: `^services/micro-frontends/(?!${mfe}/)[^/]+/` },
}));

const noDeepPackageInternalImportRules = SHARED_PACKAGES.map((pkg) => ({
  name: `no-deep-internal-import-${pkg}`,
  comment:
    `Only packages/${pkg} itself may import its own src/internal/** -- ` +
    "everything else should use the package's public entry point.",
  severity: 'error',
  from: { pathNot: `^packages/${pkg}/` },
  to: { path: `^packages/${pkg}/src/internal/` },
}));

module.exports = {
  forbidden: [...noCrossMfeImportRules, ...noDeepPackageInternalImportRules],
  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: {
      path: '(^|/)(node_modules|dist|build|coverage|\\.turbo)($|/)',
    },
    tsPreCompilationDeps: true,
    enhancedResolveOptions: {
      // dependency-cruiser's default resolver extension list doesn't include
      // .ts/.tsx, so extensionless TS imports (the norm in this codebase) would
      // silently fail to resolve and skip rule checks without this.
      extensions: ['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs', '.json'],
    },
  },
};
