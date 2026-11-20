# Auditoría Fase 0 · MEDK-33 · Nueva identidad visual y experiencia UI/UX

Tarea: MEDK-34. Cubre: Plan § Fases (Fase 0) y riesgos R2, R7 y R8.
Fecha de medición: 20/11/2026. Base: `develop` en `1fc8df1` (MEDK-25).
Datos: solo los usuarios semilla del perfil `local` y pacientes ficticios creados por el guion. No se usaron datos reales ni telemetría.

## 0. Resumen

| Tema | Resultado | Efecto en la épica |
|---|---|---|
| Pantallas principales | Existen **3 de 5** en web y **1 de 5** en móvil. No existen ni tienen API: agenda del día, gestión de citas, agendar cita, mis citas, resultados e historial, y perfil del paciente. | Se cumple **R2**: según la spec, estas pantallas quedan fuera de la épica. Las tareas 9, 10, 12, 13 y 14 se reducen. |
| Estilos sueltos web | Las pantallas no tienen estilos propios: todo sale de clases globales. Los valores literales están en 4 archivos (`styles.scss` concentra casi todos). | La migración web es barata: se reemplaza `styles.scss` por los tokens y casi no se tocan plantillas. |
| Estilos sueltos móvil | 1 color literal (semilla del tema), 20 `EdgeInsets`, 1 radio y 74 `SizedBox` con números, repartidos en 26 archivos. No se usa `Colors.*`. | La migración Flutter es mecánica (espaciados). Riesgo **R8** bajo. |
| Línea base de flujos | Ingreso: 3 acciones. Buscar y abrir ficha: 3 acciones. Registrar paciente: 8 acciones. Agendar, cancelar y reprogramar no se pueden medir porque no existen. | SC-006 y SC-007 no se pueden medir en esta épica. Hay que ajustar la spec (**R7**). |
| LCP web | Imagen de producción (nginx): **3,7 s** en perfil móvil y **0,7 s** en escritorio. El mismo build servido con compresión: **1,8 s** en móvil. | **SC-010 no se cumple hoy en móvil**. La causa es que nginx no comprime el JS (519 KB sin comprimir), no la interfaz. |
| Arranque Flutter | **Pendiente**: no hubo un dispositivo físico de referencia conectado. El protocolo y los dispositivos quedan fijados (§ 4.2). | SC-011 sigue con su umbral; hay que medirlo antes de la tarea 12. |
| Pruebas existentes (SC-005) | Web: 37 archivos y 293 pruebas en verde. Flutter: 114 pruebas en verde. | Es la línea base de no regresión. |

## 1. Inventario de pantallas

Leyenda de **Tipo**: **P** = principal según la spec (se rediseña). **V** = no principal (solo migración visual, mismo flujo). **F** = principal que no existe o no tiene API (queda fuera de la épica según la spec).

### 1.1 Web · personal (y pantallas públicas o del paciente que viven en web)

| Pantalla | Ruta | Componente | Existe | Tipo | Nota |
|---|---|---|---|---|---|
| Ingreso | `/login` | `features/auth/login` | Sí | P | Correo, contraseña e «Ingresar». |
| Elegir clínica / sucursal | `/login/clinic`, `/login/branch` | `features/auth/login-selection` | Sí | P | Paso del ingreso cuando hay varias clínicas o sucursales; va con el ingreso. |
| Agenda del día | — | — | **No** | F | No hay módulo de citas ni agenda en `api` ni en `contracts/`. |
| Búsqueda de pacientes | `/patients` | `features/patients/patients-list` | Sí | P | Búsqueda por nombre o documento, tabla y paginación. |
| Ficha del paciente | `/patients/:id` | `features/patients/patient-detail` | Sí | P | Incluye representantes y contactos (`patient-guardian-links`). |
| Gestión de citas | — | — | **No** | F | Sin API (ver agenda). |
| Registrar / editar paciente | `/patients/new`, `/patients/:id/edit` | `features/patients/patient-form` | Sí | V | Formulario más usado después de la búsqueda; ver § 6, propuesta 3. |
| Autorregistro (configuración) | `/patients/self-registration` | `features/patients/self-registration-settings` | Sí | V | |
| Personal (lista, detalle, invitar) | `/staff`, `/staff/:id`, `/staff/new` | `features/staff-users/*` | Sí | V | Incluye el editor de asignaciones de rol. |
| Roles (lista, formulario) | `/roles`, `/roles/new`, `/roles/:id` | `features/roles/*` | Sí | V | |
| Catálogo de permisos (SUPER_ADMIN) | `/platform/permissions` | `features/platform` | Sí | V | |
| Registrar clínica | `/signup` | `features/signup` | Sí | V | Pública. |
| Recuperar / crear contraseña | `/forgot-password`, `/password-reset` | `features/auth/*` | Sí | V | |
| Cambiar contraseña | `/change-password` | `features/change-password` | Sí | V | |
| Activar cuenta (invitación) | `/invitation` | `features/auth/accept-invitation` | Sí | V | |
| Verificar correo | `/email-verification` | `features/auth/verify-email` | Sí | V | |
| Autorregistro de pacientes | `/clinics/:publicCode/register` | `features/auth/self-registration` | Sí | V | Pública; la usa el paciente desde la web. |
| Mis contactos autorizados | `/my-contacts` | `features/patients/my-guardian-links` | Sí | V | Del paciente en web. |
| Estado del sistema | `/status` | `features/status` | Sí | V | |
| Sin permiso / no encontrada | `/forbidden`, `**` | `features/message` | Sí | V | |
| Marco de la app (barra, navegación, cambio de sucursal) | — | `app.*`, `features/auth/branch-switcher` | Sí | P | Es la navegación (FR-007). Debe dar acceso rápido a la búsqueda; la agenda no existe. |

