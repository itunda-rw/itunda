import test from "node:test";
import assert from "node:assert/strict";
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
