# MEDK-75 · Integración web con la api real

## Qué cambia

- **web contra la api real:** las pantallas de plataforma (MEDK-72, MEDK-73) y los flujos del OWNER (MEDK-74) ya funcionan contra la api real, no solo contra el mock. Las notas de MEDK-72 y MEDK-74 que dicen «solo funcionan contra el mock» quedan superadas. Rutas consumidas: `/api/v1/platform/**`, `/api/v1/public/owner-invitations/*`, `/api/v1/auth/two-factor/*`, `/api/v1/tenant/subscription` y `/api/v1/tenant/exports/**`. El mock de Prism (puerto 4012) sigue sirviendo para trabajar sin api.
- **web, solo desarrollo local:** script nuevo `npm run test:integration` (Playwright, `web/playwright.integration.config.ts`). Recorre en el navegador todo el ciclo de una clínica contra la api real y Mailpit. Es opcional: no corre en `npm test` ni en CI. Variables `E2E_*` solo locales, descritas en `web/e2e/integration/README.md`. Las credenciales salen del `.env` local y nunca se escriben.

No hay servicios, dependencias, puertos, dominios, variables de despliegue ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio. Para el recorrido: `npx playwright install chromium` (una vez) y los pasos de `web/e2e/integration/README.md`. |
| Pruebas | Nada adicional. |
| Producción | Nada adicional. |
