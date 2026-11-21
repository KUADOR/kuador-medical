# Cierre · MEDK-33 · Medición contra los criterios de éxito

Tarea: MEDK-48 (tarea 15 del plan). Cubre: Plan § Fases (Fase 5) y riesgos R6 y R7.
Fecha de medición: 20/11/2026. Base: `develop` en `a6ad2df` (MEDK-44, última tarea integrada) más esta rama.
Umbrales: los de `audit.md` § 6, aprobados por el Equipo Kuador en MEDK-34 (R7). Datos: solo ficticios (FR-014).

## 0. Resumen

| SC | Meta (versión aprobada) | Resultado | Estado |
|---|---|---|---|
| SC-001 | 0 estilos sueltos en las principales que existen | Web: lint de estilos y plantillas en verde. Flutter: guarda de estilos con 0 hallazgos en `lib/` | **Cumple** |
| SC-002 | AA en claro y oscuro | 60 pares de contraste AA (claro y oscuro) en el generador. axe en el navegador y `textContrastGuideline` en Flutter, en verde | **Cumple** (automático) |
| SC-003 | Un token se refleja en web y móvil | 94 pruebas del generador y verificación de deriva en CI, en verde | **Cumple** |
| SC-004 | Objetivos táctiles ≥ 44 px en las principales móviles | `androidTapTargetGuideline` / `iOSTapTargetGuideline` en verde en ingreso, registro y pantallas migradas | **Cumple** |
| SC-005 | Sin regresiones frente a la base (293 web + 114 Flutter) | Web: 415 pruebas + 20 de guardas. Flutter: 275 + 52 goldens (estos corren en Linux, job `movil`). Todo en verde | **Cumple** |
| SC-006 | Agendar en ≤ 4 pasos y < 60 s | La función no existe (R2) | **No aplica en esta épica** (aprobado en MEDK-34) |
| SC-007 | Cancelar o reprogramar en ≤ 3 pasos | La función no existe (R2) | **No aplica en esta épica** (aprobado en MEDK-34) |
| SC-008 | −20 % de tiempo humano en F1, F2 y F4; acciones F1 ≤ 3, F2 ≤ 3, F4 ≤ 1 | Acciones: F1 = 3, F2 = 3, F4 = **0** (antes 1). Tiempo humano: sin medir | **Parcial**: falta la sesión moderada (§ 4) |
| SC-009 | 90 % sin ayuda en F1, F2, F5 y F6 | Sin medir | **Pendiente**: sesión moderada (§ 4) |
| SC-010 | LCP de `/login` < 2,5 s (perfil móvil de Lighthouse, imagen de producción) | **2,1 s** con este PR (en `develop`: 4,1 s) | **Cumple con este PR** |
| SC-011 | Arranque < 2 s y sin tirones en los dispositivos de referencia | Medición lista y probada; falta el dispositivo físico | **Pendiente**: medición en dispositivo (§ 3) |
| SC-012 | Aprobación formal de la imagen y de la entrega de cada plataforma | — | **Pendiente**: la hace el Equipo Kuador |

**Lectura.** Los criterios automáticos se cumplen. Para SC-010 hizo falta corregir el servidor web, y esa corrección va en este PR (§ 2). Quedan cuatro pasos que dependen de personas o de equipos físicos: la sesión moderada (SC-008 en tiempo y SC-009), la medición en dispositivos (SC-011) y la aprobación formal (SC-012). Son condiciones del cierre, no preguntas.

## 1. Pruebas, guardas y CI

| Verificación | Comando | Resultado (20/11/2026) |
|---|---|---|
| Web: lint, guardas y pruebas | `cd web && npm test -- --watch=false` | Lint en verde; guardas: 3 archivos y 20 pruebas; unitarias: 49 archivos y 415 pruebas |
| Web: build | `cd web && npm run build` | Compila. **Aviso de presupuesto:** bundle inicial de 567,5 kB frente a 500 kB (ver § 5) |
| Flutter: análisis | `cd movil && flutter analyze` | Sin problemas |
| Flutter: pruebas | `cd movil && flutter test` | 275 en verde; 52 goldens omitidos en macOS. En Linux (job `movil` de CI) corren y están en verde |
| Tokens | `cd design && npm test && npm run check` | 94 pruebas; contraste: 60 verificaciones AA en claro y oscuro |
| Lighthouse CI | `cd web && npm run lighthouse` | `/` y `/login`: LCP de 0,6 s, rendimiento 1,0 y accesibilidad 1,0 (mediana de 3) |

