import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

const production = process.env.NODE_ENV === 'production'

export default defineConfig({
  base: production ? '/app/remotes/kyc/' : '/',
  plugins: [
    react(),
    // @ts-ignore -- vite-plugin-federation's CJS/ESM type interop is runtime-safe.
    federation({
      name: 'kyc_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './KycDashboard': './src/KycDashboard.tsx',
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
    port: Number(process.env.VITE_PORT ?? 5001),
    cors: true
  }
})
