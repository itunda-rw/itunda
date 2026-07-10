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
      name: 'host_app',
      remotes: {
        kyc_mfe: 'http://localhost:5001/assets/remoteEntry.js',
        bank_mfe: 'http://localhost:5002/assets/remoteEntry.js',
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
    port: 5000
  }
})
