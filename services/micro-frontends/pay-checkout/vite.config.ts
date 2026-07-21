import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Real "Pay with itunda" hosted checkout page (2026-07-21) -- see
// PaymentsApiController.kt's own doc comment for the full account of the real Toss
// Payments feature this mirrors. Deliberately no module-federation plugin, unlike
// every other micro-frontend here -- this page is never embedded as a remote inside
// host-app; it's the standalone page a merchant's OWN website redirects a customer's
// browser to, matching how Toss Payments' own hosted checkout page works.
export default defineConfig({
  plugins: [react()],
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false,
  },
  server: {
    port: 5005,
    cors: true,
  },
})
