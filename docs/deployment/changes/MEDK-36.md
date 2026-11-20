# MEDK-36 · CI: deriva de tokens, movil, visuales y Lighthouse

Notas de despliegue de la tarjeta MEDK-36 (épica MEDK-33). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`, sección «CI/CD → Integración continua») y se borra este archivo.

## Qué cambia

Solo CI y herramientas de desarrollo: no hay servicios, puertos, dominios, variables ni secretos nuevos, y nada cambia en el despliegue de pruebas o producción.

Checks nuevos en `.github/workflows/ci.yml`:

| Check | Corre si cambia | Qué hace |
|---|---|---|
| `design` | `design/**`, `web/src/styles/tokens.css`, `movil/lib/design_system/tokens.g.dart` (y los archivos comunes del CI) | Node.js 24, `npm ci`, `npm test` (pruebas del generador) y `npm run build` (contrato y contraste AA); falla si `tokens.css` o `tokens.g.dart` difieren de lo regenerado, es decir, si hay deriva de tokens o alguien editó una salida a mano |
| `web-visual` | `web/**`, `contracts/**` | Playwright `1.65.1` (versión fija en `package.json`) en la imagen `mcr.microsoft.com/playwright:v1.65.1-noble`, corre `npm run test:visual`, la suite de MEDK-37 (`e2e/visual/`): servidor de desarrollo con API simulada y solo datos sintéticos, 18 pruebas con 12 capturas (login, login con errores y foco, estado, estado degradado; claro, oscuro y móvil) y axe-core. Esta tarea agrega sus líneas base Linux, que MEDK-37 no había versionado. Si falla, sube el informe y las capturas propuestas como artefacto `web-visual-linux` |
| `web-lighthouse` | `web/**`, `contracts/**` | `npm run build` y Lighthouse CI (`@lhci/cli` `0.16.1`, perfil móvil, 3 corridas) sobre `/` y `/login`; falla si la mediana de LCP es ≥ 2,5 s (medido en local: unos 0,6 s). Rendimiento y accesibilidad < 0,9 solo avisan. Sube los informes como artefacto `web-lighthouse` |

Cambio en el check `movil`: `flutter test` incluye ahora los goldens (`test/goldens/`, etiqueta `golden`). Las líneas base son renderizados de Linux con Flutter `3.47.9`. Si falla, el job los vuelve a renderizar y los sube con los diffs como artefacto `movil-goldens-linux`. En macOS y Windows `flutter test` salta los goldens.

Herramientas locales nuevas (necesitan Docker):

- `web`: `npm run test:visual:docker` (de MEDK-37) regenera las líneas base en la misma imagen que el CI; `npm run lighthouse` (nuevo) usa el Chrome local, después de `npm run build`.
- `movil`: `scripts/update-goldens.sh` regenera los goldens en Linux con Flutter `3.47.9` (no hay imagen publicada de esa versión: el SDK se clona una vez en el volumen de Docker `medical-kuador-flutter-sdk`).

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio. Para revisar cambios visuales: Docker y los comandos de arriba. |
| CI (repo) | Agregar `design`, `web-visual` y `web-lighthouse` a los checks requeridos de `develop` y `main` cuando el repo tenga protección de ramas (sigue pendiente lo indicado en «Despliegue»). |
| Pruebas | Nada. |
| Producción | Nada. |

## Pendiente en el tablero

- La pieza `movil` no tiene comandos de build, prueba ni ejecución configurados. Hay que cargarlos en el tablero: build `flutter build apk`, prueba `flutter analyze && flutter test`, ejecución `flutter run`.
