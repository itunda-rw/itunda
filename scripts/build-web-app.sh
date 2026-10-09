#!/usr/bin/env bash
set -euo pipefail

APP="${1:-}"

corepack enable
yarn install --immutable

case "$APP" in
  customer)
    yarn workspace host-app build
    yarn workspace maps-mfe build
    yarn workspace bank-mfe build
    yarn workspace kyc-mfe build

    rm -rf services/micro-frontends/host-app/dist/remotes
    mkdir -p services/micro-frontends/host-app/dist/remotes
    cp -R services/micro-frontends/maps-mfe/dist services/micro-frontends/host-app/dist/remotes/maps
    cp -R services/micro-frontends/bank-mfe/dist services/micro-frontends/host-app/dist/remotes/bank
    cp -R services/micro-frontends/kyc-mfe/dist services/micro-frontends/host-app/dist/remotes/kyc
    test -s services/micro-frontends/host-app/dist/index.html
    test -s services/micro-frontends/host-app/dist/remotes/maps/index.html
    test -s services/micro-frontends/host-app/dist/remotes/bank/index.html
    test -s services/micro-frontends/host-app/dist/remotes/kyc/index.html
    echo "Customer web app ready: services/micro-frontends/host-app/dist"
    ;;
  business)
    yarn workspace merchant-mfe build
    test -s services/micro-frontends/merchant-mfe/dist/index.html
    echo "Business web app ready: services/micro-frontends/merchant-mfe/dist"
    ;;
  *)
    echo "Usage: $0 {customer|business}" >&2
    exit 2
    ;;
esac
