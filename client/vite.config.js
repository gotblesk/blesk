import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    port: 5173,
    host: '127.0.0.1',
  },
  esbuild: {
    // drop console disabled — causes TDZ in production builds
  },
  build: {
    target: 'chrome108',
    sourcemap: false,
    chunkSizeWarningLimit: 1000,
    minify: false, // minification (terser/esbuild) causes TDZ across circular deps in react/three/framer-motion
  },
});
