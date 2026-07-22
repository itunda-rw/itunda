import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  resolve: {
    alias: {
      // See src/shims/next-router.ts -- @toss/use-funnel@1.4.2 (its own source
      // marks this API deprecated) unconditionally imports next/router.js even
      // though this is a plain Vite SPA with no Next.js anywhere.
      'next/router.js': fileURLToPath(new URL('./src/shims/next-router.ts', import.meta.url)),
    },
  },
  plugins: [
    react(),
    // @ts-ignore -- same CJS/ESM default-export interop mismatch bank-mfe's
    // vite.config.ts already works around; federation() is callable at runtime.
    federation({
      name: 'kyc_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './KycDashboard': './src/KycDashboard.tsx',
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
