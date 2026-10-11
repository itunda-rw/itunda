# Itunda Mini-App Developer Tool

Dependency-free local entry point for Apps in Itunda projects. The CLI creates a starter manifest and validates the manifest contract; it does not build, sign, publish, or deploy a mini-app.

## Requirements

- Node.js 20 or newer
- A valid Itunda mini-app ID such as `rw.example.my-shop`
- An HTTPS bundle URL for the manifest. Local HTTP bundle URLs are deliberately rejected by the manifest contract; use your development server separately rather than weakening a publishable manifest.

## Scaffold

```sh
node tools/itunda-miniapp/bin/itunda-miniapp.mjs new my-shop --id rw.example.my-shop --category SHOPPING --bundle https://example.com/my-shop.bundle.js
```

The generated `manifest.json` copies the semantic Brand v3 theme from the checked-in partner template (primary `#8F89FF`, pressed `#7D77F7`, dark brand surface `#25252D`) and includes light and dark theme roles. The template is the source of truth; the CLI does not recreate canonical logo assets. Request only the permissions the feature actually needs. Do not put credentials, private keys, identity documents, or other secrets in the manifest or bundle.

## Validate

```sh
node tools/itunda-miniapp/bin/itunda-miniapp.mjs validate my-shop/manifest.json
```

Validation checks the manifest version and shape, required fields, HTTPS URLs, semantic version, supported permission scopes, duplicate permissions, and complete light/dark theme roles. Validation is a local contract check; it does not prove that a remote bundle exists, is signed, is safe to execute, or is compatible with a particular native shell.

## Test the CLI

```sh
cd tools/itunda-miniapp
npm test
```

Tests run with Node's built-in test runner and cover the checked-in partner template, scaffold round-trip, Itunda brand defaults, insecure URLs, unsupported/duplicate permissions, malformed versions/schema URLs, unknown properties, and incomplete palettes.

## Lifecycle and production boundary

The intended lifecycle is **scaffold → develop → validate → build → human review → sign → publish → operate**. The CLI currently implements `new` and `validate`; build/sign/publish/rollback remain separate platform responsibilities. Do not treat successful validation as production approval. Runtime artifact signing, immutable artifact integrity, compatibility enforcement, staged rollout, and rollback must be verified before remote bundle delivery is considered production-ready.