Total web: 19 filas de pantallas o grupos que existen. **4 principales** existen (ingreso con su selección, búsqueda, ficha y marco/navegación). **2 principales faltan** (agenda del día, gestión de citas). **14** son de solo migración visual.

### 1.2 Móvil · paciente (Flutter)

| Pantalla | Archivo | Existe | Tipo | Nota |
|---|---|---|---|---|
| Ingreso (y selección de clínica o sucursal) | `auth/ui/login_screen.dart`, `login_flow.dart`, `clinic_selection_screen.dart`, `branch_selection_screen.dart` | Sí | P | |
| Registro (autorregistro) | `auth/ui/self_registration_screen.dart` | Sí | P | Se abre desde un enlace de la clínica (deep link). |
| Activar cuenta, verificar correo, recuperar contraseña | `auth/ui/invitation_screen.dart`, `email_verification_screen.dart`, `password_reset_*_screen.dart` | Sí | P | Van con «ingreso y registro». |
| Agendar cita | — | **No** | F | Sin API. |
| Mis citas (ver, cancelar, reprogramar) | — | **No** | F | Sin API. |
| Resultados e historial | — | **No** | F | Sin API (no hay historia clínica ni resultados en `api`). |
| Perfil | — | **No** | F | Solo existe `GET /me` (datos de la sesión, de lectura). No hay pantalla ni API para editar el perfil. |
| Inicio | `home/home_screen.dart` | Sí | V | Hoy es un menú (para el paciente, solo «Mis contactos autorizados»). |
| Mis contactos autorizados / agregar contacto | `patients/ui/my_guardian_links_screen.dart`, `add_guardian_screen.dart` | Sí | V | Única función propia del paciente hoy. |
| Estado del sistema / sin conexión | `system_status/system_status_screen.dart`, `core/connectivity/*` | Sí | V | |

### 1.3 Móvil · pantallas del personal (hallazgo)

La app Flutter **también tiene pantallas del personal**: personal, roles, asignaciones, pacientes (lista, ficha, formulario) y configuración de autorregistro (`features/staff_users`, `features/roles`, `features/patients/ui/patient*`, `self_registration_settings_screen.dart`). Todas existen también en web.

La spec dice que en Flutter solo se diseña la experiencia del paciente. **Decisión razonable que tomo y declaro:** estas pantallas se tratan como **V (solo migración visual)**, igual que las demás no principales. No se rediseñan ni se quitan, porque quitarlas cambiaría funciones (FR-012). Si negocio prefiere otra cosa, es una decisión aparte que no cambia la Fase 0.

## 2. Conteo de estilos sueltos

Cómo se contó (reproducible desde la raíz del repo, rama de esta tarea):

