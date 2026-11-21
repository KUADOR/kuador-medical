# MEDK-48 · Cierre de la épica MEDK-33: compresión en nginx

Notas de despliegue de la tarjeta MEDK-48 (épica MEDK-33). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **nginx (`web/nginx.conf`):** compresión gzip para HTML, CSS, JS, JSON y SVG estáticos (`gzip_vary`, nivel 6, desde 1 KB, también detrás de un proxy o CDN). El JS inicial pasa de unos 560 KB a unos 150 KB transferidos y el LCP de `/login` en el perfil móvil de Lighthouse baja de 4,1 s a 2,1 s (SC-010, `specs/033-epica-nueva-identidad-visual-y-experienc/results.md`).
- **Respuestas de la api por `/api/`:** no se comprimen (`gzip off` en esa ruta), porque llevan tokens y datos personales.
- **App móvil:** se agregan las dependencias de desarrollo `integration_test` y `flutter_driver` (del SDK de Flutter) para la medición de rendimiento. No cambian la app publicada.

No hay servicios, puertos, dominios, variables ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada. Con `docker compose --profile full`, reconstruir `web` para tener la compresión. |
| Pruebas | Reconstruir la imagen de `web`. Verificar: `curl -sI -H 'Accept-Encoding: gzip' https://<host>/main-<hash>.js` responde `Content-Encoding: gzip`. |
| Producción | Reconstruir la imagen de `web`. Si hay un CDN o un proxy delante que ya comprime, no hace falta cambiarlo. Si quita `Accept-Encoding` o descomprime, configurarlo para que comprima o deje pasar la compresión, y respetar `Vary: Accept-Encoding`. |
