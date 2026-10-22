/**
 * Shared setup for every test file (configured in vite.config.ts -> test.setupFiles).
 * - jest-dom matchers (toHaveTextContent, toBeInTheDocument, ...)
 * - React Testing Library auto-cleanup runs between tests because vitest globals
 *   expose afterEach to RTL's automatic cleanup hook.
 */
import '@testing-library/jest-dom/vitest'