**CI en `develop`.** Todos los jobs están en verde en su última corrida:

- `web`, `web-visual` y `web-lighthouse`: `a6ad2df`.
- `movil`: `5810933`.
- `design`, `contract` y `api`: `bfbabcd`.

Cada job corre solo cuando cambian sus archivos. Este PR vuelve a correr `web`, `web-visual`, `web-lighthouse` y `movil`.

**Hallazgo sobre Lighthouse CI.** El job mide el build con el servidor estático de LHCI, que **sí comprime** (el JS viaja en 151 kB). Por eso dio 0,6 s mientras la imagen de producción daba 4,1 s. No protege SC-010 tal como se aprobó (sobre la imagen de producción). Se propone, en una tarjeta de DEVOPS, que el job mida la imagen de `web` (nginx). No se cambia aquí.

## 2. SC-010 · LCP web

Medición: Lighthouse 12.8.2 sobre `/login` (`/` redirige ahí sin sesión), en la imagen de producción construida con el `Dockerfile` de `web`. Tres corridas por caso; las tres coinciden.

| Imagen | Perfil | LCP | FCP | TBT | CLS | Puntaje |
|---|---|---|---|---|---|---|
| Línea base Fase 0 (`1fc8df1`) | Móvil | 3,7 s | 3,3 s | 0 ms | 0 | 0,82 |
| `develop` (`a6ad2df`), sin compresión | Móvil | **4,1 s** | 3,9 s | 5–16 ms | 0 | 0,78 |
| `develop` (`a6ad2df`), sin compresión | Escritorio | 0,80 s | 0,72 s | 0 ms | 0 | 0,99 |
| **Esta rama (gzip en nginx)** | Móvil | **2,1 s** | 1,8 s | 4–6 ms | 0 | 0,98 |
| **Esta rama (gzip en nginx)** | Escritorio | 0,48 s | 0,40 s | 0 ms | 0 | 1,00 |

**Causa** (la misma de `audit.md` § 4.1, propuesta 4, que no tenía tarjeta). nginx entregaba `main-*.js` sin comprimir:

- Sin comprimir: 562 kB. Con el rediseño creció desde 519 kB.
- Con gzip: 150 kB.

**Corrección en este PR** (`web/nginx.conf`):

- gzip para HTML, CSS, JS, JSON y SVG estáticos, también detrás de un proxy.
- Sin compresión en `/api/`, porque esas respuestas llevan tokens y datos personales.

Nota de despliegue: `docs/deployment/changes/MEDK-48.md`.

Repetir:

```bash
cd web && docker build -t medk-web . && docker run --rm -d --add-host api:127.0.0.1 -p 18480:8080 medk-web
npx lighthouse@12 http://localhost:18480/login --only-categories=performance        # y --preset=desktop
```

## 3. SC-011 · Arranque y fluidez de Flutter (R6)

Dispositivos de referencia: los de `audit.md` § 4.2.

- **Android:** Galaxy A14/A15 o equivalente.
- **iOS:** iPhone 11 o SE (3.ª generación).

**Qué se agregó en este PR.**

- `movil/integration_test/performance_test.dart` hace dos mediciones con la api simulada y datos ficticios:
  - tiempo hasta el inicio del paciente con una sesión guardada;
  - `traceAction` sobre el desplazamiento de «Mis contactos autorizados», con 40 vínculos sintéticos.
- `movil/test_driver/perf_driver.dart` escribe el resumen de cuadros: cuadros fuera de presupuesto y percentiles.
- El procedimiento está en `movil/README.md` § «Performance (SC-011)».

**Qué se verificó aquí.** La medición corre de punta a punta en el simulador de iOS 26.3 (iPhone 17), en modo debug:

- 57 cuadros, 0 fuera de presupuesto de build y raster;
- build promedio de 3,5 ms (p90: 7,0 ms);
- inicio listo en 587 ms.

