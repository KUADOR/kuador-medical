# Migración visual restante de la app móvil · MEDK-47

Tarea 14 del plan de MEDK-33. Cubre: Plan § Resumen y § Verificación (principio V).

## Alcance

| Parte | Resultado |
|---|---|
| Resultados e historial del paciente | **No se hace en esta épica.** La pantalla no existe en la app ni tiene API (`audit.md` § 1.2, tipo F). Según la spec y el riesgo R2 (aprobado en MEDK-34), queda fuera. No se agregó código ni fixtures para ella. |
| Migración visual restante (pantallas V) | Hecha. Mismos flujos, mismos textos y mismas llamadas a la API; cambia el aspecto. |
| Conteo de estilos sueltos en `movil/lib` | **0**, verificado por la prueba de guarda, sin excepciones. |

## Qué cambió

- **Guarda de estilos** (`test/support/style_guard.dart`). Además de colores, `EdgeInsets` y radios, ahora falla ante tamaños y huecos numéricos (`SizedBox(height: 16)`, `spacing`, `size`, `maxWidth`, `dimension`, `elevation`, `Size(…)`…). El cero se permite (significa «ninguno»). Se escribió primero la prueba (en rojo con 95 hallazgos) y luego la migración.
- **Sistema de diseño.** `KdLayout` suma valores con nombre (`formMaxWidth`, `pageMaxWidth`, `stateIconSize`) y la librería suma `KdProgressIndicator` (indicador en línea, estático con movimiento reducido).
- **Piezas compartidas** (afectan a todas las pantallas de módulos): botón de envío, banner sin conexión, error de carga, aviso, diálogo de confirmación (ahora `KdDialog`, con «Volver» en lugar de «Cancelar», según el tono de `DESIGN.md`), estado de carga, campo de búsqueda y ancho de las páginas.
- **Pantallas del paciente que existen y no son principales:** inicio (menú en `KdListSection`, selector de sucursal en `KdSheet`), mis contactos autorizados y agregar representante (`KdTextField`, `KdCard`), y estado del sistema (`KdListSection`, íconos del set propio).
- **Pantallas del personal en la app** (tratadas como V, `audit.md` § 1.3): reemplazo de espacios literales por tokens y de botones de acción por `KdButton`. Sus formularios siguen con los campos de Material, ya tematizados por los tokens.
- **Pantallas de ingreso y registro** (de MEDK-45): solo se reemplazaron sus literales por tokens, porque el criterio de cierre es el conteo en cero. Su rediseño es de MEDK-45; si ambos PR tocan las mismas líneas, el conflicto es trivial (mismo token).

## Verificación

- `cd movil && flutter analyze && flutter test`: 236 pruebas en Linux con Flutter 3.47.9 (goldens incluidos); en macOS los goldens se omiten.
- Goldens claro y oscuro con datos ficticios: `movil/test/features/goldens/` (inicio del paciente, inicio del personal sin conexión, mis contactos autorizados, estado del sistema y su error).
- `meetsGuideline` (objetivos táctiles Android/iOS, etiquetas y contraste de texto) en esas cinco pantallas, en claro y oscuro: `movil/test/features/screens_visual_test.dart`.

## Pendientes y notas

- `scripts/update-goldens.sh` falla con `--enforce-lockfile` porque `pubspec.lock` no coincide con los paquetes del SDK 3.47.9 (CI usa `flutter pub get` sin esa opción). Los goldens de esta tarea se generaron con el mismo contenedor sin esa opción. Conviene corregir el script o el lock en una tarjeta aparte.
- Quedan íconos de Material (`Icons.*`) y campos de Material en pantallas del personal. No son estilos sueltos (la guarda no los cuenta); su migración a `KdIcons` / `KdTextField` puede ir en la épica posterior de pantallas no principales.
- No cambia el despliegue.
