# Itunda Web Deployment Boundaries

Web applications and public/information sites are separate deployment products.

## Cloudflare Pages projects

| Cloudflare Pages project | Product | Repository source | Build command | Output directory |
|---|---|---|---|---|
| `itunda-app` | Customer web application | `services/micro-frontends/host-app` + bank/KYC/maps remotes | `bash scripts/cloudflare-build-web-app.sh customer` | `services/micro-frontends/host-app/dist` |
| `itunda-business-app` | Merchant/business web application | `services/micro-frontends/merchant-mfe` | `bash scripts/cloudflare-build-web-app.sh business` | `services/micro-frontends/merchant-mfe/dist` |

Both projects are standalone web applications. They must not be treated as the public Itunda or public Business information sites.

## Public / information sites

These remain separate Cloudflare Pages products:

- Itunda public site
- Itunda Business public/information site
- Itunda Developers
- Itunda Tech Blog

The public sites should not be used as the deployment target for the authenticated customer or merchant applications.

## Customer application

`itunda-app` is a standalone root-hosted Vite application.

The production shell is served from `/`, not `/app/`.

Its Module Federation remotes are assembled into the same Pages artifact:

- `/remotes/maps/`
- `/remotes/bank/`
- `/remotes/kyc/`

This avoids requiring a separate Pages project just to serve the customer's internal remotes.

## Business application

`itunda-business-app` is the standalone merchant application built from `merchant-mfe`.

It is independent of the public `itunda-business` site. The public site explains the product; the app is where authenticated merchants operate their business.

## Deployment architecture

```
GitHub
  ├── public/information web source
  ├── customer web app source
  ├── merchant web app source
  └── native Android/iOS source

Cloudflare Pages
  ├── Itunda public site
  ├── Itunda Business public site
  ├── Itunda Developers
  ├── Itunda Tech Blog
  ├── itunda-app
  └── itunda-business-app

Native distribution
  ├── Android
  └── iOS
```

GitHub Pages is not part of this deployment model.

## Cloudflare configuration notes

For both Pages projects:

- connect the `itunda-rw/itunda` repository;
- use branch `agent/itunda-agent-network` for this deployment lane;
- keep the Pages output directory specific to the application above;
- do not point either project at the repository root as a generic static site;
- keep application environment variables separate from public-site variables.

Cloudflare dashboard project creation/verification is external account state; repository configuration is committed here so the intended boundaries remain explicit and reproducible.