- Web: en cada archivo de estilo o plantilla se cuentan `#[0-9a-fA-F]{3,8}`, `rgb()/hsl()`, colores con nombre, `[0-9.]+px`, `[0-9.]+(rem|em)` y declaraciones `border-radius`. Las plantillas HTML no tienen `style=` ni `[style]` (0 coincidencias).
- Flutter: en cada archivo de `lib/` se cuentan `Color(0x`, `Colors.`, `EdgeInsets.*(` con número, `BorderRadius/Radius.*(` con número, `SizedBox(height|width: <número>)` y `fontSize:` con número. La columna «Literales» cuenta cualquier `height|width|size|padding|spacing|elevation|fontSize|radius: <número>`, así que incluye los `SizedBox`.

### 2.1 Web

| Archivo | Hex | Colores con nombre | px | rem/em | `border-radius` | Nota |
|---|---|---|---|---|---|---|
| `src/styles.scss` (global, 518 líneas) | 13 | 0 | 21 | 37 | 11 | Los 13 hex están en `:root` (`--color-*`). Ya hay variables de color, espacio (`--space-1…8`) y radio (`--radius`), pero fuera de `:root` quedan unos 60 literales: bordes de 1–4 px, alturas de 2,75rem, tamaños de letra y `999px`/`50%`. |
| `src/app/app.scss` (marco) | 0 | 1 | 1 | 4 | 2 | |
| `src/app/features/status/status.page.scss` | 0 | 0 | 3 | 3 | 3 | Única pantalla con hoja propia. |
| `src/app/features/message/message.page.ts` (estilo en línea) | 0 | 0 | 0 | 1 | 0 | `max-width: 40rem`. |
| Las demás pantallas (`*.page.html`, `*.page.ts`) | 0 | 0 | 0 | 0 | 0 | Solo usan clases globales. |

Clases globales más usadas en las plantillas (las primeras candidatas a componentes): `button` (68), `field` (64), `alert` (58), `field__error` (57), `hint` (44), `actions` (41), `button--secondary` (26), `page` (20), `field__hint` (20), `form` (17), `badge` (12), `checkbox` (11), `card` (8), `table` (5) y `pagination` (1).

Uso de clases por pantalla principal: ingreso 11, selección de clínica o sucursal 5, búsqueda de pacientes 22, ficha del paciente 29 y marco 7.

### 2.2 Flutter (`movil/lib`)

| Archivo | `Color(0x` | `Colors.` | `EdgeInsets` | Radios | `SizedBox` | Literales |
|---|---|---|---|---|---|---|
| `app.dart` | 1 | 0 | 1 | 0 | 2 | 2 |
| `core/connectivity/connectivity_widgets.dart` | 0 | 0 | 1 | 1 | 1 | 1 |
| `core/ui/module_widgets.dart` | 0 | 0 | 6 | 0 | 2 | 2 |
| `core/ui/paged_search_list.dart` | 0 | 0 | 3 | 0 | 3 | 3 |
| `features/auth/ui/auth_widgets.dart` | 0 | 0 | 2 | 0 | 3 | 4 |
| `features/auth/ui/clinic_selection_screen.dart` | 0 | 0 | 0 | 0 | 1 | 1 |
| `features/auth/ui/email_verification_screen.dart` | 0 | 0 | 0 | 0 | 2 | 2 |
| `features/auth/ui/invitation_screen.dart` | 0 | 0 | 0 | 0 | 5 | 5 |
| `features/auth/ui/login_screen.dart` | 0 | 0 | 0 | 0 | 4 | 4 |
| `features/auth/ui/password_reset_confirm_screen.dart` | 0 | 0 | 0 | 0 | 3 | 3 |
| `features/auth/ui/password_reset_request_screen.dart` | 0 | 0 | 0 | 0 | 3 | 3 |
| `features/auth/ui/self_registration_screen.dart` | 0 | 0 | 0 | 0 | 3 | 3 |
| `features/home/home_screen.dart` | 0 | 0 | 2 | 0 | 7 | 7 |
| `features/patients/ui/add_guardian_screen.dart` | 0 | 0 | 0 | 0 | 3 | 3 |
| `features/patients/ui/my_guardian_links_screen.dart` | 0 | 0 | 0 | 0 | 4 | 4 |
| `features/patients/ui/patient_detail_screen.dart` | 0 | 0 | 1 | 0 | 5 | 6 |
| `features/patients/ui/patient_form_screen.dart` | 0 | 0 | 0 | 0 | 3 | 3 |
| `features/patients/ui/patient_widgets.dart` | 0 | 0 | 0 | 0 | 1 | 1 |
| `features/patients/ui/self_registration_settings_screen.dart` | 0 | 0 | 0 | 0 | 1 | 1 |
| `features/roles/ui/role_form_screen.dart` | 0 | 0 | 1 | 0 | 4 | 4 |
| `features/roles/ui/roles_screen.dart` | 0 | 0 | 0 | 0 | 1 | 1 |
| `features/staff_users/ui/role_assignments_editor.dart` | 0 | 0 | 0 | 0 | 3 | 3 |
| `features/staff_users/ui/role_assignments_screen.dart` | 0 | 0 | 0 | 0 | 1 | 1 |
| `features/staff_users/ui/staff_user_detail_screen.dart` | 0 | 0 | 2 | 0 | 2 | 3 |
| `features/staff_users/ui/staff_user_form_screen.dart` | 0 | 0 | 0 | 0 | 2 | 2 |
| `system_status/system_status_screen.dart` | 0 | 0 | 1 | 0 | 5 | 8 |
| **Total (26 archivos)** | **1** | **0** | **20** | **1** | **74** | **80** |

