#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

corepack yarn install --immutable
corepack yarn workspace maps-mfe build
corepack yarn workspace bank-mfe build
corepack yarn workspace kyc-mfe build
corepack yarn workspace host-app build

rm -rf client/app
mkdir -p client/app/remotes/maps client/app/remotes/bank client/app/remotes/kyc
cp -R services/micro-frontends/maps-mfe/dist/. client/app/remotes/maps/
cp -R services/micro-frontends/bank-mfe/dist/. client/app/remotes/bank/
cp -R services/micro-frontends/kyc-mfe/dist/. client/app/remotes/kyc/
cp -R services/micro-frontends/host-app/dist/. client/app/

printf 'Itunda consumer web app assembled at /app\n'
