#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import crypto from "node:crypto";

const [, , command, ...args] = process.argv;
const categories = new Set(["FINANCE","SHOPPING","PRODUCTIVITY","LIFESTYLE","OTHER"]);
const permissions = new Set(["identity","navigation","share","storage","notifications","payments","location","camera","contacts"]);
const commands = ["new","plan","dev","build","validate","test","release","submit"];
function projectRoot(file = ".") {
  return path.resolve(file);
}
function readManifest(file = "manifest.json") {
  const target = projectRoot(file);
  if (!fs.existsSync(target)) throw new Error("manifest not found: " + target);
  return JSON.parse(fs.readFileSync(target, "utf8"));
}
function writeJson(file, value) {
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + "\n");
}

function resolveBundlePath(root, bundleFile) {
  const candidate = path.resolve(root, bundleFile);
  const relativeCandidate = path.relative(root, candidate);
  if (!relativeCandidate || relativeCandidate === ".." || relativeCandidate.startsWith(".." + path.sep) || path.isAbsolute(relativeCandidate)) {
    throw new Error("bundle file must stay inside the mini-app project directory");
  }
  if (fs.existsSync(candidate)) {
    const realRoot = fs.realpathSync(root);
    const realCandidate = fs.realpathSync(candidate);
    const realRelative = path.relative(realRoot, realCandidate);
    if (!realRelative || realRelative === ".." || realRelative.startsWith(".." + path.sep) || path.isAbsolute(realRelative)) {
      throw new Error("bundle file must resolve inside the mini-app project directory");
    }
  }
  return candidate;
}