El tema actual es `ColorScheme.fromSeed` con un solo color semilla en `app.dart`, en claro y oscuro. Las pantallas ya leen `Theme.of(context).colorScheme`. No hay tamaños de letra escritos a mano.

Las pantallas principales del paciente que existen (ingreso, registro y sus pasos) suman 22 literales numéricos y ningún color.

### 2.3 Lectura para R8

La migración visual de las no principales es **acotada**:

- En web es sobre todo reescribir `styles.scss` contra tokens y extraer componentes de las clases globales.
- En Flutter son unas 95 sustituciones mecánicas de espaciado.

No veo motivo para partir las tareas 11 y 14.

## 3. Línea base de flujos

Cómo se midió:

- Recorridos guionados con Playwright 1.65 (Chrome, sin limitar CPU ni red) contra el entorno local en contenedores (`docker compose --profile full`, imagen nginx de producción).
- Usuario semilla `usuario.a@clinica-a.example.test` (personal, una clínica).
- 10 corridas por flujo, cada una en un contexto limpio.
- El guion es un script de auditoría fuera del repo. Repetirlo está descrito en § 7.

**Advertencia:** el tiempo guionado mide al sistema (render y API), no a una persona. El tiempo humano se mide en las sesiones moderadas del cierre (plan, tarea 15), con estos mismos guiones. Para SC-008, la base comparable es el **número de acciones** y el tiempo guionado. **Acción** = un campo que se completa, un clic o un envío.

| # | Flujo | Plataforma | Pantallas | Acciones | Tiempo guionado: mediana (mín.–p90) |
|---|---|---|---|---|---|
| F1 | Ingreso del personal (una clínica, una sucursal) → primera pantalla (`/staff`) | Web | 2 | 3 (correo, contraseña, «Ingresar») | 229 ms (180–261) |
| F1b | Ingreso con varias clínicas o sucursales | Web y móvil | 3–4 | +1 por cada selección | No medido: la semilla tiene una clínica por usuario |
| F2 | Buscar paciente y abrir su ficha, desde `/patients` | Web | 2 | 3 (texto, «Buscar», clic en el resultado) | 305 ms (236–349) |
| F3 | Registrar paciente (solo campos obligatorios), desde `/patients` | Web | 3 (lista → formulario → ficha) | 8 (botón «Registrar», documento, nombres, apellidos, fecha de nacimiento, sexo, consentimiento, «Guardar») | 419 ms (276–458) |
| F4 | Llegar a la búsqueda desde la primera pantalla tras el ingreso | Web | 2 | 1 (clic en «Pacientes» del menú) | — |
| F5 | Ingreso del paciente en la app | Móvil | 2 (+1 si elige clínica) | 3 | No medido (sin dispositivo, ver § 4.2) |
| F6 | Autorregistro del paciente desde el enlace de la clínica | Web y móvil | 2 | 12 campos y 1 envío en web; 11 campos y 1 envío en móvil (cuenta según el código) | No medido: necesita un código público de clínica habilitado |
| F7 | Agendar cita | Móvil | — | — | **No se puede medir: no existe** |
| F8 | Cancelar / reprogramar cita | Móvil | — | — | **No se puede medir: no existe** |

Resultado de las 30 corridas: 0 errores.

Hallazgo de navegación: tras el ingreso, el personal administrador cae en «Personal» (`/staff`), no en la búsqueda de pacientes. Llegar a la búsqueda cuesta una acción más (F4). Es un dato de entrada para la tarea 9 (acceso rápido a la búsqueda).

