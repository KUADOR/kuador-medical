// Build-time defaults. The runtime public/config.json (API_BASE_URL) overrides apiBaseUrl,
// so one build serves every environment.
export const environment = {
  /** Same origin: nginx forwards /api/ to the api in the container image. */
  apiBaseUrl: '/api/v1',
};
