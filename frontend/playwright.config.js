import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  use: { baseURL: 'http://localhost:5193', channel: process.env.PW_CHANNEL || 'msedge', headless: true, trace: 'retain-on-failure' },
  webServer: { command: 'node node_modules/vite/bin/vite.js --port 5193 --strictPort', url: 'http://localhost:5193', reuseExistingServer: false },
  reporter: 'list'
})