## 4. Rendimiento

### 4.1 LCP de la página principal web

La «página principal» es `/`. Sin sesión, redirige a `/login`, así que se mide `/login`. Herramienta: Lighthouse 12.8.2, categoría *performance*, Chrome sin interfaz. Tres corridas por caso; las tres dieron lo mismo (±0,1 s).

| Servidor | Perfil | LCP | FCP | TBT | CLS | Puntaje |
|---|---|---|---|---|---|---|
| Imagen de producción (nginx del `Dockerfile` de `web`) | Móvil (por defecto de Lighthouse: Moto G Power emulado, 4G lento, CPU ×4) | **3,7 s** | 3,3 s | 0 ms | 0 | 0,82 |
| Imagen de producción (nginx) | Escritorio (`--preset=desktop`) | 0,72 s | 0,64 s | 0 ms | 0 | — |
| Mismo build con compresión Brotli (`serve`) | Móvil | 1,8 s | 1,5 s | 0 ms | 0 | 0,99 |
| Mismo build con compresión Brotli (`serve`) | Escritorio | 0,44 s | 0,32 s | 0 ms | 0 | 1,00 |

Causa del 3,7 s:

- `web/nginx.conf` entrega `main-*.js` **sin comprimir** (519 KB).
- Con Brotli, el mismo archivo pesa unos 120 KB.
- Además, el bundle inicial ya excede el presupuesto de Angular (526,5 KB frente a 500 KB, aviso de `npm run build`).

No es un problema de la interfaz. Hay que corregirlo para cumplir SC-010, pero está fuera de esta tarea (ver § 6, propuesta 4).

### 4.2 Arranque de la app Flutter

**Dispositivos de referencia (fijados aquí).** Es una decisión razonable mía, porque nadie los había definido. Se pueden cambiar sin afectar el resto de la auditoría.

| Plataforma | Dispositivo de referencia | Por qué |
|---|---|---|
| Android | Gama media de 2023: Samsung Galaxy A14 / A15 o equivalente (4 GB de RAM, Android 14) | Representa al paciente promedio; un equipo de gama alta escondería problemas. |
| iOS | iPhone 11 o iPhone SE (3.ª generación), con el iOS más reciente que soporte | Es el equipo más antiguo que todavía es habitual. |
| Web | Perfiles de Lighthouse «móvil» (por defecto) y «escritorio» | Así se mide en § 4.1 y en Lighthouse CI (tarea 3). |

Protocolo de medición:

1. Instalar la app limpia en el dispositivo físico.
2. Ejecutar `flutter run --profile --trace-startup --dart-define=API_BASE_URL=<api local>` 5 veces, en arranque en frío.
3. Leer `timeToFirstFrameMicros` de `build/start_up_info.json`.
4. Para la fluidez: en `integration_test`, `traceAction` sobre el desplazamiento de la lista de contactos, con `FrameTiming`. Se reportan los cuadros > 16 ms.

**Estado: pendiente.** En esta sesión no había ningún dispositivo físico disponible:

- `flutter devices` no encontró ninguno conectado.
- No hay un AVD Android creado.
- El modo *profile* no corre en el simulador de iOS, y medir en modo debug no sería representativo.

Queda como **condición previa a la tarea 12**: una persona con el dispositivo de referencia ejecuta el protocolo y completa esta tabla.

| Dispositivo | Arranque en frío, mediana de 5 | Cuadros > 16 ms al desplazar |
|---|---|---|
| Android de referencia | [POR MEDIR] | [POR MEDIR] |
| iOS de referencia | [POR MEDIR] | [POR MEDIR] |

## 5. Pruebas existentes (línea base de SC-005)

| Pieza | Comando | Resultado (20/11/2026, `1fc8df1`) |
|---|---|---|
| web | `npm test -- --watch=false` | 37 archivos, 293 pruebas, todas en verde |
| movil | `flutter test` | 114 pruebas, todas en verde |
| web | `npm run build` | Compila con un aviso de presupuesto (bundle inicial de 526,5 KB frente a 500 KB) |

## 6. Umbrales de los SC: confirmados o ajustados

Según R7, cambiar un SC es cambiar la spec: lo que sigue es una **propuesta** que una persona aprueba en la spec. La tarea 15 mide contra la versión acordada.

