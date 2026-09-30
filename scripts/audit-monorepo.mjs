import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const catalog = fs.readFileSync(path.join(root, ".yarnrc.yml"), "utf8");
const required = {
  react: "^19.2.7", "react-dom": "^19.2.7", typescript: "~6.0.2",
  "@types/react": "^19.2.17", "@types/react-dom": "^19.2.3", "@types/node": "^24.13.2",
  oxlint: "^1.71.0", vite: "^8.1.1", "@vitejs/plugin-react": "^6.0.3",
  "@originjs/vite-plugin-federation": "^1.4.1"
};
const errors = [];
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
