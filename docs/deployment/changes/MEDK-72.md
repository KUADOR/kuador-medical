# MEDK-72 · Panel de planes, tarifas y ajustes

Notas de despliegue de la tarjeta MEDK-72 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **web, solo desarrollo local:** tercer mock Prism, del contrato `contracts/platform.openapi.yaml`, en el puerto local **4012** (`npm run mock:platform`, incluido en `npm run dev:mock`). El proxy de mocks (`web/proxy.mock.conf.json`) envía `/api/v1/platform/*` a 4012, salvo `/api/v1/platform/permissions`, que sigue en 4010. En «Despliegue» (§ Entorno local) hay que decir «puertos 4010, 4011 y 4012».
- **web, rutas nuevas** (solo SUPER_ADMIN con los permisos `platform.plans.manage` y `platform.settings.manage`, que agrega MEDK-65): `/platform/plans`, `/platform/plans/new`, `/platform/plans/{id}` y `/platform/settings`. Consumen `/api/v1/platform/plans/**`, `/api/v1/platform/catalog/**` y `/api/v1/platform/settings`; hasta que MEDK-65 los implemente en la api, solo funcionan contra el mock.

No hay servicios, dominios, variables ni secretos nuevos, y nada cambia en las imágenes.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio. Para trabajar sin api, `cd web && npm run dev:mock` levanta también el mock en 4012 (debe estar libre). |
| Pruebas | Nada. |
| Producción | Nada. |
