import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

const production = process.env.NODE_ENV === 'production'

export default defineConfig({
  base: production ? '/remotes/maps/' : '/',
  plugins: [
    react(),
    // @ts-ignore
    federation({
      name: 'maps_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './MapView': './src/MapView.tsx',
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
    port: Number(process.env.VITE_PORT ?? 5006),
    cors: true
  },
  preview: {
    host: '0.0.0.0',
    port: Number(process.env.VITE_PORT ?? 5006),
    cors: true
  }
})
