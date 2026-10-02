import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5010,
  },
  build: {
    target: 'esnext',
  },
});
