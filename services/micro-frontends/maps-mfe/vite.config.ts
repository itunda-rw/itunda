import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    // @ts-ignore -- same CJS/ESM default-export interop mismatch bank-mfe's and
    // kyc-mfe's vite.config.ts already work around; federation() is callable
    // at runtime.
    federation({
      name: 'maps_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './MapView': './src/MapView.tsx',
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
    port: Number(process.env.VITE_PORT ?? 5006),
    cors: true
  },
  preview: {
    host: '0.0.0.0',
    port: Number(process.env.VITE_PORT ?? 5006),
    cors: true
  }
})
