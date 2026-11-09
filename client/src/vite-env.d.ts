/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_TARGET?: string;
  readonly VITE_BASE_DOMAIN?: string;
  readonly VITE_DEFAULT_TENANT?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
