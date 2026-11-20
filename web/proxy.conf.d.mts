// Types of proxy.conf.mjs for the unit tests.
export interface ProxyRoute {
  target: string;
  secure: boolean;
  changeOrigin: boolean;
  pathRewrite?: Record<string, string>;
}

export type ProxyConfig = Record<'/api' | '/actuator', ProxyRoute>;

export declare const DEFAULT_API_PROXY_TARGET: string;
export declare function createProxyConfig(env: Record<string, string | undefined>): ProxyConfig;
declare const config: ProxyConfig;
export default config;
