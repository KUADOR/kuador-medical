# MEDK-70 · Exportación y baja definitiva

Notas de despliegue de la tarjeta MEDK-70 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Migración Flyway `V13__platform_tenant_export_and_purge.sql`**: tablas `tenant_export` (exportaciones de datos de una clínica, con el archivo en `bytea`) y `tenant_purge_request` (solicitud y confirmación de la baja definitiva), nuevas acciones de auditoría (`TENANT_EXPORT_REQUESTED`, `TENANT_EXPORT_DOWNLOADED`, `TENANT_PURGE_REQUESTED`, `TENANT_PURGE_CONFIRMED`, `TENANT_PURGE_CANCELLED`, `TENANT_PURGED`) y el permiso `tenant.data.export` para los roles OWNER y ADMIN de las clínicas que ya existen.
- **Nueva ruta pública de la api** `GET /api/v1/public/tenant-exports/download?grant=…`: es el `downloadUrl` de una exportación lista. El `grant` está firmado (HMAC-SHA256) y vale 5 minutos. La URL se arma con `KUADOR_WEB_BASE_URL` (variable existente): debe apuntar al dominio de la web que reenvía `/api/` a la api.
- **`web/nginx.conf`**: nueva `location ^~ /api/v1/public/tenant-exports/` igual al proxy de `/api/`, pero con `access_log off`, para que el `grant` (credencial de 5 minutos) no quede en el log de acceso. Cada descarga queda auditada en la api. Si delante de la web hay otro proxy, balanceador o CDN, tampoco debe registrar la query de esa ruta ni cachearla (la respuesta ya trae `Cache-Control: no-store`).
- **Secreto opcional nuevo `KUADOR_EXPORT_SIGNING_KEY`** (≥ 32 bytes): firma los enlaces de descarga. Si queda vacío, la clave se deriva de `KUADOR_JWT_SECRET` (HMAC con una etiqueta propia), así que no es obligatorio. Al rotarlo, o al rotar `KUADOR_JWT_SECRET` sin él, solo dejan de valer los enlaces vivos (5 minutos); el cliente pide otro.
- **Nuevo job programado dentro de la api** `platform-tenant-export-cleanup` (ShedLock, cada hora en el minuto 45 UTC): borra el archivo de las exportaciones vencidas (7 días). No requiere configuración; en pruebas automáticas está desactivado (`cleanup-cron: "-"`).
- **Purga**: la confirmación borra o anonimiza en la base los datos de la clínica (contacto de la clínica y del OWNER, exportaciones) y la marca purgada; la historia clínica no se purga hasta la validación legal (ADR-018). Los respaldos con esos datos desaparecen por la retención de 30 días de `scripts/db-backup.sh` (MEDK-71): no hay que hacer nada extra.

No hay servicios, dependencias, puertos ni dominios nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio; la migración corre sola al arrancar. Con `npm start` el proxy de desarrollo ya reenvía `/api`. |
| Pruebas | 1) Comprobar que `KUADOR_WEB_BASE_URL` es el dominio de la web de pruebas. 2) Opcional: crear `KUADOR_EXPORT_SIGNING_KEY` en el gestor de secretos (`openssl rand -base64 48`). 3) Desplegar la imagen nueva de `web` (cambia `nginx.conf`). 4) Verificar (ver abajo). |
| Producción | Igual que pruebas. Revisar que ningún proxy, balanceador o CDN delante de la web registre la query ni cachee `/api/v1/public/tenant-exports/`. |

## Verificación

1. Como OWNER: `POST /api/v1/tenant/exports` con `{"format":"JSON"}` responde `202` con `status: READY`; `GET /api/v1/tenant/exports/{id}` trae un `downloadUrl` que empieza por `KUADOR_WEB_BASE_URL`; abrirlo en el navegador sin sesión descarga el archivo. Pasados 5 minutos el mismo enlace responde `404`.
2. En el log de acceso de nginx no aparecen solicitudes a `/api/v1/public/tenant-exports/`.
3. En `audit_event` hay `TENANT_EXPORT_REQUESTED` y `TENANT_EXPORT_DOWNLOADED` con el actor.

## Para «Despliegue» al preparar el release

- **Secretos** (fila nueva): `KUADOR_EXPORT_SIGNING_KEY` | api | pruebas, producción | gestor de secretos | sí | Opcional. Firma los enlaces de descarga de las exportaciones (≥ 32 bytes). Vacío: derivada de `KUADOR_JWT_SECRET`.
- **Variables** (fila de `KUADOR_WEB_BASE_URL`): agregar «también arma el `downloadUrl` de las exportaciones (MEDK-70)».
- **Jobs**: `platform-tenant-export-cleanup`, cada hora (minuto 45 UTC), borra archivos de exportaciones vencidas.
- **Fila en «Cambios»**: «Exportación de datos de la clínica (JSON/CSV, enlace firmado de 5 minutos) y baja definitiva en dos pasos con RUC; migración V13; `nginx.conf` sin log de acceso para las descargas; secreto opcional `KUADOR_EXPORT_SIGNING_KEY` | MEDK-70».
