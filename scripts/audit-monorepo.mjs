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
const dirs = [
  "packages/shared-utils","packages/design-tokens","packages/design-system-web","packages/itunda-pay-widget",
  "services/api-gateway","services/micro-frontends/bank-mfe","services/micro-frontends/host-app",
  "services/micro-frontends/kyc-mfe","services/micro-frontends/maps-mfe","services/micro-frontends/merchant-mfe",
  "services/micro-frontends/ops-mfe","services/micro-frontends/pay-checkout"
];
const catalogDeps = new Set(Object.keys(required));
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
}
if (errors.length) { console.error(errors.join("\n")); process.exit(1); }
console.log(`Monorepo governance audit passed: ${dirs.length} active JS workspaces checked.`);
