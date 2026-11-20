---
# Tokens of the identity guide (MEDK-39). Source of truth: design/tokens/ (DTCG).
# This header is a summary for people; design/test/design-md.test.mjs fails if it drifts.
name: Medical Kuador
reference: design/reference/medical-inspirations.avif
color:
  accent: { light: '#00737a', dark: '#3ccfd2' }
  on-accent: { light: '#ffffff', dark: '#002244' }
  focus: { light: '#004f55', dark: '#86e6e8' }
  ok: { light: '#1f7034', dark: '#6fd38c' }
  error: { light: '#c0263d', dark: '#ff8da0' }
  on-error: { light: '#ffffff', dark: '#3d0712' }
  warning: { light: '#8a5000', dark: '#f2c063' }
  info: { light: '#3456b8', dark: '#a4bbff' }
surface:
  base: { light: '#f1f5f6', dark: '#071827' }
  sunken: { light: '#e3ebed', dark: '#030f1a' }
  raised: { light: '#ffffff', dark: '#0e2236' }
  overlay: { light: '#ffffff', dark: '#162c42' }
text:
  default: { light: '#0b2440', dark: '#e7eff3' }
  muted: { light: '#4a5e6d', dark: '#a4b5c1' }
border:
  hairline: { light: '#d3dee1', dark: '#223a4e' }
  strong: { light: '#6b808b', dark: '#7c93a3' }
radius: { sm: 6px, md: 10px, lg: 16px, full: 9999px }
space: { 0: 0px, 1: 4px, 2: 8px, 3: 12px, 4: 16px, 6: 24px, 8: 32px, 12: 48px }
size: { target-min: 44px }
type:
  family: { base: Figtree, mono: ui-monospace }
  weight: { regular: 400, medium: 500, bold: 600 }
  size: { xs: 12px, sm: 14px, md: 16px, lg: 18px, xl: 20px, 2xl: 24px, 3xl: 30px }
motion:
  duration: { fast: 120ms, normal: 200ms, slow: 320ms }
icons: { set: Lucide, grid: 24px, stroke: 2px, folder: design/icons/ }
---

# DESIGN.md · Identidad visual de Medical Kuador

Guía de identidad de la épica MEDK-33 (tarea MEDK-39). Dice **qué** se usa y **por qué**; los
valores viven en `design/tokens/` y se generan para web (`web/src/styles/tokens.css`) y Flutter
(`movil/lib/design_system/tokens.g.dart`). Si esta guía y los tokens no coinciden, mandan los
tokens y la prueba `design/test/design-md.test.mjs` falla hasta corregir la cabecera.

## Imagen de referencia

![Imagen de referencia: tres pantallas móviles de una app médica (inicio, lista de especialistas y agenda) en turquesa, azul marino y blanco](design/reference/medical-inspirations.avif)

`design/reference/medical-inspirations.avif` (adjunta en MEDK-33). El Equipo Kuador indicó que es
**una muestra de lo que se espera lograr y se toma como referencia** (comentario del 20/11/2026):
de ella sale el carácter (turquesa sobre blanco, azul marino de apoyo, tarjetas redondeadas,
íconos de línea), no una copia de pantallas. Contiene las tres pantallas clave en móvil:

| Pantalla de la imagen | Equivale a | Qué tomamos |
|---|---|---|
| Izquierda: bienvenida | Inicio | Título en el acento, una frase y **una** acción principal. |
| Centro: categorías y especialistas | Lista | Filas en tarjetas con avatar, fila seleccionada marcada con el acento. |
| Derecha: agenda | Detalle | Calendario con estados (ocupado / disponible), confirmación como acción única, panel azul marino. |

**Pendiente (no bloquea):** la aprobación formal de la imagen por la persona designada del Equipo
Kuador y los mockups propios de web (inicio, lista y detalle). Cuando lleguen, se guardan en
`design/reference/` y se enlazan aquí; si cambian un valor, solo se toca `design/tokens/`.

## Cómo se extrajo

