/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Khoá publishable Stripe (pk_test_... / pk_live_...). Công khai, an toàn để lộ ở FE. */
  readonly VITE_STRIPE_PUBLISHABLE_KEY: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
