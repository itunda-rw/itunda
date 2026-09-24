import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

const production = process.env.NODE_ENV === 'production'
const remoteHost = process.env.VITE_REMOTE_HOST ?? 'localhost'
const remoteBase = process.env.VITE_REMOTE_BASE ?? (production ? '/app/remotes' : `http://${remoteHost}:5000`)

const remote = (name: string, port: number) =>
  production
    ? `${remoteBase}/${name}/assets/remoteEntry.js`
    : `http://${remoteHost}:${port}/assets/remoteEntry.js`

export default defineConfig({
  base: production ? '/app/' : '/',
  plugins: [
    react(),
    // @ts-ignore -- vite-plugin-federation's CJS/ESM type interop is runtime-safe.
    federation({
      name: 'host_app',
      remotes: {
        kyc_mfe: remote('kyc', 5001),
        bank_mfe: remote('bank', 5002),
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
    port: Number(process.env.VITE_PORT ?? 5000)
  }
})
