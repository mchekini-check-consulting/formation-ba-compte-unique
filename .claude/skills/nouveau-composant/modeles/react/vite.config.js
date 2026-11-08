import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: __PORT_LOCAL__,
    // En local : /api/<backend> relayé vers le backend lancé sur le poste (src/api.js)
    proxy: __PROXY_VITE__,
  },
  // Tests unitaires (vitest) : DOM simulé par jsdom
  test: {
    environment: 'jsdom',
    coverage: {
      include: ['src/**/*.{js,jsx}'], // pas les css / images importés
      exclude: ['src/main.jsx'], // démarrage : comme sonar.coverage.exclusions
      reporter: ['text', 'lcov'], // coverage/lcov.info lu par SonarQube
    },
  },
})
