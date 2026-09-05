import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    // @ts-ignore -- same CJS/ESM default-export interop mismatch bank-mfe's
    // vite.config.ts already works around; federation() is callable at runtime.
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
