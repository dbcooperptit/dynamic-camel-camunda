/// <reference types="vite/client" />

declare interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_TENANT_ID?: string;
  readonly VITE_API_KEY?: string;
}

declare interface ImportMeta {
  readonly env: ImportMetaEnv;
}
