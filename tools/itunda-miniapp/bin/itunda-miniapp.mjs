#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const [, , command, ...args] = process.argv;
const categories = new Set(["FINANCE", "SHOPPING", "PRODUCTIVITY", "LIFESTYLE", "OTHER"]);
const permissions = new Set([
  "identity", "auth", "navigation", "share", "storage", "notifications",
  "payments", "location", "camera", "contacts", "clipboard", "haptic",
  "analytics", "events", "deepLinks"
]);

function usage() {
  console.log(`Itunda mini-app developer tool

  new <name>                  Scaffold a mini-app
  dev <path>                  Inspect local development inputs
  validate [manifest]         Validate a manifest
  test [manifest]             Run contract-level local checks
  build [manifest]            Create a deterministic build artifact
  inspect [manifest]          Print normalized platform metadata
  publish [manifest]          Publish to a platform endpoint
  status <id>                 Query platform publication status

Options:
  --id <id>                   Mini-app identifier
  --category <category>       FINANCE|SHOPPING|PRODUCTIVITY|LIFESTYLE|OTHER
  --bundle <https-url>        Production bundle URL
  --permission <scope>        Repeat to request a Saronite scope
  --endpoint <url>            Platform API endpoint
  --dry-run                   Validate and print the operation without network I/O`);
}

function fail(message) {
  console.error("x " + message);
  process.exitCode = 1;
  return false;
}

function flag(name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
}

function flags(name) {
  const values = [];
  for (let i = 0; i < args.length; i += 1) {
    if (args[i] === name && args[i + 1]) values.push(args[i + 1]);
  }
  return values;
}

function slugify(value) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "mini-app";
}

function manifestPath(input) {
  return path.resolve(input || "manifest.json");
}

function readManifest(input) {
  const target = manifestPath(input);
  if (!fs.existsSync(target)) return fail("manifest not found: " + target);
  try {
    return { target, manifest: JSON.parse(fs.readFileSync(target, "utf8")) };
  } catch {
    fail("manifest is not valid JSON: " + target);
    return null;
  }
}

function validateManifest(m) {
  const errors = [];
  if (m.manifestVersion !== 1) errors.push("manifestVersion must be 1");
  if (typeof m.id !== "string" || !/^rw\.[a-z0-9]+(?:[._-][a-z0-9]+)+$/.test(m.id)) errors.push("id is invalid");
  if (typeof m.name !== "string" || !m.name.trim()) errors.push("name is required");
  if (typeof m.version !== "string" || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(m.version)) errors.push("version must be semantic");
  if (!categories.has(m.category)) errors.push("category is invalid");
  if (m.entry?.type !== "saronite") errors.push("entry.type must be saronite");
  if (typeof m.entry?.bundleUrl !== "string" || !m.entry.bundleUrl.startsWith("https://")) errors.push("entry.bundleUrl must be HTTPS");
  if (typeof m.icon?.url !== "string" || !m.icon.url.startsWith("https://")) errors.push("icon.url must be HTTPS");
  if (!Array.isArray(m.permissions) || m.permissions.some(p => !permissions.has(p))) errors.push("permissions contains an unsupported scope");
  if (new Set(m.permissions || []).size !== (m.permissions || []).length) errors.push("permissions must not contain duplicates");
  if (m.brandTheme) {
    for (const mode of ["light", "dark"]) {
      for (const role of ["brand", "brandStrong", "brandSurface", "onBrand", "focus", "pressed"]) {
        if (!/^#[0-9A-Fa-f]{6}$/.test(m.brandTheme?.[mode]?.[role] || "")) {
          errors.push("brandTheme." + mode + "." + role + " must be six-digit hex");
        }
      }
    }
  }
  return errors;
}

