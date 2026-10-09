import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {spawnSync} from "node:child_process";

const cli = fileURLToPath(new URL("../bin/itunda-miniapp.mjs", import.meta.url));

function run(args, cwd, extraEnv = {}) {
  return spawnSync(process.execPath, [cli, ...args], {
    cwd,
    encoding: "utf8",
    env: {...process.env, ...extraEnv},
  });
}

function tempProject(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "itunda-miniapp-test-"));
  t.after(() => fs.rmSync(dir, {recursive: true, force: true}));
  return dir;
}

test("new scaffolds a manifest that validates", (t) => {
  const cwd = tempProject(t);
  const created = run(["new", "Sample Wallet"], cwd);
  assert.equal(created.status, 0, created.stderr);
  const project = path.join(cwd, "sample-wallet");
  const checked = run(["validate", path.join(project, "manifest.json")], cwd);
  assert.equal(checked.status, 0, checked.stderr);
  assert.match(checked.stdout, /valid Itunda mini-app manifest/);
  const agentRules = fs.readFileSync(path.join(project, "AGENTS.md"), "utf8");
  assert.match(agentRules, /# Itunda mini-app rules\n\n- Use IDS/);
  assert.doesNotMatch(agentRules, /\\\\n/);
});

test("submit fails closed when a manifest capability has no API scope mapping", (t) => {
  const cwd = tempProject(t);
  assert.equal(run(["new", "Permission Test"], cwd).status, 0);
  const manifestPath = path.join(cwd, "permission-test", "manifest.json");
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  manifest.permissions = ["navigation"];
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  const result = run(["submit", path.join(cwd, "permission-test")], cwd, {
    ITUNDA_API_KEY: "test-only-not-a-real-key",
    ITUNDA_API_BASE_URL: "http://127.0.0.1:1",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /without an explicit partner API scope mapping/);
  assert.doesNotMatch(result.stderr, /fetch failed|ECONNREFUSED/);
});

test("submit rejects incomplete release integrity metadata before network access", (t) => {
  const cwd = tempProject(t);
  assert.equal(run(["new", "Release Test"], cwd).status, 0);
  const project = path.join(cwd, "release-test");
  fs.writeFileSync(path.join(project, "release.manifest.json"), JSON.stringify({
    releaseId: "partial-release",
    manifestSha256: "a".repeat(64),
    bundleSha256: null,
    bundleSizeBytes: null,
  }));
  const result = run(["submit", project], cwd, {
    ITUNDA_API_KEY: "test-only-not-a-real-key",
    ITUNDA_API_BASE_URL: "http://127.0.0.1:1",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /incomplete integrity metadata/);
  assert.doesNotMatch(result.stderr, /fetch failed|ECONNREFUSED/);
});


test("submit requires a release manifest before network access", (t) => {
  const cwd = tempProject(t);
  assert.equal(run(["new", "Missing Release"], cwd).status, 0);
  const project = path.join(cwd, "missing-release");
  const result = run(["submit", project], cwd, {
    ITUNDA_API_KEY: "test-only-not-a-real-key",
    ITUNDA_API_BASE_URL: "http://127.0.0.1:1",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /release\.manifest\.json is required/);
  assert.doesNotMatch(result.stderr, /fetch failed|ECONNREFUSED/);
});


test("release rejects bundle paths that escape the project directory", (t) => {
  const cwd = tempProject(t);
  assert.equal(run(["new", "Path Test"], cwd).status, 0);
  const project = path.join(cwd, "path-test");
  const outside = path.join(cwd, "outside.js");
  fs.writeFileSync(outside, "not part of the app");
  const result = run(["release", project, "--bundle-file", "../outside.js"], cwd);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /bundle file must stay inside the mini-app project directory/);
  assert.equal(fs.existsSync(path.join(project, "release.manifest.json")), false);
});

test("release requires an existing non-empty built bundle and records its digest", (t) => {
  const cwd = tempProject(t);
  assert.equal(run(["new", "Bundle Test"], cwd).status, 0);
  const project = path.join(cwd, "bundle-test");
  const missing = run(["release", project], cwd);
  assert.notEqual(missing.status, 0);
  assert.match(missing.stderr, /requires --bundle-file/);
  const bundleDir = path.join(project, "dist");
  fs.mkdirSync(bundleDir);
  const bundlePath = path.join(bundleDir, "app.js");
  const bytes = Buffer.from("globalThis.itundaMiniApp = true;\n");
  fs.writeFileSync(bundlePath, bytes);
  const released = run(["release", project, "--bundle-file", "dist/app.js"], cwd);
  assert.equal(released.status, 0, released.stderr);
  const release = JSON.parse(fs.readFileSync(path.join(project, "release.manifest.json"), "utf8"));
  assert.equal(release.bundleSha256, crypto.createHash("sha256").update(bytes).digest("hex"));
  assert.equal(release.bundleSizeBytes, bytes.length);
  assert.equal(release.bundleFile, "dist/app.js");
  assert.match(release.releaseId, /^[a-f0-9]{24}$/);
});

test("submit detects bundle tampering before network access", (t) => {
  const cwd = tempProject(t);
  assert.equal(run(["new", "Tamper Test"], cwd).status, 0);
  const project = path.join(cwd, "tamper-test");
  fs.mkdirSync(path.join(project, "dist"));
  fs.writeFileSync(path.join(project, "dist", "app.js"), "original bundle");
  assert.equal(run(["release", project, "--bundle-file", "dist/app.js"], cwd).status, 0);
  fs.writeFileSync(path.join(project, "dist", "app.js"), "modified bundle");
  const result = run(["submit", project], cwd, {
    ITUNDA_API_KEY: "test-only-not-a-real-key",
    ITUNDA_API_BASE_URL: "http://127.0.0.1:1",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /bundle integrity check failed/);
  assert.doesNotMatch(result.stderr, /fetch failed|ECONNREFUSED/);
});
