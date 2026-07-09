import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    // @ts-ignore
    federation({
      name: 'bank_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './BankDashboard': './src/BankDashboard.tsx',
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
    port: 5002,
    cors: true
  }
})