function scaffold(name) {
  const directory = path.resolve(slugify(name));
  if (fs.existsSync(directory) && fs.readdirSync(directory).length) return fail("directory already exists and is not empty: " + directory);
  const id = flag("--id", "rw.example." + slugify(name));
  const category = (flag("--category", "OTHER") || "OTHER").toUpperCase();
  const bundle = flag("--bundle", "https://example.com/itunda-mini-app.bundle.js");
  const requestedPermissions = flags("--permission");
  if (!/^rw\.[a-z0-9]+(?:[._-][a-z0-9]+)+$/.test(id)) return fail("invalid mini-app id");
  if (!categories.has(category)) return fail("invalid category: " + category);
  if (!bundle.startsWith("https://") && !args.includes("--local")) return fail("bundle URL must use HTTPS, or pass --local");
  if (requestedPermissions.some(p => !permissions.has(p))) return fail("unsupported permission: " + requestedPermissions.find(p => !permissions.has(p)));
  if (new Set(requestedPermissions).size !== requestedPermissions.length) return fail("permissions must not contain duplicates");

  fs.mkdirSync(path.join(directory, "src"), { recursive: true });
  const manifest = {
    $schema: "https://raw.githubusercontent.com/itunda-rw/itunda/main/packages/saronite/mini-apps/partner-template/manifest.schema.json",
    manifestVersion: 1,
    id,
    name: name.trim(),
    version: "0.1.0",
    category,
    description: "Itunda mini-app: " + name.trim(),
    entry: { type: "saronite", bundleUrl: bundle },
    icon: { url: "https://example.com/icon.png" },
    permissions: requestedPermissions,
    brandTheme: {
      themeId: slugify(name),
      light: { brand: "#7472F4", brandStrong: "#625FE0", brandSurface: "#F0EFFF", onBrand: "#FFFFFF", focus: "#7472F4", pressed: "#625FE0" },
      dark: { brand: "#9A98FF", brandStrong: "#B0AEFF", brandSurface: "#29274A", onBrand: "#FFFFFF", focus: "#9A98FF", pressed: "#B0AEFF" }
    }
  };
  fs.writeFileSync(path.join(directory, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
  fs.writeFileSync(path.join(directory, "src", "README.md"), "# " + name.trim() + "\n\nBuild feature UI here and keep host capabilities behind explicit Saronite permissions.\n");
  fs.writeFileSync(path.join(directory, "AGENTS.md"), "# Itunda mini-app rules\n\n- Use IDS component anatomy and accessibility states.\n- Keep partner branding inside semantic brand-theme roles.\n- Request only permissions actually needed.\n- Do not embed host credentials or unrestricted native APIs.\n- Provide loading, empty, error, unsupported and reduced-motion states.\n");
  console.log("created " + directory);
}

function validate(input) {
  const loaded = readManifest(input);
  if (!loaded) return false;
  const errors = validateManifest(loaded.manifest);
  if (errors.length) {
    errors.forEach(error => console.error("x " + error));
    process.exitCode = 1;
    return false;
  }
  console.log("valid Itunda mini-app manifest: " + loaded.target);
  return true;
}

function inspect(input) {
  const loaded = readManifest(input);
  if (!loaded) return;
  const m = loaded.manifest;
  console.log(JSON.stringify({
    id: m.id,
    name: m.name,
    version: m.version,
    category: m.category,
    entry: m.entry,
    permissions: m.permissions,
    brandTheme: m.brandTheme?.themeId,
    protocol: "saronite/1"
  }, null, 2));
}

function test(input) {
  const loaded = readManifest(input);
  if (!loaded) return;
  const errors = validateManifest(loaded.manifest);
  const checks = [
    ["manifest contract", errors.length === 0],
    ["permission uniqueness", new Set(loaded.manifest.permissions || []).size === (loaded.manifest.permissions || []).length],
    ["HTTPS entry", loaded.manifest.entry?.bundleUrl?.startsWith("https://") === true],
    ["dual-theme brand contract", Boolean(loaded.manifest.brandTheme?.light && loaded.manifest.brandTheme?.dark)]
  ];
  for (const [name, ok] of checks) console.log((ok ? "✓ " : "x ") + name);
  if (errors.length) errors.forEach(error => console.error("x " + error));
  if (checks.some(([, ok]) => !ok)) process.exitCode = 1;
}

function build(input) {
  const loaded = readManifest(input);
  if (!loaded) return;
  const errors = validateManifest(loaded.manifest);
  if (errors.length) {
    errors.forEach(error => console.error("x " + error));
    process.exitCode = 1;
    return;
  }
  const root = path.dirname(loaded.target);
  const out = path.join(root, "dist");
  fs.mkdirSync(out, { recursive: true });
  const manifestBytes = Buffer.from(JSON.stringify(loaded.manifest, null, 2) + "\n");
  const manifestSha256 = crypto.createHash("sha256").update(manifestBytes).digest("hex");
  fs.writeFileSync(path.join(out, "manifest.json"), manifestBytes);
  fs.writeFileSync(path.join(out, "build.json"), JSON.stringify({
    format: "itunda-mini-app",
    protocol: "saronite/1",
    id: loaded.manifest.id,
    version: loaded.manifest.version,
    manifestSha256,
    createdAt: new Date().toISOString()
  }, null, 2) + "\n");
  console.log("built " + out);
}

async function publish(input) {
  const loaded = readManifest(input);
  if (!loaded) return;
  const errors = validateManifest(loaded.manifest);
  if (errors.length) return errors.forEach(error => console.error("x " + error));
  const endpoint = flag("--endpoint");
  if (args.includes("--dry-run")) {
    console.log(JSON.stringify({ operation: "publish", id: loaded.manifest.id, version: loaded.manifest.version, endpoint: endpoint || null, dryRun: true }, null, 2));
    return;
  }
  if (!endpoint) return fail("publish requires --endpoint, or use --dry-run");
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json", "accept": "application/json" },
    body: JSON.stringify({ manifest: loaded.manifest })
  });
  const body = await response.text();
  if (!response.ok) return fail("publish failed (" + response.status + "): " + body);
  console.log(body || "published " + loaded.manifest.id + "@" + loaded.manifest.version);
}

async function status(id) {
  const endpoint = flag("--endpoint");
  if (!endpoint) return fail("status requires --endpoint");
  const response = await fetch(endpoint.replace(/\/$/, "") + "/" + encodeURIComponent(id), {
    headers: { "accept": "application/json" }
  });
  const body = await response.text();
  if (!response.ok) return fail("status failed (" + response.status + "): " + body);
  console.log(body);
}

if (command === "new") {
  const name = args.find(a => !a.startsWith("--"));
  if (!name) { usage(); process.exitCode = 1; } else scaffold(name);
} else if (command === "dev") inspect(args.find(a => !a.startsWith("--")));
else if (command === "validate") validate(args.find(a => !a.startsWith("--")));
else if (command === "test") test(args.find(a => !a.startsWith("--")));
else if (command === "build") build(args.find(a => !a.startsWith("--")));
else if (command === "inspect") inspect(args.find(a => !a.startsWith("--")));
else if (command === "publish") await publish(args.find(a => !a.startsWith("--")));
else if (command === "status") {
  const id = args.find(a => !a.startsWith("--"));
  if (!id) { usage(); process.exitCode = 1; } else await status(id);
} else {
  usage();
  if (command) process.exitCode = 1;
}