function usage() {
  console.log("Itunda mini-app developer tool\n\n  new <name>       Scaffold a mini-app\n  plan [path]      Show the development/release plan\n  dev [path]       Start the local development command when configured\n  build [path]     Build a mini-app when configured\n  validate [path]  Validate a manifest and platform rules\n  test [path]      Run the project's configured tests\n  release [path]   Generate an immutable release manifest (no production publish)\n  submit [path]    Submit a mini-app for human review using ITUNDA_API_KEY");
}
function fail(message) {
  console.error("x " + message);
  process.exitCode = 1;
}
function flag(name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
}
function slugify(value) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "mini-app";
}
function scaffold(name) {
  const directory = path.resolve(slugify(name));
  if (fs.existsSync(directory) && fs.readdirSync(directory).length) return fail("directory already exists and is not empty: " + directory);
  const id = flag("--id", slugify(name));
  const category = (flag("--category", "OTHER") || "OTHER").toUpperCase();
  const bundle = flag("--bundle", "https://example.com/itunda-mini-app.bundle.js");
  if (!/^[a-z0-9]+(?:[.-][a-z0-9]+)*$/.test(id)) return fail("invalid mini-app id");
  if (!categories.has(category)) return fail("invalid category: " + category);
  if (!bundle.startsWith("https://") && !args.includes("--local")) return fail("bundle URL must use HTTPS, or pass --local");
  fs.mkdirSync(path.join(directory, "src"), {recursive:true});
  const manifest = {
    $schema: "https://raw.githubusercontent.com/itunda-rw/itunda/main/packages/saronite/mini-apps/partner-template/manifest.schema.json",
    manifestVersion: 1, id, name: name.trim(), version: "0.1.0", category,
    description: "Itunda mini-app: " + name.trim(),
    entry: {type:"saronite", bundleUrl:bundle},
    icon: {url:"https://example.com/icon.png"},
    permissions: [],
    brandTheme: {
      themeId: slugify(name),
      light: {brand:"#7472F4",brandStrong:"#5E5BE6",brandSurface:"#F0EFFF",onBrand:"#FFFFFF",focus:"#7472F4",pressed:"#5E5BE6"},
      dark: {brand:"#8A88FF",brandStrong:"#A9A7FF",brandSurface:"#2C2B46",onBrand:"#111118",focus:"#A9A7FF",pressed:"#8A88FF"}
    }
  };
  fs.writeFileSync(path.join(directory,"manifest.json"), JSON.stringify(manifest,null,2)+"\n");
  fs.writeFileSync(path.join(directory,"src","README.md"), "# " + name.trim() + "\n\nBuild feature UI here and keep host capabilities behind explicit Saronite permissions.\n");
  fs.writeFileSync(path.join(directory,"AGENTS.md"), "# Itunda mini-app rules\n\n- Use IDS component anatomy and accessibility states.\n- Keep partner branding inside semantic brand-theme roles.\n- Request only permissions actually needed.\n- Do not embed host credentials or unrestricted native APIs.\n- Provide loading, empty, error, unsupported and reduced-motion states.\n");
  console.log("created " + directory);
}
function validate(file) {
  const target = path.resolve(file || "manifest.json");
  if (!fs.existsSync(target)) return fail("manifest not found: " + target);
  let m;
  try { m = JSON.parse(fs.readFileSync(target,"utf8")); } catch { return fail("manifest is not valid JSON"); }
  const e = [];
  if (m.manifestVersion !== 1) e.push("manifestVersion must be 1");
  if (typeof m.id !== "string" || !/^[a-z0-9]+(?:[.-][a-z0-9]+)*$/.test(m.id)) e.push("id is invalid");
  if (typeof m.name !== "string" || !m.name.trim()) e.push("name is required");
  if (typeof m.version !== "string" || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(m.version)) e.push("version must be semantic");
  if (!categories.has(m.category)) e.push("category is invalid");
  if (m.entry?.type !== "saronite") e.push("entry.type must be saronite");
  if (typeof m.entry?.bundleUrl !== "string" || !m.entry.bundleUrl.startsWith("https://")) e.push("entry.bundleUrl must be HTTPS");
  if (typeof m.icon?.url !== "string" || !m.icon.url.startsWith("https://")) e.push("icon.url must be HTTPS");
  if (!Array.isArray(m.permissions) || m.permissions.some(p => !permissions.has(p))) e.push("permissions contains an unsupported scope");
  if (new Set(m.permissions || []).size !== (m.permissions || []).length) e.push("permissions must not contain duplicates");
  if (m.brandTheme) for (const mode of ["light","dark"]) for (const role of ["brand","brandStrong","brandSurface","onBrand","focus","pressed"]) if (!/^#[0-9A-Fa-f]{6}$/.test(m.brandTheme?.[mode]?.[role] || "")) e.push("brandTheme."+mode+"."+role+" must be six-digit hex");
  if (e.length) { e.forEach(x => console.error("x "+x)); process.exitCode=1; return; }
  console.log("valid Itunda mini-app manifest: " + target);
}
async function runProjectCommand(command, file, args) {
  const target = projectRoot(file || ".");
  const packageFile = path.join(target, "package.json");
  if (!fs.existsSync(packageFile)) return fail("package.json not found: " + packageFile);
  let pkg;
  try { pkg = JSON.parse(fs.readFileSync(packageFile, "utf8")); } catch { return fail("package.json is not valid JSON"); }
  const script = command === "dev" ? "dev" : command === "build" ? "build" : "test";
  if (!pkg.scripts?.[script]) return fail("package.json has no \"" + script + "\" script");
  const {spawnSync} = await import("node:child_process");
  const result = spawnSync(process.platform === "win32" ? "npm.cmd" : "npm", ["run", script, ...args], {cwd: target, stdio: "inherit"});
  process.exitCode = result.status ?? 1;
}
function plan(file) {
  try {
    const m = readManifest(path.join(file || ".", "manifest.json"));
    console.log(JSON.stringify({
      app: m.id,
      version: m.version,
      stages: ["create","develop","validate","build","sandbox","test-device","release"],
      permissions: m.permissions,
      productionPublish: "requires developer-console approval"
    }, null, 2));
  } catch (e) { fail(e.message); }
}
function release(file) {
  try {
    const root = projectRoot(file || ".");
    const m = readManifest(path.join(root, "manifest.json"));
    const bundle = m.entry?.bundleUrl;
    if (!bundle?.startsWith("https://")) return fail("release requires an HTTPS bundleUrl");
    const canonical = JSON.stringify(m);
    const manifestSha256 = crypto.createHash("sha256").update(canonical).digest("hex");
    const bundleFile = flag("--bundle-file");
    if (!bundleFile) return fail("release requires --bundle-file pointing to the exact built bundle");
    const bundlePath = resolveBundlePath(root, bundleFile);
    if (!fs.existsSync(bundlePath) || !fs.statSync(bundlePath).isFile()) return fail("bundle file not found: " + bundlePath);
    const bundleBytes = fs.readFileSync(bundlePath);
    if (bundleBytes.length === 0) return fail("bundle file must not be empty");
    const bundleSha256 = crypto.createHash("sha256").update(bundleBytes).digest("hex");
    const bundleSizeBytes = bundleBytes.length;
    const releaseId = crypto.createHash("sha256").update(manifestSha256 + ":" + bundleSha256).digest("hex").slice(0, 24);
    writeJson(path.join(root, "release.manifest.json"), {
      releaseId,
      manifestSha256,
      bundleSha256,
      bundleSizeBytes,
      bundleFile: bundleSha256 ? bundleFile : null,
      appId: m.id,
      version: m.version,
      bundleUrl: bundle,
      publishedAt: null,
      status: "READY_FOR_CONSOLE_PUBLISH"
    });
    console.log("release manifest generated: " + releaseId);
  } catch (e) { fail(e.message); }
}

async function submit(file) {
  try {
    const root = projectRoot(file || ".");
    const m = readManifest(path.join(root, "manifest.json"));
    const key = process.env.ITUNDA_API_KEY;
    const base = (process.env.ITUNDA_API_BASE_URL || "https://api.itunda.im").replace(/\/$/, "");
    if (!key) return fail("ITUNDA_API_KEY is required; never put partner keys in source control or manifest files");
    if (!m.name || !m.description || !m.icon?.url || !m.entry?.bundleUrl) return fail("manifest is missing required submission metadata");
    validate(path.join(root, "manifest.json"));
    if (process.exitCode) return;
    const releasePath = path.join(root, "release.manifest.json");
    const release = fs.existsSync(releasePath) ? JSON.parse(fs.readFileSync(releasePath, "utf8")) : null;
    // Manifest capabilities and partner API scopes are different contracts. Never
    // silently drop a requested capability: the current submission API only accepts
    // explicit read scopes, and only identity has a safe mapping today.
    const permissionMap = {identity:"profile:read"};
    const unmapped = [...new Set((m.permissions || []).filter((p) => !permissionMap[p]))];
    if (unmapped.length) return fail("cannot submit manifest permissions without an explicit partner API scope mapping: " + unmapped.join(", ") + ". Currently supported manifest permission: identity (maps to profile:read)");
    const requested = [...new Set((m.permissions || []).map((p) => permissionMap[p]))];
    if (!release) return fail("release.manifest.json is required; run release with --bundle-file pointing to the built bundle before submitting");
    if (!release.releaseId || !/^[a-f0-9]{64}$/.test(release.manifestSha256 || "") || !/^[a-f0-9]{64}$/.test(release.bundleSha256 || "") || !Number.isInteger(release.bundleSizeBytes) || release.bundleSizeBytes <= 0 || typeof release.bundleFile !== "string" || !release.bundleFile) {
      return fail("release.manifest.json has incomplete integrity metadata; rerun release with --bundle-file pointing to the built bundle before submitting");
    }
    const currentManifestSha256 = crypto.createHash("sha256").update(JSON.stringify(m)).digest("hex");
    if (release.manifestSha256 !== currentManifestSha256) return fail("release manifest digest does not match manifest.json; regenerate the release");
    const bundlePath = resolveBundlePath(root, release.bundleFile);
    if (!fs.existsSync(bundlePath) || !fs.statSync(bundlePath).isFile()) return fail("release bundle file not found: " + bundlePath);
    const bundleBytes = fs.readFileSync(bundlePath);
    const actualBundleSha256 = crypto.createHash("sha256").update(bundleBytes).digest("hex");
    if (actualBundleSha256 !== release.bundleSha256 || bundleBytes.length !== release.bundleSizeBytes) return fail("release bundle integrity check failed; regenerate the release from the exact built bundle");
    const expectedReleaseId = crypto.createHash("sha256").update(release.manifestSha256 + ":" + release.bundleSha256).digest("hex").slice(0, 24);
    if (release.releaseId !== expectedReleaseId) return fail("releaseId does not match release integrity metadata");
    const response = await fetch(base + "/api/v1/partners/mini-apps", {
      method: "POST",
      headers: {"Content-Type":"application/json","X-Api-Key":key},
      body: JSON.stringify({
        name: m.name,
        description: m.description,
        iconUrl: m.icon.url,
        bundleUrl: m.entry.bundleUrl,
        permissions: requested,
        releaseId: release?.releaseId ?? null,
        manifestSha256: release?.manifestSha256 ?? null,
        bundleSha256: release?.bundleSha256 ?? null,
        bundleSizeBytes: release?.bundleSizeBytes ?? null
      })
    });
    const body = await response.text();
    if (!response.ok) return fail("partner submission failed (" + response.status + "): " + body);
    console.log("mini-app submitted for human review:");
    console.log(body);
  } catch (e) { fail(e.message); }
}

if (command === "new") scaffold(args.find(a => !a.startsWith("--")) || "mini-app");
else if (command === "plan") plan(args.find(a => !a.startsWith("--")));
else if (command === "validate") validate(args.find(a => !a.startsWith("--")) || "manifest.json");
else if (command === "dev" || command === "build" || command === "test") runProjectCommand(command, args.find(a => !a.startsWith("--")), args.filter(a => a !== args.find(x => !x.startsWith("--"))));
else if (command === "release") release(args.find(a => !a.startsWith("--")));
else if (command === "submit") submit(args.find(a => !a.startsWith("--")));
