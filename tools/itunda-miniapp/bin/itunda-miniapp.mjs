#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const [, , command, ...args] = process.argv;
const categories = new Set(["FINANCE","SHOPPING","PRODUCTIVITY","LIFESTYLE","OTHER"]);
const permissions = new Set(["identity","navigation","share","storage","notifications","payments","location","camera","contacts"]);

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
function scaffold(name) {
  const directory = path.resolve(slugify(name));
  if (fs.existsSync(directory) && fs.readdirSync(directory).length) return fail("directory already exists and is not empty: " + directory);
  const id = flag("--id", "rw.example." + slugify(name));
  const category = (flag("--category", "OTHER") || "OTHER").toUpperCase();
  const bundle = flag("--bundle", "https://example.com/itunda-mini-app.bundle.js");
  if (!/^rw\.[a-z0-9]+(?:[._-][a-z0-9]+)+$/.test(id)) return fail("invalid mini-app id");
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
      light: {brand:"#4F46E5",brandStrong:"#4338CA",brandSurface:"#EEF2FF",onBrand:"#FFFFFF",focus:"#4F46E5",pressed:"#4338CA"},
      dark: {brand:"#A5B4FC",brandStrong:"#C7D2FE",brandSurface:"#312E81",onBrand:"#111827",focus:"#C7D2FE",pressed:"#A5B4FC"}
    }
  };
  fs.writeFileSync(path.join(directory,"manifest.json"), JSON.stringify(manifest,null,2)+"\\n");
  fs.writeFileSync(path.join(directory,"src","README.md"), "# " + name.trim() + "\\n\\nBuild feature UI here and keep host capabilities behind explicit Saronite permissions.\\n");
  fs.writeFileSync(path.join(directory,"AGENTS.md"), "# Itunda mini-app rules\\n\\n- Use IDS component anatomy and accessibility states.\\n- Keep partner branding inside semantic brand-theme roles.\\n- Request only permissions actually needed.\\n- Do not embed host credentials or unrestricted native APIs.\\n- Provide loading, empty, error, unsupported and reduced-motion states.\\n");
  console.log("created " + directory);
}
function validate(file) {
  const target = path.resolve(file || "manifest.json");
  if (!fs.existsSync(target)) return fail("manifest not found: " + target);
  let m;
  try { m = JSON.parse(fs.readFileSync(target,"utf8")); } catch { return fail("manifest is not valid JSON"); }
  const e = [];
  if (m.manifestVersion !== 1) e.push("manifestVersion must be 1");
  if (typeof m.id !== "string" || !/^rw\.[a-z0-9]+(?:[._-][a-z0-9]+)+$/.test(m.id)) e.push("id is invalid");
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
if (command === "new") {
  const name = args.find(a => !a.startsWith("--"));
  if (!name) { usage(); process.exitCode=1; } else scaffold(name);
} else if (command === "validate") validate(args.find(a => !a.startsWith("--")));
else { usage(); if (command) process.exitCode=1; }