**Estos números no valen para SC-011.** El simulador solo corre en modo debug, sobre el hardware del Mac. Solo prueban que la herramienta funciona.

**Por qué no se midió.** En esta sesión no había ningún dispositivo físico:

- `flutter devices` no encontró ningún iPhone conectado ni en la red;
- no hay ningún Android conectado ni AVD creado.

La base de `audit.md` § 4.2 tampoco se midió: se aceptó como condición previa a la tarea 12, y esa condición no se cumplió.

**Condición del cierre.** Una persona con los dispositivos de referencia ejecuta los dos comandos de `movil/README.md` § «Performance (SC-011)» y completa esta tabla:

| Dispositivo | Arranque en frío, mediana de 5 (`timeToFirstFrameMicros`) | Cuadros fuera de presupuesto al desplazar (build / raster) | p90 de build | ¿Cumple? |
|---|---|---|---|---|
| Android de referencia | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] |
| iOS de referencia (cubre la compilación de iOS de R6) | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] |

Criterio: arranque < 2 s. «Sin tirones»: 0 cuadros fuera de presupuesto en el desplazamiento.

## 4. SC-008 (tiempo) y SC-009 · Sesión moderada

No se puede hacer sin participantes, y no se inventan resultados.

Además, **no consta una línea base de tiempo humano**. `audit.md` § 6 indicaba tomarla al inicio de la tarea 9 (MEDK-42), y no hay registro de que se haya tomado. Para que SC-008 siga siendo medible, propongo este protocolo:

1. **Versiones a comparar.** En la misma sesión se comparan dos builds:
   - antes: `develop` en `1fc8df1`, la base de la Fase 0;
   - después: `develop` con esta rama.
2. **Participantes.** Al menos 5 de personal (F1, F2, F4) y 5 pacientes (F5, F6). La mitad empieza por cada versión, para compensar el aprendizaje.
3. **Datos.** Usuarios semilla del perfil `local` y pacientes ficticios. No se graba audio ni video con datos personales. Solo se anotan el tiempo, si completó el flujo y si pidió ayuda.
4. **Medida.**
   - SC-008: mediana de tiempo por flujo, después contra antes; la meta es −20 %.
   - SC-009: porcentaje que completa sin ayuda; la meta es ≥ 90 %.

Tabla para completar:

| Flujo | Tiempo antes (mediana) | Tiempo después (mediana) | Δ | Sin ayuda (después) |
|---|---|---|---|---|
| F1 Ingreso del personal | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] |
| F2 Buscar y abrir ficha | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] |
| F4 Llegar a la búsqueda | [POR MEDIR] | [POR MEDIR] | [POR MEDIR] | — |
| F5 Ingreso del paciente (móvil) | — | — | — | [POR MEDIR] |
| F6 Autorregistro del paciente | — | — | — | [POR MEDIR] |

**Acciones (apoyo de SC-008, ya medido).** Lo verifican las pruebas unitarias:

| Flujo | Base | Ahora | Prueba |
|---|---|---|---|
| F1 | 3 | 3 | `web/src/app/features/auth/login/login.page.spec.ts` («keeps the flow in 3 actions») |
| F2 | 3 | 3 | `web/src/app/features/patients/patients-list/patients-list.page.spec.ts` («in 3 actions (baseline F2)») |
| F4 | 1 | **0** (el personal con `patients.read` entra directo a la búsqueda) | `login.page.spec.ts` |

## 5. Otras observaciones

- **Presupuesto del bundle.** El bundle inicial mide 567,5 kB, frente a un presupuesto de 500 kB; es solo un aviso y el build compila. Con gzip no afecta a SC-010. Conviene revisar la carga diferida de más rutas en una tarjeta aparte. No se cambia aquí.
- **`DESIGN.md`.** Sin cambios: el cierre no modificó tokens, componentes ni reglas.
- **Spec de MEDK-33.** Sigue pendiente reflejar en la spec los ajustes de SC aprobados en MEDK-34 (`audit.md` § 6). Le corresponde a Product Owner.
- **Despliegue.** Cambia: `docs/deployment/changes/MEDK-48.md`.
