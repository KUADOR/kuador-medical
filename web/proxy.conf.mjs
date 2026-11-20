// Dev server proxy (`npm start`): the web calls same-origin `/api` and `/actuator`, and
// `ng serve` forwards them to the local api, so local development needs no CORS (MEDK-26).
// Set API_PROXY_TARGET to reach an api on another host or port.

export const DEFAULT_API_PROXY_TARGET = 'http://localhost:8080';

/** Builds the proxy configuration from the environment (pure, so it can be unit tested). */
export function createProxyConfig(env) {
  const configured = env.API_PROXY_TARGET?.trim();
  const target = (configured || DEFAULT_API_PROXY_TARGET).replace(/\/+$/, '');
  if (!/^https?:\/\/[^/]+/.test(target)) {
    throw new Error('API_PROXY_TARGET must be an http(s) URL, e.g. http://localhost:8080');
  }
  // Paths are absolute from the api origin (contracts/local-integration.openapi.yaml): no rewrite.
  const route = () => ({ target, secure: false, changeOrigin: true });
  return { '/api': route(), '/actuator': route() };
}

export default createProxyConfig(globalThis.process?.env ?? {});
