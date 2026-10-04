import { defineConfig } from '@playwright/test'
import base from './playwright.config.js'

// Opt-in: this suite leaves an explicitly named simulated publication in the local DB.
export default defineConfig({ ...base, testDir: './e2e-full', outputDir: './test-results-full', timeout: 120000 })