| SC | Umbral actual | Propuesta | Motivo |
|---|---|---|---|
| SC-001 | 100 % de las principales sin literales | **Confirmar**, aplicado a las principales que existen (§ 1) | Es medible con las guardas de las tareas 4 y 5. |
| SC-002 | AA en claro y oscuro | **Confirmar** | — |
| SC-003 | Un token se refleja en web y móvil | **Confirmar** | — |
| SC-004 | Objetivos táctiles ≥ 44 px en las principales móviles | **Confirmar** | Hoy web ya usa `min-height: 2.75rem` (44 px) en botones y campos. |
| SC-005 | Pruebas existentes en verde | **Confirmar**, con la base de § 5 (293 + 114) | — |
| SC-006 | Agendar en ≤ 4 pasos y < 60 s | **Ajustar: «no aplica en esta épica»** | Agendar cita no existe ni tiene API (R2). Queda como meta para la épica que cree el módulo de citas. |
| SC-007 | Cancelar o reprogramar en ≤ 3 pasos | **Ajustar: «no aplica en esta épica»** | Mismo motivo. |
| SC-008 | −20 % de tiempo frente a la línea base en los demás flujos principales | **Ajustar el alcance** a F1, F2 y F4 (§ 3). Proponer como medida: el tiempo de la sesión moderada (−20 %) y, como apoyo, que no suban las acciones (F1 ≤ 3, F2 ≤ 3, F4 ≤ 1). | El tiempo guionado ya es de 0,2–0,4 s y una mejora visual no lo baja. El tiempo humano se mide en el cierre, con la base que se tome al inicio de la tarea 9. |
| SC-009 | 90 % sin ayuda | **Confirmar**, sobre F1, F2, F5 y F6 | — |
| SC-010 | LCP < 2,5 s | **Confirmar 2,5 s**, medido con el perfil móvil de Lighthouse sobre la imagen de producción | Hoy da 3,7 s por la falta de compresión en nginx (§ 4.1). Corregirlo **antes** de la tarea 9 permite atribuir bien el resultado. |
| SC-011 | Arranque < 2 s y sin tirones | **Confirmar**, en los dispositivos de referencia de § 4.2 | Base pendiente de medir en un dispositivo físico. |
| SC-012 | Aprobación formal | **Confirmar** | — |

Otras propuestas que salen de la auditoría:

1. **Lista de pantallas principales (spec, decisiones de negocio).**
   - Web: ingreso (con la selección de clínica y sucursal), búsqueda de pacientes, ficha del paciente y navegación.
   - Móvil: ingreso y registro (con activación, verificación y recuperación).
   - Agenda del día, gestión de citas, agendar cita, mis citas, resultados e historial, y perfil quedan fuera por R2.
2. **Las tareas 10, 13 y 14 quedan casi vacías en su parte principal.**
   - Tarea 10: solo la ficha del paciente.
   - Tarea 13: nada.
   - Tarea 14: solo la migración visual restante.

   Lo debe revisar quien reparte las tareas de la épica.
3. **Formulario de registrar/editar paciente (web).** Es el formulario con más acciones (F3) y está pegado a la búsqueda y la ficha. Queda como V, pero se sugiere considerarlo principal en una revisión de la spec. Hoy no cambia nada.
4. **Compresión en nginx** (`gzip`/`brotli` para JS, CSS y JSON) y **presupuesto del bundle**. Cambia un paso de despliegue, así que va por la tarea 3 (DEVOPS) o por una tarjeta aparte, con su nota en `docs/deployment/changes/`. Esta tarea no la modifica.

## 7. Cómo repetir la medición

1. Levantar el entorno en un proyecto de Compose propio, para no pisar otro entorno local que use el nombre por defecto `medical-kuador`:

   ```bash
   docker compose -p <nombre> --profile full up -d --wait
   ```

   Con puertos libres en `.env`: `DB_PORT`, `API_PORT`, `WEB_PORT` y los de Mailpit.
2. Medir el LCP:

   ```bash
   npx lighthouse@12 http://localhost:<WEB_PORT>/login --only-categories=performance
   ```

   Repetirlo con `--preset=desktop`. Tres corridas por caso.
3. Flujos: Playwright con Chrome. Pasos de F1, F2 y F3 como en § 3, 10 corridas por flujo y contexto limpio en cada una, con el usuario semilla y pacientes ficticios. El guion de esta auditoría no se versionó, porque no es parte del producto. La tarea 3 o la 15 lo pueden convertir en `web/e2e/flows/` si se quiere en CI.
4. Conteo de estilos: con los patrones de § 2.