| Rasgo | En la imagen | Decisión |
|---|---|---|
| Acento | Turquesa `#00b3b6` | Mismo tono, oscurecido a `#00737a` en claro para que el texto blanco encima y el acento como texto pasen AA (5,6:1). En oscuro, turquesa claro `#3ccfd2` con texto azul marino. |
| Apoyo | Azul marino `#002244` | Es el color del texto (`text.default` `#0b2440`) y la base del tema oscuro. No es un segundo acento. |
| Neutros | Gris verdoso `#d8e0e1`, blanco | Neutros fríos con un toque turquesa, en capas: `sunken` < `base` < `raised` ≤ `overlay`. |
| Selección suave | Menta `#bfe1e1` | No es un token: se obtiene mezclando el acento con la superficie (ver reglas). |
| Ocupado | Magenta `#ff0fa5` | Inspira `color.error` (carmesí `#c0263d`), sin usar magenta puro: no pasa AA y compite con el acento. |
| Semánticos | — | `ok` verde, `warning` ámbar, `info` azul (el azul de los uniformes de la ilustración), todos AA sobre `base` y `raised`. |
| Tipografía | Sans geométrica redondeada | **Figtree** (ver Tipografía). |
| Radios | Tarjetas muy redondeadas, botones medios | `sm` 6 px, `md` 10 px, `lg` 16 px, `full` para avatares y píldoras. |
| Íconos | Línea, trazo redondeado | Set **Lucide** de línea, 24 px, trazo 2 px (ver Iconografía). |

## Color

| Rol | Uso | No usar para |
|---|---|---|
| `color.accent` | Acción primaria, elemento seleccionado, enlaces, indicador de pestaña activa. | Fondos grandes, títulos decorativos, gráficos. |
| `color.on-accent` | Texto e íconos sobre el acento. | Texto sobre superficies. |
| `color.focus` | Anillo de foco (2 px, separado 2 px del control). | Bordes de estado. |
| `color.ok` / `error` / `warning` / `info` | Texto e íconos de estado, siempre con un ícono o un texto que lo diga. | Comunicar el estado **solo** con color. |
| `color.on-error` | Texto sobre un botón destructivo. | — |
| `surface.base` | Fondo de la página. | Tarjetas. |
| `surface.sunken` | Encabezados de tabla, campos deshabilitados, zonas hundidas. | Contenido principal. |
| `surface.raised` | Tarjetas, formularios, paneles. | Lo que flota. |
| `surface.overlay` | Diálogos, menús, hojas inferiores, toasts. | Contenido fijo. |
| `text.default` / `text.muted` | Texto principal / secundario (pistas, metadatos). | `muted` en texto de acciones. |
| `border.hairline` | Divisor decorativo de 1 px entre capas. | Contorno de controles (no llega a 3:1). |
| `border.strong` | Contorno de campos y controles (≥ 3:1). | Divisores. |

Todos los pares de `design/tokens/contrast-pairs.json` cumplen WCAG 2.2 AA en claro y oscuro
(texto 4,5:1, no texto 3:1); el build de tokens falla si uno deja de cumplir.

## Tipografía

- **Familia:** Figtree (sans geométrica, cercana a la de la imagen y más compacta para tablas del
  personal). Para códigos e identificadores, la monoespaciada del sistema.
- **Licencia (riesgo R4, verificado):** SIL Open Font License 1.1 — permite incrustarla,
  autoalojarla y distribuirla con la app, sin costo; obliga a incluir la licencia y a no venderla
  sola. Archivos: `web/src/assets/fonts/` (woff2 variable, subconjuntos latin y latin-ext) y
  `movil/assets/fonts/` (TTF 400, 500 y 600), cada carpeta con su `OFL.txt`. Sin CDN.
- **Pesos:** 400 texto, 500 subtítulos, 600 títulos y etiquetas. No se usan 700+.
- **Escala:** `display` 30, `title` 20, `subtitle` 18, `body` 16, `body-sm` 14, `label` 14,
  `caption` 12 (px). El texto de lectura nunca baja de 14 px; 12 px solo en `caption`.

## Iconografía

- Set: **Lucide** (licencia ISC, `design/icons/LICENSE`), solo íconos de línea, cuadrícula de
  24 px, trazo 2 px, extremos redondeados, color heredado del texto (`currentColor`).
