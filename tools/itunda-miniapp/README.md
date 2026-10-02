# Itunda Mini-App Developer Tool

Dependency-free local entry point for Apps in Itunda projects.

## Scaffold

    node tools/itunda-miniapp/bin/itunda-miniapp.mjs new my-shop --id rw.example.my-shop --category SHOPPING

## Validate

    node tools/itunda-miniapp/bin/itunda-miniapp.mjs validate my-shop/manifest.json

The lifecycle is scaffold -> develop -> validate -> build -> human review -> publish -> operate.
The tool never bypasses human review.
