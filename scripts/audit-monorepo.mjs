import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const catalog = fs.readFileSync(path.join(root, ".yarnrc.yml"), "utf8");
const required = {
  react: "^19.2.7", "react-dom": "^19.2.7", typescript: "^5.0.0",
  "@types/react": "^19.2.17", "@types/react-dom": "^19.2.3", "@types/node": "^24.13.2",
  oxlint: "^1.71.0", vite: "^8.1.1", "@vitejs/plugin-react": "^6.0.3",
  "@originjs/vite-plugin-federation": "^1.4.1", "dependency-cruiser": "^16.10.0"
};
const errors = [];
const idsShowcase = fs.readFileSync(path.join(root, "design-system/index.html"), "utf8");
if (!idsShowcase.includes("#7472F4")) errors.push("design-system/index.html: canonical Itunda Indigo #7472F4 is missing");
if (idsShowcase.includes("#1F78FF") || idsShowcase.includes("#1769E0")) errors.push("design-system/index.html: stale legacy blue identity detected");
if (idsShowcase.includes("IDS v2.1")) errors.push("design-system/index.html: stale IDS v2.1 label detected");
for (const [name, version] of Object.entries(required)) {
  const line = name.startsWith("@") ? `  "${name}": ${version}` : `  ${name}: ${version}`;
  if (!catalog.includes(line)) errors.push(`catalog: ${name} must be ${version}`);
}
const rootPackage = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
const workspacePatterns = rootPackage.workspaces ?? [];
const dirs = workspacePatterns.flatMap((pattern) => {
  if (!pattern.includes("*")) return [pattern];
  const star = pattern.indexOf("*");
  const prefix = pattern.slice(0, star);
  const suffix = pattern.slice(star + 1);
  const base = path.join(root, prefix);
  if (!fs.existsSync(base)) return [];
  return fs.readdirSync(base, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(prefix, entry.name) + suffix)
    .filter((dir) => fs.existsSync(path.join(root, dir, "package.json")));
}).sort();
const catalogDeps = new Set(Object.keys(required));
const workspacePackageNames = new Set();
for (const dir of dirs) {
  const pkg = JSON.parse(fs.readFileSync(path.join(root, dir, "package.json"), "utf8"));
  if (pkg.name) workspacePackageNames.add(pkg.name);
}
const rootDeps = {...(rootPackage.dependencies ?? {}), ...(rootPackage.devDependencies ?? {})};
for (const name of catalogDeps) if (name in rootDeps && rootDeps[name] !== "catalog:")
  errors.push(`root package: ${name} must use catalog:`);
for (const [name, version] of Object.entries(rootDeps))
  if (name.startsWith("@itunda/") && !version.startsWith("workspace:"))
    errors.push(`root package: ${name} must use workspace:`);
for (const dir of dirs) {
  const file = path.join(root, dir, "package.json");
  if (!fs.existsSync(file)) continue;
  const pkg = JSON.parse(fs.readFileSync(file, "utf8"));
  const deps = {...(pkg.dependencies ?? {}), ...(pkg.devDependencies ?? {})};
  for (const name of catalogDeps) if (name in deps && deps[name] !== "catalog:")
    errors.push(`${dir}: ${name} must use catalog:`);
  for (const [name, version] of Object.entries(deps))
    if (name.startsWith("@itunda/") && !version.startsWith("workspace:"))
      errors.push(`${dir}: ${name} must use workspace:`);
  if (fs.existsSync(path.join(root, dir, "package-lock.json")))
    errors.push(`${dir}: remove package-lock.json; this is an active Yarn workspace`);

  // Every imported active Yarn workspace must be a direct dependency of the importer.
  // This prevents accidental success through transitive hoisting/PnP visibility.
  const sourceExtensions = new Set([".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".css"]);
  const sourceFiles = [];
  const walk = (current) => {
    if (!fs.existsSync(current)) return;
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      if (["node_modules", "dist", "build", "coverage", ".turbo"].includes(entry.name)) continue;
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (sourceExtensions.has(path.extname(entry.name))) sourceFiles.push(full);
    }
  };
  walk(path.join(root, dir, "src"));
  const importedWorkspacePackages = new Set();
  for (const sourceFile of sourceFiles) {
    const source = fs.readFileSync(sourceFile, "utf8");
    const importPattern = /(?:from\s+|import\s*\(\s*|require\(\s*|@import\s+)["'](@itunda\/[^"']+)["']/g;
    for (const match of source.matchAll(importPattern)) {
      const imported = match[1];
      if (workspacePackageNames.has(imported) && imported !== pkg.name) importedWorkspacePackages.add(imported);
    }
  }
  for (const imported of importedWorkspacePackages) {
    if (!Object.prototype.hasOwnProperty.call(deps, imported)) errors.push(`${dir}: ${imported} is imported from source but is not declared as a direct workspace dependency`);
    else if (!String(deps[imported]).startsWith("workspace:")) errors.push(`${dir}: ${imported} must use workspace: for a direct workspace dependency`);
  }
}
if (errors.length) { console.error(errors.join("\n")); process.exit(1); }
console.log(`Monorepo governance audit passed: ${dirs.length} active JS workspaces checked; direct workspace imports verified.`);
