# MEDK-39 · Guía de identidad, valores definitivos y DESIGN.md

Notas de despliegue de la tarjeta MEDK-39 (épica MEDK-33). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Fuente autoalojada en web**: Figtree (SIL OFL 1.1) en `web/src/assets/fonts/` (dos `.woff2` variables, ~30 KB). El build la copia con hash a `/media/`, que nginx ya sirve con caché de un año (MEDK-37). No hay CDN ni dominio nuevo.
- **Fuente empaquetada en la app**: `movil/assets/fonts/` (TTF 400, 500 y 600) declarados en `movil/pubspec.yaml`. Aumenta el tamaño de la app en ~170 KB.
- **Tokens definitivos**: cambian los colores, radios, sombras y la familia tipográfica en las salidas generadas (`web/src/styles/tokens.css`, `movil/lib/design_system/tokens.g.dart`). Cambia el aspecto, no el comportamiento.

No hay servicios, dependencias de ejecución, variables, secretos, puertos ni dominios nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada. `npm start` en `web/` y `flutter pub get` en `movil/` toman la fuente. |
| Pruebas | Nada obligatorio. Si se sirven capturas visuales de referencia, regenerarlas (`npm run test:visual:docker` en `web/`). |
| Producción | Nada. Publicar la web y una versión nueva de la app como siempre. |

## Para «Despliegue» al preparar el release

- **Fila en «Cambios»**: «Identidad visual definitiva: tokens nuevos y fuente Figtree autoalojada en web (`/media/`) y empaquetada en la app | MEDK-39».
