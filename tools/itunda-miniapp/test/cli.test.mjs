import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const cli = fileURLToPath(new URL("../bin/itunda-miniapp.mjs", import.meta.url));

function run(cwd, ...args) {
  return spawnSync(process.execPath, [cli, ...args], {
    cwd,
    encoding: "utf8",
    timeout: 10_000
  });
}

function withTempDir(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "itunda-miniapp-"));
  try { fn(dir); } finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

test("scaffold creates a manifest accepted by the validator", () => {
  withTempDir((cwd) => {
    const scaffold = run(cwd, "new", "Sample Service");
    assert.equal(scaffold.status, 0, scaffold.stderr);
    const manifestPath = path.join(cwd, "sample-service", "manifest.json");
    assert.ok(fs.existsSync(manifestPath));
    const validation = run(cwd, "validate", manifestPath);
    assert.equal(validation.status, 0, validation.stderr);
    assert.match(validation.stdout, /valid Itunda mini-app manifest/);
  });
});

test("validator rejects insecure bundle URLs", () => {
  withTempDir((cwd) => {
    assert.equal(run(cwd, "new", "Sample Service").status, 0);
    const manifestPath = path.join(cwd, "sample-service", "manifest.json");
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    manifest.entry.bundleUrl = "http://example.com/bundle.js";
    fs.writeFileSync(manifestPath, JSON.stringify(manifest));
    const result = run(cwd, "validate", manifestPath);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /entry\.bundleUrl must be a valid HTTPS URL/);
  });
});

test("validator rejects duplicate permissions", () => {
  withTempDir((cwd) => {
    assert.equal(run(cwd, "new", "Sample Service").status, 0);
    const manifestPath = path.join(cwd, "sample-service", "manifest.json");
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    manifest.permissions = ["identity", "identity"];
    fs.writeFileSync(manifestPath, JSON.stringify(manifest));
    const result = run(cwd, "validate", manifestPath);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /permissions must not contain duplicates/);
  });
});

test("scaffold preserves a non-empty target directory", () => {
  withTempDir((cwd) => {
    const target = path.join(cwd, "sample-service");
    fs.mkdirSync(target);
    fs.writeFileSync(path.join(target, "keep.txt"), "keep");
    const result = run(cwd, "new", "Sample Service");
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /directory already exists and is not empty/);
    assert.equal(fs.readFileSync(path.join(target, "keep.txt"), "utf8"), "keep");
  });
});
