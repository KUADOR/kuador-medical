# MEDK-37 · Web: tema base y guardas de estilo

Notas de despliegue de la tarjeta MEDK-37 (épica MEDK-33). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Tema base de la web:** la web carga `src/styles/tokens.css` (generado en MEDK-35), `fonts.css` y `base.css`. Tema claro y oscuro según la preferencia del sistema (`prefers-color-scheme`). No hay archivos nuevos servidos: los estilos siguen dentro del bundle `styles-*.css`.
- **nginx (`web/nginx.conf`):** nueva ruta `/media/` con caché de un año (`immutable`) y sin fallback a la SPA. Ahí deja el build las fuentes autoalojadas y otros archivos referenciados desde los estilos, con hash en el nombre. Hoy no hay ninguna fuente, porque la familia la fija la guía de identidad (MEDK-39). Ninguna fuente sale de un CDN externo.
- **Guardas en el build:** `npm run build` y `npm test` ejecutan primero `npm run lint` (Stylelint + angular-eslint). Un color o un tamaño en px escrito a mano hace fallar el build. `npm test` también corre `npm run test:guards`.
- **Pruebas visuales (Playwright):** `npm run test:visual` y `npm run test:visual:docker`. Las capturas de referencia se generan en el contenedor `mcr.microsoft.com/playwright:v1.65.1-noble`. Su job de CI corresponde a MEDK-36: antes, `npx playwright install --with-deps chromium`.

No hay servicios, puertos, dominios, variables ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | `cd web && npm install`. Para las pruebas visuales, la primera vez: `npx playwright install chromium`. |
| Pruebas | Reconstruir la imagen de `web`, que incluye el `nginx.conf` nuevo. |
| Producción | Reconstruir la imagen de `web`, que incluye el `nginx.conf` nuevo. Si hay un CDN o un proxy delante, respetar el `Cache-Control` de `/media/`. |
