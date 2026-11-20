// `ng serve` / development build. Relative on purpose: the dev server proxy (proxy.conf.mjs)
// forwards /api to API_PROXY_TARGET (http://localhost:8080 by default).
export const environment = {
  apiBaseUrl: '/api/v1',
};
