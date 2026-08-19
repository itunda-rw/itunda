import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

// Real maps-mfe split (2026-08-19) -- MapView.tsx moved out of this package into its
// own federated remote (see maps-mfe/vite.config.ts's own exposes block). Defaults to
// the local dev server's own port (matches maps-mfe's own default VITE_PORT); set
// VITE_MAPS_MFE_URL to the real deployed cluster URL (see infra/k8s/private-cloud/
// maps-mfe.yaml) for anything other than local dev.
const mapsMfeUrl = process.env.VITE_MAPS_MFE_URL ?? 'http://localhost:5006/assets/remoteEntry.js'

export default defineConfig({
  plugins: [
    react(),
    // @ts-ignore
    federation({
      name: 'bank_mfe',
      filename: 'remoteEntry.js',
      remotes: {
        maps_mfe: mapsMfeUrl,
      },
      exposes: {
        './BankDashboard': './src/BankDashboard.tsx',
        './I18nProvider': './src/RemoteI18nProvider.tsx',
      },
      shared: ['react', 'react-dom']
    })
  ],
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  },
  server: {
    host: process.env.VITE_DEV_HOST ?? 'localhost',
    port: Number(process.env.VITE_PORT ?? 5002),
    cors: true
  }
})
