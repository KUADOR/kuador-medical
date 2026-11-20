# MEDK-35 · Contrato de tokens y generador CSS + Dart

Notas de despliegue de la tarjeta MEDK-35 (épica MEDK-33). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Carpeta nueva `design/`** (paquete Node de build, no se despliega): tokens de diseño en `design/tokens/` y generador con Style Dictionary (dependencia de desarrollo, versión fija). Necesita la misma versión de Node.js que `web`.
- **Archivos generados y versionados:** `web/src/styles/tokens.css` y `movil/lib/design_system/tokens.g.dart`. Todavía no los usa ninguna pantalla (lo hacen las tareas de tema base de web y Flutter), así que no cambia nada visible.
- **CI:** pendiente de la tarea de CI de la épica: correr `npm ci && npm run check && npm test` en `design/` (falla si las salidas están desactualizadas o un par no cumple contraste AA).

No hay servicios, puertos, dominios, variables ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio. Para cambiar tokens: `cd design && npm ci && npm run build`. |
| Pruebas | Nada. |
| Producción | Nada. |
