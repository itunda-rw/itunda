#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const [, , command, ...args] = process.argv;
const categories = new Set(["FINANCE","SHOPPING","PRODUCTIVITY","LIFESTYLE","OTHER"]);
const permissions = new Set(["identity","navigation","share","storage","notifications","payments","location","camera","contacts"]);
const here = path.dirname(fileURLToPath(import.meta.url));
const templatePath = path.resolve(here, "../../../packages/saronite/mini-apps/partner-template/manifest.json");
const rootKeys = new Set(["$schema","manifestVersion","id","name","version","category","description","entry","icon","permissions","brandTheme"]);
const paletteRoles = ["brand","brandStrong","brandSurface","onBrand","focus","pressed"];

function usage() {
  console.log("Itunda mini-app developer tool\n\n  new <name>       Scaffold a mini-app\n  validate [path]  Validate a manifest");
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
function isHttpsUrl(value) {
  if (typeof value !== "string") return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && Boolean(url.hostname) && !url.username && !url.password;
  } catch { return false; }
}
function scaffold(name) {
  const displayName = typeof name === "string" ? name.trim() : "";
  if (!displayName || displayName.length > 80) return fail("name must contain 1-80 characters");
  const directory = path.resolve(slugify(displayName));
  if (fs.existsSync(directory) && fs.readdirSync(directory).length) return fail("directory already exists and is not empty: " + directory);
  const id = flag("--id", "rw.example." + slugify(displayName));
  const category = (flag("--category", "OTHER") || "OTHER").toUpperCase();
  const bundle = flag("--bundle", "https://example.com/itunda-mini-app.bundle.js");
  if (!/^rw\.[a-z0-9]+(?:[._-][a-z0-9]+)+$/.test(id)) return fail("invalid mini-app id");
  if (!categories.has(category)) return fail("invalid category: " + category);
  if (!isHttpsUrl(bundle)) return fail("bundle URL must be a valid HTTPS URL without embedded credentials; local HTTP bundles are not accepted by the manifest contract");
  fs.mkdirSync(path.join(directory, "src"), {recursive:true});
  const canonicalTemplate = JSON.parse(fs.readFileSync(templatePath, "utf8"));
  const manifest = {
    ...canonicalTemplate,
    id,
    name: displayName,
    version: "0.1.0",
    category,
    description: "Itunda mini-app: " + displayName,
    entry: {...canonicalTemplate.entry, bundleUrl:bundle},
    icon: {...canonicalTemplate.icon},
    permissions: [],
    brandTheme: {...structuredClone(canonicalTemplate.brandTheme), themeId:slugify(displayName)}
  };
  fs.writeFileSync(path.join(directory,"manifest.json"), JSON.stringify(manifest,null,2)+"\n");
  fs.writeFileSync(path.join(directory,"src","README.md"), "# " + displayName + "\n\nBuild feature UI here and keep host capabilities behind explicit Saronite permissions.\n");
  fs.writeFileSync(path.join(directory,"AGENTS.md"), "# Itunda mini-app rules\n\n- Use IDS component anatomy and accessibility states.\n- Keep partner branding inside semantic brand-theme roles.\n- Request only permissions actually needed.\n- Do not embed host credentials or unrestricted native APIs.\n- Provide loading, empty, error, unsupported and reduced-motion states.\n");
  console.log("created " + directory);
}
function validate(file) {
  const target = path.resolve(file || "manifest.json");
  if (!fs.existsSync(target)) return fail("manifest not found: " + target);
  let m;
  try { m = JSON.parse(fs.readFileSync(target,"utf8")); } catch { return fail("manifest is not valid JSON"); }
  const e = [];
  if (!m || typeof m !== "object" || Array.isArray(m)) return fail("manifest root must be an object");
  for (const key of Object.keys(m)) if (!rootKeys.has(key)) e.push("unknown manifest property: " + key);
  if (m.$schema !== undefined && !isHttpsUrl(m.$schema)) e.push("$schema must be a valid HTTPS URL without embedded credentials");
  if (m.manifestVersion !== 1) e.push("manifestVersion must be 1");
  if (typeof m.id !== "string" || !/^rw\.[a-z0-9]+(?:[._-][a-z0-9]+)+$/.test(m.id)) e.push("id is invalid");
  if (typeof m.name !== "string" || !m.name.trim() || m.name.length > 80) e.push("name must contain 1-80 characters");
  if (typeof m.version !== "string" || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(m.version)) e.push("version must be semantic");
  if (!categories.has(m.category)) e.push("category is invalid");
  if (typeof m.description !== "string" || !m.description.trim() || m.description.length > 240) e.push("description must contain 1-240 characters");
  if (m.entry?.type !== "saronite") e.push("entry.type must be saronite");
  if (!isHttpsUrl(m.entry?.bundleUrl)) e.push("entry.bundleUrl must be a valid HTTPS URL without embedded credentials");
  if (m.entry && Object.keys(m.entry).some(k => !["type","bundleUrl"].includes(k))) e.push("entry contains an unknown property");
  if (!isHttpsUrl(m.icon?.url)) e.push("icon.url must be a valid HTTPS URL without embedded credentials");
  if (m.icon && Object.keys(m.icon).some(k => k !== "url")) e.push("icon contains an unknown property");
  if (!Array.isArray(m.permissions) || m.permissions.some(p => !permissions.has(p))) e.push("permissions contains an unsupported scope");
  if (Array.isArray(m.permissions) && new Set(m.permissions).size !== m.permissions.length) e.push("permissions must not contain duplicates");
  if (m.brandTheme !== undefined) {
    if (!m.brandTheme || typeof m.brandTheme !== "object" || Array.isArray(m.brandTheme)) e.push("brandTheme must be an object");
    else {
      if (typeof m.brandTheme.themeId !== "string" || !m.brandTheme.themeId.trim() || m.brandTheme.themeId.length > 64) e.push("brandTheme.themeId must contain 1-64 characters");
      for (const key of Object.keys(m.brandTheme)) if (!["themeId","light","dark"].includes(key)) e.push("brandTheme contains an unknown property: " + key);
      for (const mode of ["light","dark"]) {
        const palette = m.brandTheme[mode];
        if (!palette || typeof palette !== "object" || Array.isArray(palette)) { e.push("brandTheme."+mode+" is required"); continue; }
        for (const role of Object.keys(palette)) if (!paletteRoles.includes(role)) e.push("brandTheme."+mode+" contains an unknown role: " + role);
        for (const role of paletteRoles) if (!/^#[0-9A-Fa-f]{6}$/.test(palette[role] || "")) e.push("brandTheme."+mode+"."+role+" must be six-digit hex");
      }
    }
  }
  if (e.length) { e.forEach(x => console.error("x "+x)); process.exitCode=1; return; }
  console.log("valid Itunda mini-app manifest: " + target);
}
if (command === "new") {
  const name = args.find(a => !a.startsWith("--"));
  if (!name) { usage(); process.exitCode=1; } else scaffold(name);
} else if (command === "validate") validate(args.find(a => !a.startsWith("--")));
else { usage(); if (command) process.exitCode=1; }
