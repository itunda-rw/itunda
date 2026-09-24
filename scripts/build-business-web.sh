#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

corepack yarn install --immutable
corepack yarn workspace merchant-mfe build

rm -rf business/app
mkdir -p business/app
cp -R services/micro-frontends/merchant-mfe/dist/. business/app/

printf 'Itunda Business web app assembled at /app\n'
