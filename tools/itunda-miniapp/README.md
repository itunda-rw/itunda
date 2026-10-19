# Itunda Mini-App Developer Tool

Dependency-free local entry point for Apps in Itunda projects.

## Scaffold

    node tools/itunda-miniapp/bin/itunda-miniapp.mjs new my-shop --id rw.example.my-shop --category SHOPPING

## Validate

    node tools/itunda-miniapp/bin/itunda-miniapp.mjs validate my-shop/manifest.json

## Release and submit for review

Build your bundle first, then create release integrity metadata from the exact bundle file:

    node tools/itunda-miniapp/bin/itunda-miniapp.mjs release my-shop --bundle-file dist/index.bundle.js

The release command writes `release.manifest.json` with the manifest digest, bundle digest, byte size, and deterministic release ID. Keep the manifest and bundle paired; changing either requires generating a new release manifest.

Submit the app for human review only after the release manifest contains complete integrity metadata:

    ITUNDA_API_KEY=... node tools/itunda-miniapp/bin/itunda-miniapp.mjs submit my-shop

Set `ITUNDA_API_BASE_URL` only when targeting an explicitly configured non-production environment. Never commit API keys or put them in a manifest. Submission currently maps only the `identity` manifest permission to the `profile:read` API scope; unsupported capabilities fail closed until a reviewed mapping is implemented.

The lifecycle is scaffold -> develop -> validate -> build -> release -> sandbox/device test -> human review -> publish -> operate.
The tool never bypasses human review.
