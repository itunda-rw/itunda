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
const PACKAGE_DIRS = [
  'packages/shared-utils/',
  'packages/design-tokens/',
  'packages/design-system-web/',
  'packages/itunda-pay-widget/',
];

const noCrossMfeImportRules = MICRO_FRONTENDS.map((mfe) => ({
  name: `no-cross-mfe-import-${mfe}`,
  comment:
    `${mfe} must not import another micro-frontend's source directly -- go ` +
    'through packages/shared-utils or a real API call instead.',
  severity: 'error',
  from: { path: `^services/micro-frontends/${mfe}/` },
  to: { path: `^services/micro-frontends/(?!${mfe}/)[^/]+/` },
}));

// Verified 2026-09-05 (deliberately created a scratch packages/shared-utils/src/
// internal/*.ts + an outside importer, confirmed depcruise catches it, reverted) that
// the mechanism itself is sound -- but as of the same date, none of SHARED_PACKAGES
// actually HAS a src/internal/ directory (shared-utils and itunda-pay-widget are each
// a single flat index.ts; design-tokens is CSS, not TS). That means every rule below
// currently protects nothing real -- "0 violations" here is not the same claim as the
// cross-mfe rules above, which DO have real files to violate. Left in place as cheap,
// correct-when-it-matters insurance for if one of these packages ever grows an
// internal/ split, not because it's catching anything today. Don't read a clean
// depcruise run as proof this specific rule set is doing real work right now.
const noPackageToMfeSourceRules = PACKAGE_DIRS.map((pkgDir) => ({
  name: `no-package-to-mfe-source-${pkgDir.replace(/\/$/, '').replace(/[^a-z0-9]+/gi, '-')}`,
  comment:
    `${pkgDir} must not depend on a micro-frontend's source code. Shared packages are ` +
    'lower-level building blocks; product composition belongs in the MFE/app layer.',
  severity: 'error',
  from: { path: `^${pkgDir}` },
  to: { path: '^services/micro-frontends/[^/]+/' },
}));

const noMfeToGatewaySourceRule = {
  name: 'no-mfe-to-api-gateway-source',
  comment:
    'Micro-frontends must call the API boundary at runtime; they must not import ' +
    'services/api-gateway source directly.',
  severity: 'error',
  from: { path: '^services/micro-frontends/[^/]+/' },
  to: { path: '^services/api-gateway/' },
};

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
  forbidden: [
    ...noCrossMfeImportRules,
    ...noPackageToMfeSourceRules,
    noMfeToGatewaySourceRule,
    ...noDeepPackageInternalImportRules,
  ],
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
