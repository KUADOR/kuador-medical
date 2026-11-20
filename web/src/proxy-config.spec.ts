// MEDK-30: dev server proxy to the local api (plan decision 5).
import { DEFAULT_API_PROXY_TARGET, createProxyConfig } from '../proxy.conf.mjs';

describe('dev server proxy (proxy.conf.mjs)', () => {
  it('forwards /api and /actuator to http://localhost:8080 by default', () => {
    const config = createProxyConfig({});

    expect(DEFAULT_API_PROXY_TARGET).toBe('http://localhost:8080');
    expect(Object.keys(config).sort()).toEqual(['/actuator', '/api']);
    for (const route of Object.values(config)) {
      expect(route.target).toBe('http://localhost:8080');
      expect(route.secure).toBe(false);
      expect(route.changeOrigin).toBe(true);
      // Paths are absolute from the api origin: nothing is rewritten.
      expect(route.pathRewrite).toBeUndefined();
    }
  });

  it('takes the target from API_PROXY_TARGET without a trailing slash', () => {
    const config = createProxyConfig({ API_PROXY_TARGET: ' http://192.168.1.20:9090/ ' });

    expect(config['/api'].target).toBe('http://192.168.1.20:9090');
    expect(config['/actuator'].target).toBe('http://192.168.1.20:9090');
  });

  it('ignores an empty API_PROXY_TARGET', () => {
    expect(createProxyConfig({ API_PROXY_TARGET: '  ' })['/api'].target).toBe(
      DEFAULT_API_PROXY_TARGET,
    );
  });

  it('rejects a target that is not an http(s) URL', () => {
    expect(() => createProxyConfig({ API_PROXY_TARGET: 'localhost:8080' })).toThrow(
      /API_PROXY_TARGET/,
    );
  });
});
