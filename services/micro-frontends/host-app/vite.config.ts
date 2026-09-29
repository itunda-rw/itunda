import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

const remoteHost = process.env.VITE_REMOTE_HOST ?? 'localhost'
const remoteBase = process.env.VITE_REMOTE_BASE ?? '/remotes'

const remote = (name: string) =>
  remoteBase.startsWith('http')
    ? `${remoteBase}/${name}/assets/remoteEntry.js`
    : `${remoteBase}/${name}/assets/remoteEntry.js`

export default defineConfig({
  base: '/',
  plugins: [
    react(),
    // @ts-ignore -- vite-plugin-federation's CJS/ESM type interop is runtime-safe.
    federation({
      name: 'host_app',
      remotes: {
        kyc_mfe: remote('kyc'),
        bank_mfe: remote('bank'),
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
    host: process.env.VITE_DEV_HOST ?? remoteHost,
    port: Number(process.env.VITE_PORT ?? 5000)
  }
})
