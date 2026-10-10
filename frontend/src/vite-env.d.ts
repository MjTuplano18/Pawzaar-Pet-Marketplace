/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the Pawzaar API. Empty in dev (Vite proxies /api to localhost:8080). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
