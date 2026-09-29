import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

const production = process.env.NODE_ENV === 'production'
const mapsMfeUrl = process.env.VITE_MAPS_MFE_URL ??
  (production ? '/remotes/maps/assets/remoteEntry.js' : 'http://localhost:5006/assets/remoteEntry.js')

export default defineConfig({
  base: production ? '/remotes/bank/' : '/',
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
