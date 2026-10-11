import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../../..");
const cli = path.join(repoRoot, "tools/itunda-miniapp/bin/itunda-miniapp.mjs");
const schemaPath = path.join(repoRoot, "packages/saronite/mini-apps/partner-template/manifest.schema.json");
const templatePath = path.join(repoRoot, "packages/saronite/mini-apps/partner-template/manifest.json");

function validateManifest(manifest) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "itunda-manifest-test-"));
  const file = path.join(dir, "manifest.json");
  fs.writeFileSync(file, JSON.stringify(manifest));
  try {
    return spawnSync(process.execPath, [cli, "validate", file], { encoding: "utf8" });
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test("partner template declares its schema URI as an allowed manifest property", () => {
  const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  assert.equal(schema.additionalProperties, false);
  assert.ok(schema.properties.$schema, "root schema must allow the template's $schema property");
  assert.match(schema.properties.$schema.format, /uri/);
  assert.ok(manifest.$schema, "template should declare the schema used to validate it");
});

test("CLI accepts the checked-in partner template", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  const result = validateManifest(manifest);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /valid Itunda mini-app manifest/);
});

test("CLI rejects an unsupported permission", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  manifest.permissions = ["root-access"];
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unsupported scope/);
});

test("CLI rejects non-HTTPS bundle URLs", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  manifest.entry.bundleUrl = "http://example.com/app.js";
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /valid HTTPS URL/);
});

test("CLI rejects malformed semantic versions", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  manifest.version = "latest";
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /semantic/);
});

test("CLI rejects undeclared root properties", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  manifest.debugBypassAuth = true;
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unknown manifest property/);
});

test("CLI rejects malformed schema URLs", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  manifest.$schema = "file:///tmp/evil-schema.json";
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /\$schema must be a valid HTTPS URL/);
});

test("CLI rejects duplicate permissions", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  manifest.permissions = ["storage", "storage"];
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /must not contain duplicates/);
});

test("CLI rejects incomplete theme palettes", () => {
  const manifest = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  delete manifest.brandTheme.dark.focus;
  const result = validateManifest(manifest);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /brandTheme.dark.focus/);
});
