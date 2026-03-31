import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    port: 5173,
    host: '127.0.0.1',
  },
  build: {
    target: 'chrome108',
    sourcemap: false,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          three: ['three', '@react-three/fiber', '@react-three/drei'],
          animation: ['framer-motion', 'gsap'],
          emoji: ['@emoji-mart/react', '@emoji-mart/data'],
        },
      },
    },
  },
});
