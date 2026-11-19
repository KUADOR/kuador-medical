# Container image for the web piece (used by the "full" compose profile).
# Serves the Angular build and proxies /api/ to the api service on the same origin.
FROM node:24-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build \
    && mkdir -p /out \
    && if [ -d dist/*/browser ]; then cp -r dist/*/browser/. /out/; else cp -r dist/*/. /out/; fi

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=0755 docker-entrypoint.d/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --chmod=0755 docker-entrypoint.d/50-app-links.sh /docker-entrypoint.d/50-app-links.sh
# The nginx user (uid 101) must be able to rewrite config.json (API_BASE_URL) and the
# .well-known association files (APP_LINKS_*) at start.
COPY --from=build --chown=101:0 /out/ /usr/share/nginx/html/
EXPOSE 8080
