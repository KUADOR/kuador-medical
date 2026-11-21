# MEDK-74 · Flujos web del OWNER

Notas de despliegue de la tarjeta MEDK-74 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **web, dependencia nueva:** `qrcode-generator` (MIT, sin dependencias) para dibujar en el navegador el QR del enrolamiento TOTP. El secreto nunca sale del navegador hacia otro servicio. Se instala con `npm ci` como el resto; no cambia la imagen ni el stack.
- **web, rutas nuevas:**
  - `/owner-invitation#t=<token>`: destino del correo de invitación del OWNER. Es la ruta que usan las pruebas del correo de invitación (MEDK-64); MEDK-67 debe armar el enlace como `MEDK_WEB_BASE_URL` + `/owner-invitation#t=<token>`. El dominio público de la web la sirve como cualquier otra ruta de la SPA.
  - `/two-factor/enroll` y `/two-factor/verify`: segundo factor TOTP obligatorio del OWNER.
  - `/data-export`: exportación de datos de la clínica (permiso `tenant.data.export`).
- **web, consume** `/api/v1/public/owner-invitations/*`, `/api/v1/auth/two-factor/*`, `/api/v1/tenant/subscription` y `/api/v1/tenant/exports/**` (contrato `contracts/platform.openapi.yaml`). Hasta que MEDK-67, MEDK-68 y MEDK-70 los implementen en la api, solo funcionan contra el mock.
- **web, solo desarrollo local:** el proxy de mocks (`web/proxy.mock.conf.json`) envía esas mismas rutas al mock de plataforma en el puerto **4012** (lo agrega MEDK-72).

No hay servicios, dominios, puertos, variables ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | `cd web && npm install` (dependencia nueva). Para probar sin api, `npm run dev:mock`. |
| Pruebas | Nada adicional: el build de la web instala la dependencia. Verificar que `/owner-invitation` responde con la SPA (fallback a `index.html`). |
| Producción | Igual que pruebas. |
