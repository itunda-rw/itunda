# Real production deployment for the Vite + React micro-frontends (2026-08-19) --
# previously NONE of these had a deploy story at all: "bank-mfe running standalone"
# meant a local `yarn dev` server talking to the real deployed backend, not anything
# actually served from the cluster. One shared multi-stage Dockerfile, selected
# per-package via the WORKSPACE build-arg, builds any of bank-mfe/kyc-mfe/
# merchant-mfe/ops-mfe/pay-checkout/maps-mfe (all Vite + React, all `yarn build` ->
# dist/). Deliberately excludes host-app -- see CLAUDE.md's own note that it's a
# mostly-unused 2-tab Module Federation demo shell, not a real product surface.
#
# COPY . . (repo root) mirrors services/backend/Dockerfile's own shape: this is a
# real Yarn 4 PnP workspace (see root .pnp.cjs/yarn.lock), so a workspace build needs
# the full monorepo tree for dependency resolution, same reason the Gradle
# multi-module backend build does. See this directory's own Dockerfile.dockerignore
# for what's actually excluded from that COPY (notably packages/saronite, the 2.5GB
# React Native mini-apps tree -- irrelevant to any web build and too large to include).
ARG WORKSPACE=bank-mfe

FROM node:22-alpine AS builder
ARG WORKSPACE
WORKDIR /app
COPY . .
RUN corepack enable && yarn install --immutable
RUN yarn workspace ${WORKSPACE} run build

FROM nginx:1.27-alpine
ARG WORKSPACE
COPY services/micro-frontends/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/services/micro-frontends/${WORKSPACE}/dist /usr/share/nginx/html
EXPOSE 80