- `design/icons/` tiene el subconjunto que usan las pantallas principales (inicio, agenda,
  pacientes, citas, estados, navegación). Si falta uno, se agrega desde el mismo set y la prueba
  `design/test/icons.test.mjs` verifica que siga las reglas.
- Web: componente de ícono de la librería (MEDK-40). Flutter: `flutter_svg` (MEDK-41).
- Un ícono sin texto visible siempre lleva nombre accesible; el tamaño visual es 20 o 24 px, pero
  su objetivo táctil es de 44 px.

## Tono de los textos

- Español neutro, de **tú**, frases cortas y en positivo: «Agenda tu cita», no «Usted puede
  proceder a agendar una cita».
- Botones con verbo y objeto: «Confirmar cita», «Buscar paciente». Nada de «Aceptar» o «OK» sueltos.
- Errores: qué pasó y qué hacer, sin culpar ni códigos técnicos: «No pudimos guardar la cita.
  Revisa tu conexión e inténtalo de nuevo.»
- Bajo un título, como mucho una frase. La estructura explica la pantalla.
- Sin datos clínicos en mensajes de notificación ni en textos de ejemplo; en mockups y pruebas,
  solo datos ficticios.

## Reglas con nombre

| Regla | Sí | No |
|---|---|---|
| **R-01 Un solo acento** | El acento marca la acción primaria, la selección y el foco. | Usar el acento como decoración, en títulos o en un segundo botón. Agregar otro color de marca. |
| **R-02 Una acción primaria por vista** | Un botón con relleno de acento por pantalla, hoja o diálogo; el resto secundario (contorno) o de texto. | Dos botones de acento juntos. |
| **R-03 Capas tonales** | Profundidad con `surface.*` y `border.hairline` de 1 px. | Sombras para separar contenido fijo. |
| **R-04 Sombra solo en lo que flota** | `shadow.*` en menús, diálogos, hojas inferiores, toasts y botones flotantes. | Sombra en tarjetas, tablas o campos. |
| **R-05 Objetivo de 44 px** | Todo control táctil o clicable mide al menos `size.target-min` (44 px) en ambos ejes. | Íconos clicables de 24 px sin área ampliada. |
| **R-06 Solo tokens** | Colores, espacios, radios, sombras y tipos salen de `--kd-*` / `KdTokens`. | Hex, `px` sueltos, `Colors.*` o `EdgeInsets` con literales en pantallas (lo bloquean las guardas de MEDK-37/38). |
| **R-07 Contraste AA** | Cada combinación nueva de fondo y texto se agrega a `contrast-pairs.json`. | Texto sobre una mezcla o imagen sin verificar. |
| **R-08 Estado con más que color** | Ícono + texto («Ocupado», «Disponible»). | Distinguir estados solo por color (como el magenta/turquesa de la imagen). |
| **R-09 Foco visible** | Anillo `color.focus` de 2 px con separación, en teclado. | `outline: none` sin reemplazo. |
| **R-10 Selección suave por mezcla** | Fila o tarjeta seleccionada: fondo `color-mix(acento 12 %, surface.raised)` + borde o indicador de acento. | Un hex nuevo «menta». |
| **R-11 Radios por tamaño** | `sm` insignias; `md` botones, campos y filas; `lg` tarjetas y diálogos; `full` avatares y píldoras. | Mezclar radios en un mismo grupo de controles. |
| **R-12 Espaciado en múltiplos de 4** | `space.*` (4–48 px). | Valores intermedios (6, 10, 14 px). |
| **R-13 Movimiento sobrio** | `motion.*`; con `prefers-reduced-motion` o `disableAnimations`, sin animación. | Animaciones decorativas o de más de 320 ms. |
| **R-14 Misma marca, patrón nativo** | Web y Flutter comparten tokens; móvil usa barra inferior y hojas, web navegación lateral o superior. | Copiar la disposición de web en móvil o al revés. |
| **R-15 Claro y oscuro** | Toda pantalla se revisa en ambos temas. | Colores que solo funcionan en uno. |

## Cambiar un valor

1. Editar `design/tokens/*.tokens.json` y, si cambia un color, la cabecera de este archivo.
2. `cd design && npm run build && npm test` (contrato, contraste AA, salidas, íconos y esta cabecera).
3. Versionar las salidas regeneradas en el mismo PR.
