# Despliegue de Medical Kuador

Documento vivo: se actualiza en el mismo Pull Request que cambia cómo se despliega.

## Entornos

| Entorno | Rama | Dónde corre | URL | Perfil Spring |
|---|---|---|---|---|
| Local | cualquiera | Equipo de cada persona, con Docker Compose (`compose.yaml`) | `http://localhost:4200` (web), `http://localhost:8080` (api) | `local` |
| Pruebas automáticas | cualquiera (CI) | Runner de CI; PostgreSQL con Testcontainers | No aplica | `test` |
| Pruebas | develop | [POR DEFINIR: servidor o plataforma] | [POR DEFINIR: URL] | `prod` (con sus propias variables) |
| Producción | main | [POR DEFINIR: servidor o plataforma] | [POR DEFINIR: URL] | `prod` |

Perfiles de la api:

- `local`: desarrollo en el equipo; se conecta al PostgreSQL de Compose y permite CORS desde la web local.
- `test`: lo usan `./mvnw -B verify` y el CI; la base la crea Testcontainers, no necesita `.env` ni Compose (solo Docker).
- `prod`: pruebas y producción; todos los valores vienen de variables del entorno y secretos del gestor; sin valores por defecto para secretos.

Perfiles de Compose:

- sin perfil: `db` y `mailpit` (`docker compose up -d db mailpit`).
- `full`: `db`, `mailpit`, `api` y `web` en contenedores (`docker compose --profile full up -d --build`).
- `compose.local.yaml`: solo `db` (PostgreSQL), para correr api y web en el host con sus comandos nativos. Toma el servicio `db` de `compose.yaml` (misma imagen, variables, volumen y healthcheck) y usa el mismo nombre de proyecto, así que comparte contenedor y datos con `docker compose up -d db`. Ver «Paso a paso › Local › Base de datos local».

## Infraestructura

[POR DEFINIR: proveedor, sistema operativo y tamaño del servidor; plataforma de despliegue (por ejemplo Dokploy o Docker Compose); proxy y certificados.]

## Piezas

| Pieza | Cómo se construye | Puerto | Dominio por entorno |
|---|---|---|---|
| api | `./mvnw -B package` en `api/`; imagen con `api/Dockerfile` (JRE 25, usuario sin privilegios) | 8080 en el contenedor; `API_PORT` en local (por defecto 8080) | [POR DEFINIR: pruebas / producción] |
| web | `npm run build` en `web/` (Node.js `^22.22.3` o `^24.15.0`); imagen con `web/Dockerfile` (nginx sin privilegios, sirve el build y hace proxy de `/api/` a la api; al arrancar reescribe `config.json` si recibe `API_BASE_URL` y los archivos de `/.well-known/` si recibe `APP_LINKS_*`; ver «Deep links») | 8080 en el contenedor; `WEB_PORT` en local (por defecto 4200) | [POR DEFINIR: pruebas / producción] |
| movil | Build de Flutter en `movil/` con `--dart-define=API_BASE_URL=…` y `--dart-define=APP_LINK_HOST=…`; en iOS el dominio de Associated Domains va en `ios/Flutter/AppLinks.local.xcconfig` (iOS mínimo 15.0; Android necesita JDK 17+) [POR DEFINIR: distribución a tiendas o canal interno] | No aplica | No aplica |

En local los puertos se publican solo en `127.0.0.1`.

## Dependencias

| Servicio | Versión | Para qué | Cómo se levanta | Datos que guarda |
|---|---|---|---|---|
| PostgreSQL | 18 (`postgres:18-alpine`) | Base de datos de la api | Local: servicio `db` de `compose.yaml`, puerto `DB_PORT` (por defecto 5432). Pruebas automáticas: Testcontainers. Pruebas y producción: [POR DEFINIR: contenedor o servicio administrado] | Datos de todos los tenants, incluidos datos de salud. Local: volumen `db-data`. [POR DEFINIR: volumen y respaldo en pruebas y producción] |
| Mailpit | 1.29 (`axllent/mailpit:v1.29`) | Solo local: servidor SMTP que atrapa todos los correos (invitaciones, recuperación, verificación) para verlos sin enviarlos | Servicio `mailpit` de `compose.yaml`: SMTP en `127.0.0.1:${MAILPIT_SMTP_PORT}` (por defecto 1025) e interfaz web en `http://localhost:${MAILPIT_UI_PORT}` (por defecto 8025). Nunca en pruebas ni producción | Correos de prueba en memoria; se pierden al detenerlo |
| Correo (SMTP) | [POR DEFINIR: proveedor] | Invitaciones del personal y de pacientes adultos, recuperación de contraseña y verificación de correo | Servicio externo por SMTP con STARTTLS, configurado con `KUADOR_SMTP_*` | Ninguno propio |
| SMS | [POR DEFINIR: proveedor] | Invitación de activación al teléfono registrado (mayoría de edad) | Servicio externo con `KUADOR_SMS_*`. Sin `KUADOR_SMS_PROVIDER` el envío por SMS queda deshabilitado | Ninguno propio |
| Proveedores de pagos | [POR DEFINIR] | Cobro de suscripciones | Servicios externos [POR DEFINIR: proveedores] | Ninguno propio |

Librerías de la api que cambian cómo se despliega (no son servicios aparte, viven en la base PostgreSQL):

| Librería | Para qué | Qué exige al desplegar |
|---|---|---|
| Flyway (`spring-boot-starter-flyway`, `flyway-database-postgresql`) | Migraciones de la base en `api/src/main/resources/db/migration` (`V<n>__<descripción>.sql`) | La api las aplica al arrancar, antes de atender solicitudes. El usuario `DB_USERNAME` necesita permiso para crear y modificar tablas en su esquema. Una migración publicada nunca se edita: se agrega una nueva. Si una migración falla, la api no arranca; se corrige con una migración nueva y se vuelve a desplegar (Flyway guarda el historial en `flyway_schema_history`) |
| Spring Mail (`spring-boot-starter-mail`) | Adaptador SMTP de `NotificationSender` (módulo `notifications`): invitaciones del personal, de pacientes y representantes (MEDK-16) y recuperación de contraseña (MEDK-15) | Usa `spring.mail.*` desde `KUADOR_SMTP_*` y el remitente `KUADOR_MAIL_FROM`. Sin `KUADOR_SMTP_HOST` o sin `KUADOR_MAIL_FROM` la api arranca pero no envía correos (lo avisa en el log sin datos personales). Las invitaciones salen después de confirmar la transacción y se pueden reenviar; la recuperación sale en segundo plano, así que un fallo del proveedor queda en el log y no cambia la respuesta `202` |
| ShedLock | Lock distribuido de las tareas programadas (por ejemplo, el job diario de mayoría de edad de MEDK-18), para que con varias réplicas de la api cada ejecución corra una sola vez | Usa la tabla `shedlock` de la misma base, creada por una migración Flyway de la tarea que lo agrega (MEDK-18). No necesita variables ni servicio propio. Los relojes de los servidores deben estar sincronizados (NTP) |

## Variables y secretos

Solo nombres: los valores nunca van en este documento ni en el repo. En local viven en `.env` (copiado de `.env.example`, ignorado por git); en pruebas y producción, en el gestor de secretos del entorno [POR DEFINIR: cuál].

| Nombre | Pieza | Entornos | Dónde vive | Secreto | Uso |
|---|---|---|---|---|---|
| SPRING_PROFILES_ACTIVE | api | todos | `.env` / variables del entorno | no | `local`, `test` o `prod` |
| DB_URL | api | local, pruebas, producción | `.env` / variables del entorno | no | JDBC de PostgreSQL. En el perfil `full` de Compose se fuerza a host `db` |
| DB_USERNAME | api, db | local, pruebas, producción | `.env` / variables del entorno | no | Usuario de la base |
| DB_PASSWORD | api, db | local, pruebas, producción | `.env` / gestor de secretos | sí | Clave de la base |
| DB_NAME | db | local | `.env` | no | Nombre de la base que crea Compose (por defecto `medical`) |
| DB_PORT | db | local | `.env` | no | Puerto del host para PostgreSQL (por defecto 5432) |
| KUADOR_JWT_SECRET | api | local, pruebas, producción | `.env` / gestor de secretos | sí | Firma de los tokens de acceso (≥ 256 bits, es decir ≥ 32 bytes; distinta por entorno) y clave de las HMAC del límite de intentos. Obligatoria: sin ella la api no arranca. Al rotarla se invalidan los tokens de acceso vivos y se reinician los contadores de intentos |
| KUADOR_JWT_TTL | api | todos | `.env` / variables del entorno | no | Vida del token de acceso (por defecto `PT15M`, ADR-013) |
| KUADOR_JWT_REFRESH_TTL | api | todos | `.env` / variables del entorno | no | Vida del refresh token rotativo (por defecto `P7D`) |
| KUADOR_JWT_PRE_AUTH_TTL | api | todos | `.env` / variables del entorno | no | Vida del `preAuthToken` entre el login y la elección de clínica o sucursal (por defecto `PT5M`) |
| KUADOR_LOGIN_MAX_FAILURES | api | todos | `.env` / variables del entorno | no | Intentos fallidos permitidos por correo + IP y por cuenta dentro de la ventana (por defecto 5) |
| KUADOR_LOGIN_FAILURE_WINDOW | api | todos | `.env` / variables del entorno | no | Ventana en la que se cuentan los intentos fallidos (por defecto `PT15M`) |
| KUADOR_LOGIN_LOCK_DURATION | api | todos | `.env` / variables del entorno | no | Duración del bloqueo temporal al superar el límite (por defecto `PT15M`) |
| KUADOR_PLAN_USER_LIMIT | api | todos | `.env` / variables del entorno | no | Provisional hasta que exista el módulo de planes: límite de usuarios del personal (invitados y activos) para todas las clínicas; vacío o `0` = sin límite. El límite real lo fija el plan de cada clínica [POR DEFINIR: planes y límites del SUPER_ADMIN] |
| KUADOR_CLINIC_TIME_ZONE | api | todos | `.env` / variables del entorno | no | Zona horaria (identificador IANA) con la que se calcula si un paciente es menor de 18 años. Por defecto `UTC`; vale para todas las clínicas hasta que cada clínica tenga la suya [POR DEFINIR: zona horaria por clínica] |
| KUADOR_INVITATION_TTL | api | todos | `.env` / variables del entorno | no | Vencimiento del enlace de invitación (por defecto `PT72H`) |
| KUADOR_PASSWORD_RESET_TTL | api | todos | `.env` / variables del entorno | no | Vencimiento del enlace de recuperación de contraseña (por defecto `PT30M`) |
| KUADOR_EMAIL_VERIFICATION_TTL | api | todos | `.env` / variables del entorno | no | Vencimiento del enlace de verificación de correo del autorregistro (por defecto `PT24H`) |
| KUADOR_WEB_BASE_URL | api | local, pruebas, producción | `.env` / variables del entorno | no | URL base de la web para los enlaces de los correos (local: `http://localhost:4200`). Obligatoria en pruebas y producción |
| KUADOR_APP_LINK_BASE_URL | api | pruebas, producción | variables del entorno | no | `https://<dominio de deep links>` [POR DEFINIR: dominio]. Base de los enlaces de invitación, recuperación y verificación, que abren la app (App Links / Universal Links) o la web. Si está vacía se usa `KUADOR_WEB_BASE_URL`. Ese dominio debe servir la web con los archivos de `/.well-known/` (ver «Deep links») |
| KUADOR_SMTP_HOST | api | todos | `.env` / variables del entorno | no | Servidor SMTP. Local: `localhost` (Mailpit; `mailpit` dentro del perfil `full`) |
| KUADOR_SMTP_PORT | api | todos | `.env` / variables del entorno | no | Puerto SMTP (por defecto 587; local 1025) |
| KUADOR_SMTP_AUTH | api | todos | `.env` / variables del entorno | no | Autenticación SMTP (por defecto `true`; local `false`) |
| KUADOR_SMTP_STARTTLS | api | todos | `.env` / variables del entorno | no | STARTTLS obligatorio (por defecto `true`; local `false`) |
| KUADOR_SMTP_USERNAME | api | pruebas, producción | variables del entorno | no | Usuario SMTP del proveedor [POR DEFINIR: proveedor] |
| KUADOR_SMTP_PASSWORD | api | pruebas, producción | gestor de secretos | sí | Clave SMTP del proveedor. Mailpit no la necesita |
| KUADOR_MAIL_FROM | api | todos | `.env` / variables del entorno | no | Remitente de los correos [POR DEFINIR: dirección y dominio de envío en pruebas y producción] |
| KUADOR_SMS_PROVIDER | api | pruebas, producción | variables del entorno | no | Adaptador de SMS [POR DEFINIR: proveedor]. Vacío: SMS deshabilitado |
| KUADOR_SMS_API_KEY | api | pruebas, producción | gestor de secretos | sí | Credencial del proveedor de SMS. Los nombres de `KUADOR_SMS_*` pueden cambiar cuando se defina el proveedor |
| KUADOR_SMS_SENDER | api | pruebas, producción | variables del entorno | no | Remitente o número de envío de SMS |
| MAILPIT_SMTP_PORT / MAILPIT_UI_PORT | mailpit | local | `.env` | no | Puertos del host para Mailpit (por defecto 1025 y 8025) |
| APP_LINKS_ANDROID_PACKAGE | web | pruebas, producción | variables del contenedor web | no | `applicationId` de Android para `assetlinks.json` (por defecto `com.kuador.medical_kuador`) |
| APP_LINKS_ANDROID_SHA256 | web | pruebas, producción | variables del contenedor web | no | Huellas SHA-256 de los certificados de firma de la app Android, separadas por coma (la de la firma de Google Play y, si aplica, la de subida). Son públicas, no son secretos |
| APP_LINKS_IOS_APP_IDS | web | pruebas, producción | variables del contenedor web | no | `<TeamID>.<bundle id>` de la app iOS, separados por coma (bundle actual `com.kuador.medicalKuador`) [POR DEFINIR: Team ID de Apple] |
| KUADOR_BOOTSTRAP_ADMIN_EMAIL | api | local, pruebas, producción | `.env` / variables del entorno | no | Correo de la cuenta inicial SUPER_ADMIN (el acordado en MEDK-1) |
| KUADOR_BOOTSTRAP_ADMIN_PASSWORD | api | local, pruebas, producción | `.env` / gestor de secretos | sí | Clave inicial del SUPER_ADMIN; ver «Cuenta inicial del SUPER_ADMIN» |
| KUADOR_CORS_ALLOWED_ORIGINS | api | local, pruebas, producción | `.env` / variables del entorno | no | Origen permitido de la web |
| SMOKE_SUPER_ADMIN_NEW_PASSWORD | `scripts/smoke-test.sh` | local | `.env` | sí | Clave que la prueba de humo pone al SUPER_ADMIN en su primera corrida sobre una base nueva (≥ 12 caracteres) y con la que ingresa en las siguientes. Solo local; la api la ignora |
| API_PORT | api | local | `.env` | no | Puerto del host para la api en el perfil `full` |
| WEB_PORT | web | local | `.env` | no | Puerto del host para la web en el perfil `full` |
| API_PROXY_TARGET | web (servidor de desarrollo, `proxy.conf.mjs`) | local | variable del shell al correr `npm start` | no | Destino del proxy de desarrollo de `/api` y `/actuator`. Por defecto `http://localhost:8080`; debe ser una URL `http(s)` (si no, `npm start` no arranca). La web escucha en `http://localhost:4200` (`ng serve`) |
| API_BASE_URL | web (`config.json`), movil (`--dart-define`) | todos | web: `public/config.json` (por defecto `/api/v1`, mismo origen) o variable del contenedor web, que reescribe `config.json` al arrancar; movil: `--dart-define` | no | URL de la api (`…/api/v1`). En la web, una URL de otro origen exige incluir el origen de la web en `KUADOR_CORS_ALLOWED_ORIGINS` |
| APP_LINK_HOST | movil (`--dart-define`; iOS: `ios/Flutter/AppLinks.local.xcconfig`, no se versiona) | pruebas, producción | build de la app | no | Host de `KUADOR_APP_LINK_BASE_URL` sin esquema [POR DEFINIR: dominio]. Android lo pone en el `intent-filter` con `autoVerify` y iOS en `applinks:` de Associated Domains; la app solo acepta enlaces `https` de ese host. Sin valor: `localhost` en el manifiesto y los entitlements, y la app acepta cualquier host (solo para builds locales) |
| [POR DEFINIR: credenciales de proveedores de pagos y facturación] | api | pruebas, producción | [POR DEFINIR] | sí | — |

Secretos de la épica MEDK-12, solo por nombre: `KUADOR_JWT_SECRET` (firma de los tokens de acceso; el `preAuthToken`, los refresh tokens y los tokens de un solo uso son opacos y se guardan como hash, sin clave propia), `KUADOR_SMTP_PASSWORD` y `KUADOR_SMS_API_KEY`. Las rutas de los enlaces (`/invitation`, `/password-reset`, `/email-verification`) son fijas en `application.yaml` y en `web/docker-entrypoint.d/50-app-links.sh`: si cambian, cambian en ambos y en las apps.

### Deep links (App Links y Universal Links)

Los enlaces de invitación, recuperación y verificación tienen la forma `https://<dominio>/<ruta>#t=<token>` (ADR-014): el token va en el fragmento y no llega a los logs de nginx ni de la api. En el dominio de `KUADOR_APP_LINK_BASE_URL` el contenedor web sirve:

| Archivo | Content-Type | Contenido |
|---|---|---|
| `/.well-known/assetlinks.json` | `application/json` | Android: `package_name` y `sha256_cert_fingerprints` de `APP_LINKS_ANDROID_*` |
| `/.well-known/apple-app-site-association` (sin extensión) | `application/json` | iOS: `appIDs` de `APP_LINKS_IOS_APP_IDS` y las tres rutas |

- Los archivos del repo (`web/public/.well-known/`) no asocian ninguna app; `web/docker-entrypoint.d/50-app-links.sh` los reescribe al arrancar si recibe las variables y rechaza valores mal formados (el contenedor no arranca).
- nginx los sirve sin redirección, con `Cache-Control: max-age=3600`, y responde 404 a cualquier otro archivo de `/.well-known/` (no cae en `index.html`).
- Requisitos del dominio: HTTPS con certificado válido y sin redirecciones hacia esos archivos (Apple y Android no las siguen). Apple consulta los archivos a través de su CDN: tras cambiarlos puede tardar en verlos.
- La configuración de las apps la hace MEDK-23 con el mismo dominio, a través de `APP_LINK_HOST`: `autoVerify` en `movil/android/app/src/main/AndroidManifest.xml` y `com.apple.developer.associated-domains` en `movil/ios/Runner/Runner.entitlements` (la capacidad Associated Domains debe estar activa en el App ID del Team ID [POR DEFINIR]).
- En local no hay verificación de dominio (necesita HTTPS público); los enlaces se prueban abriendo la URL de la web o con `adb shell am start -a android.intent.action.VIEW -d "<url>"` y `xcrun simctl openurl booted "<url>"`.

Generar valores aleatorios para secretos locales: `openssl rand -base64 24` (clave de base) y `openssl rand -base64 48` (firma JWT). Cada entorno tiene sus propios valores; nunca se reutilizan los de local.

### Cuenta inicial del SUPER_ADMIN

La api crea la cuenta al arrancar solo si no existe ningún SUPER_ADMIN, con `KUADOR_BOOTSTRAP_ADMIN_EMAIL` y `KUADOR_BOOTSTRAP_ADMIN_PASSWORD`, y la marca para cambiar la clave en el primer ingreso. Si faltan las variables, arranca sin crearla y lo avisa en el log (sin datos).

1. Local: escribir ambas variables en `.env` (nunca en `.env.example`, código, documentos ni comentarios del tablero).
2. Pruebas y producción: cargar `KUADOR_BOOTSTRAP_ADMIN_PASSWORD` como secreto en el gestor del entorno y `KUADOR_BOOTSTRAP_ADMIN_EMAIL` como variable normal.
3. Arrancar la api, ingresar con la cuenta y cambiar la clave de inmediato.
4. Después del primer arranque, borrar `KUADOR_BOOTSTRAP_ADMIN_PASSWORD` del entorno: ya no se usa y la clave vigente es la cambiada.

## Paso a paso

### Local

#### Base de datos local

Prerrequisitos: Docker con Compose v2 (Docker Desktop en macOS/Windows o Docker Engine en Linux) y el puerto 5432 libre en el equipo (o cambiar `DB_PORT`).

1. `cp .env.example .env` y completar `DB_PASSWORD` con cualquier valor local (por ejemplo `openssl rand -base64 24`). `DB_NAME` y `DB_USERNAME` ya traen `medical`; `.env` está en `.gitignore` y no debe versionarse.
2. `docker compose -f compose.local.yaml --env-file .env up -d --wait`: levanta PostgreSQL 18 en `127.0.0.1:${DB_PORT}` (por defecto 5432) con el volumen `medical-kuador_db-data` y espera al healthcheck (`pg_isready`). Sin `DB_PASSWORD` no arranca y el mensaje dice qué variable falta.
3. `docker compose -f compose.local.yaml ps` debe mostrar `db` como `healthy`.
4. Detener: `docker compose -f compose.local.yaml down` (con `-v` se borran los datos locales).

Si también se usan `mailpit` u otros servicios de `compose.yaml`, Compose avisa de «orphan containers» al usar `compose.local.yaml`: es esperado porque ambos archivos comparten proyecto.

#### Entorno completo

1. Requisitos: Docker con Compose v2, JDK 25, Node.js LTS, Flutter estable.
2. `cp .env.example .env` y completar `DB_PASSWORD`, `KUADOR_JWT_SECRET`, `KUADOR_BOOTSTRAP_ADMIN_EMAIL` y `KUADOR_BOOTSTRAP_ADMIN_PASSWORD`.
3. `docker compose up -d db mailpit` y esperar a que `docker compose ps` muestre `db` y `mailpit` como `healthy`. Los correos que envía la api se ven en `http://localhost:8025`.
4. Comprobar la base: `docker compose exec db psql -U "$DB_USERNAME" -d "$DB_NAME" -c 'select version();'` (con las variables de `.env` cargadas) o con cualquier cliente en `127.0.0.1:${DB_PORT}`.
5. api en el host: `set -a; . ./.env; set +a` y luego `cd api && ./mvnw spring-boot:run`.
6. web en el host: `cd web && npm install && npm start` (`http://localhost:4200`; el servidor de desarrollo hace proxy de `/api` y `/actuator` a `API_PROXY_TARGET`, por defecto `http://localhost:8080`, sin reescribir la ruta, así que no hace falta CORS). Con la api en otro puerto o equipo: `API_PROXY_TARGET=http://localhost:9090 npm start`. Si la api no responde, el proxy contesta 502 y la web muestra «No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.».
   - Sin api: `cd web && npm run dev:mock` levanta los mocks Prism de los contratos (`identity.openapi.yaml` en `http://localhost:4010` y `user-registration.openapi.yaml` en `http://localhost:4011`) y la web en `http://localhost:4200` con proxy de `/api/v1` a los mocks. Mientras la api no implemente `user-registration.openapi.yaml` (MEDK-14 a MEDK-16), el login de la web solo funciona contra el mock.
7. Alternativa con todo en contenedores: `docker compose --profile full up -d --build`.
8. Detener: `docker compose down` (con `-v` se borran los datos locales).

Si el puerto 5432 del equipo ya está ocupado, cambiar `DB_PORT` y el puerto de `DB_URL` en `.env` (por ejemplo 55432).

### Primera vez (pruebas y producción)

1. Preparar el servidor [POR DEFINIR: proveedor y plataforma].
2. Crear las aplicaciones (api, web) y la base PostgreSQL 18 por entorno.
3. Cargar variables y secretos en el gestor del entorno, sin escribir sus valores en el repo; `SPRING_PROFILES_ACTIVE=prod`. Incluye `KUADOR_WEB_BASE_URL`, `KUADOR_APP_LINK_BASE_URL`, `KUADOR_SMTP_*`, `KUADOR_MAIL_FROM` y, cuando haya proveedor, `KUADOR_SMS_*`; en el contenedor web, `APP_LINKS_*`.
   - Apuntar el dominio de deep links a la web con HTTPS y comprobar `curl -sI https://<dominio>/.well-known/apple-app-site-association` (200, `application/json`, sin redirección) y lo mismo con `assetlinks.json`.
4. Primer despliegue en pruebas, cambiar la clave del SUPER_ADMIN y retirar `KUADOR_BOOTSTRAP_ADMIN_PASSWORD`; verificar; luego producción.

### Cada despliegue

Al fusionar en `develop` se construye y despliega a pruebas; al fusionar en `main` se construye y despliega a producción. Las migraciones de base (Flyway) las aplica la api al arrancar. [POR DEFINIR: orden de despliegue de las piezas.]

## CI/CD

### Integración continua (GitHub Actions)

Un solo workflow, `.github/workflows/ci.yml` (nombre `ci`), corre en cada Pull Request hacia `develop` o `main` y en cada push a esas ramas. No usa secretos: la api prueba con Testcontainers (perfil `test`) y el resto no necesita credenciales.

| Check | Corre si cambia | Qué hace |
|---|---|---|
| `ci` | siempre | Comprueba que existan los documentos de la metodología |
| `api` | `api/**`, `contracts/**` | JDK 25 (Temurin) y `./mvnw -B verify` en `api/`; Docker del runner para Testcontainers (PostgreSQL) |
| `web` | `web/**`, `contracts/**` | Node.js 24, `npm ci`, `npm run api:check` (tipos generados al día con el contrato), `npm run build`, archivos de `/.well-known/` válidos y dentro del build, `scripts/test-app-links.sh` (el script de arranque escribe JSON válido y rechaza valores mal formados), `npm test -- --watch=false` |
| `movil` | `movil/**`, `contracts/**` | Flutter estable fijado en `3.47.9` (la 3.50 depreca `package:flutter/material.dart` y `flutter analyze` falla; subir la versión es una tarea aparte que migra el código), `flutter pub get`, `flutter analyze`, `flutter test` |
| `contract` | `contracts/**`, `redocly.yaml`, `.redocly.lint-ignore.yaml` | `npx @redocly/cli@2 lint` con la configuración de la raíz, sobre `identity.openapi.yaml` y `user-registration.openapi.yaml` |

Validación del contrato: `contract` revisa la estructura y las reglas de Redocly; `api` corre `UserRegistrationContractTest` en `./mvnw -B verify` (permiso declarado en cada operación, operaciones planificadas presentes, `ErrorCode` y `PermissionCode` iguales al código); `web` comprueba que los tipos generados coincidan con el contrato. `movil` corre `flutter analyze` y `flutter test` también cuando cambia el contrato.

Cualquier cambio en `.github/workflows/ci.yml` corre los cinco checks. El filtro por ruta lo hace el job `changes` dentro del workflow: un check cuya pieza no cambió queda «skipped», y GitHub lo cuenta como aprobado, así los checks pueden ser requeridos sin bloquear PRs que no tocan esa pieza.

Checks requeridos hacia `develop` (y `main`): `ci`, `api`, `web`, `movil` y `contract`, con «Require branches to be up to date». Se configuran en *Settings → Branches* (o *Rules → Rulesets*) del repo. [POR DEFINIR: hoy el repo es privado en un plan de la organización sin protección de ramas (la API responde 403); hace falta subir el plan a GitHub Team o hacer público el repo para poder marcarlos como requeridos.]

Comprobar en local lo mismo que el CI: ver «Comandos del proyecto» en `AGENTS.md`; el contrato con `npx @redocly/cli@2 lint` desde la raíz.

### Entrega continua

[POR DEFINIR: publicación de imágenes y despliegue de `develop` a pruebas y de `main` a producción.]

## Respaldos y vuelta atrás

[POR DEFINIR: qué se respalda (como mínimo la base PostgreSQL, por contener datos de salud), frecuencia, dónde se guarda, pruebas de restauración y cómo volver a la versión anterior.] En local no hay respaldo: el volumen `db-data` es descartable.

## Verificación

- Local: `docker compose ps` muestra `db` y `mailpit` como `healthy` y la base responde a `select version();`; `http://localhost:8025` abre Mailpit.
- web: `curl -sI <web>/.well-known/assetlinks.json` y `curl -sI <web>/.well-known/apple-app-site-association` responden 200 con `Content-Type: application/json`; `curl -sI <web>/.well-known/otro` responde 404.
- api: `POST /api/v1/auth/login` con la cuenta inicial responde 200 con `scope: PLATFORM` y `mustChangePassword: true`; hasta cambiar la clave (`POST /api/v1/auth/password`) solo responden `/auth/password` y `/me`.
- api: `GET /api/v1/system/info` (público) responde 200 con `status` y `database` en `UP`; si la base no responde, devuelve `DOWN` en ambos (sigue respondiendo 200). Las migraciones Flyway (`api/src/main/resources/db/migration`) se aplican al arrancar.
- Punta a punta (local): `scripts/smoke-test.sh` con el perfil `full` arriba; debe terminar en 0. Ver «Prueba de humo».
- [POR DEFINIR: revisión de logs y prueba de punta a punta en pruebas y producción (el script sirve apuntando `API_URL`/`WEB_URL` al entorno, pero cambia la clave del SUPER_ADMIN en su primera corrida: ahí se usa solo después de cambiarla a mano).]

### Prueba de humo

`scripts/smoke-test.sh` (requiere `bash`, `curl`, `jq` y `openssl`) recorre la api real y, si `WEB_URL` no está vacío, también el contenedor web y su proxy `/api/`:

1. `GET /system/info` con `status` y `database` en `UP`; `GET /me` sin token responde 401.
2. Autorregistro de dos clínicas (A y B) con correos `@example.test` únicos por corrida; correo repetido responde 409 `EMAIL_TAKEN`.
3. Login (clave errónea 401; dueño con `scope: TENANT`) y `GET /me` con OWNER y ADMIN en su propia clínica.
4. CRUD de roles en A: catálogo solo `TENANT`, roles OWNER y ADMIN, crear, nombre repetido (409), permiso fuera del catálogo o de plataforma (400), leer, editar, versión vieja (409), editar o borrar OWNER (409 `SYSTEM_ROLE_IMMUTABLE`), borrar.
5. Entre tenants: B lee, edita o borra el rol de A y recibe 404; el listado de B no incluye roles de A y el rol de A queda intacto.
6. Separación de alcance: token `TENANT` en `/platform/**` responde 403 `FORBIDDEN`.
7. SUPER_ADMIN: en la primera corrida sobre una base nueva ingresa con la clave inicial (`mustChangePassword: true`), comprueba que solo `/me` y `/auth/password` responden (resto 403 `PASSWORD_CHANGE_REQUIRED`), rechaza una clave débil, cambia a `SMOKE_SUPER_ADMIN_NEW_PASSWORD` y verifica que la inicial ya no sirve. En corridas siguientes verifica que la inicial se rechaza. Siempre: ingreso con la clave nueva, catálogo completo en `/platform/permissions` y 403 `FORBIDDEN` en endpoints de clínica.
8. Web: sirve la app, `config.json` tiene `API_BASE_URL`, la api responde a través de esa URL (también con token) y las rutas profundas devuelven `index.html`.

Cómo correrla en local:

```bash
docker compose --profile full up -d --build   # con .env completo, incluido SMOKE_SUPER_ADMIN_NEW_PASSWORD
scripts/smoke-test.sh                          # imprime PASS/FAIL por paso; termina en 0 si todo pasa
```

Si en `.env` se cambia la clave inicial o la nueva después de la primera corrida, la prueba ya no puede ingresar como SUPER_ADMIN: empezar con una base nueva (`docker compose down -v`). Nunca imprime claves ni tokens; los datos que crea (clínicas `Smoke Clinic A/B`) quedan en la base local.

Resultado de MEDK-11 (2026-11-19, perfil `full` en macOS con Docker 29): 45 pasos en verde en la primera corrida sobre base nueva y 39 en las siguientes; código de salida 0. Además se verificó a mano:

- Web (contenedor, Chrome sin interfaz): estado del sistema «Disponible», autorregistro que lleva a «Roles» con OWNER y ADMIN, creación de un rol que aparece en el listado, SUPER_ADMIN enviado a «Cambiar contraseña» sin poder saltarlo, luego al catálogo de plataforma y a «Sin permiso» si intenta ver roles de clínica. Sin errores en la consola.
- Base caída (`docker compose stop db`): `GET /system/info` responde 200 con `DOWN` y la web lo muestra como no disponible; el resto de la api responde 500 `INTERNAL_ERROR` sin detalles internos, que la web traduce a un mensaje en español.
- Base caída, en la web: «Estado del sistema» muestra la base «No disponible» con el aviso «Algunos servicios no están disponibles…» y el login muestra «Ocurrió un error inesperado. Intenta de nuevo en unos minutos.». Observación: con la base caída cada solicitud tarda unos 10–13 s en responder (espera de conexión a la base); queda anotado para la api, no bloquea.
- Movil (simulador iPhone 17 Pro, iOS 26, Xcode 27, `--dart-define=API_BASE_URL=http://localhost:8080/api/v1`): la pantalla «Estado del sistema» muestra «El sistema funciona correctamente» con aplicación y base «Disponible» y versión 0.1.0. Para compilar en Xcode 27 se subió el mínimo de iOS de 13.0 a 15.0 (Xcode 27 ya no admite 13.0). En el emulador de Android la URL es `http://10.0.2.2:8080/api/v1`; no se probó en Android en esta tarea.

## Cambios

| Fecha | Qué cambió | Tarjeta |
|---|---|---|
| {date} | Primera versión | — |
| 2026-11-19 | Entorno local con Docker Compose (PostgreSQL 18 y perfil `full` con api y web), `.env.example`, perfiles `local`/`test`/`prod`, variables y secretos de la api (renombra `SPRING_DATASOURCE_*` a `DB_*` según el plan), carga de la clave inicial del SUPER_ADMIN como secreto | MEDK-3 |
| 2026-11-19 | Esqueleto de la api: migración base Flyway `V1__baseline.sql` aplicada al arrancar, comprobación de salud `GET /api/v1/system/info`, `./mvnw -B verify` necesita Docker (Testcontainers). Sin variables ni secretos nuevos | MEDK-4 |
| 2026-11-19 | Autorregistro, login JWT, `GET /me`, cambio de clave y creación de la cuenta inicial SUPER_ADMIN al arrancar. `KUADOR_JWT_SECRET` pasa a ser obligatoria (la api no arranca sin ella o si tiene menos de 32 bytes); `KUADOR_BOOTSTRAP_ADMIN_*` ya se usan. Sin variables, puertos ni servicios nuevos | MEDK-5 |
| 2026-11-19 | Web Angular: `config.json` en tiempo de ejecución con `API_BASE_URL` (por defecto `/api/v1`, mismo origen), reescritura de `config.json` al arrancar el contenedor web si recibe `API_BASE_URL`, proxy local a la api (`npm start`) y mock Prism del contrato en el puerto 4010 (`npm run dev:mock`) | MEDK-7 |
| 2026-11-19 | CI por pieza en GitHub Actions (`ci`, `api`, `web`, `movil`, `contract`) con filtros por ruta; Flutter `3.47.9` fijado en CI; checks a marcar como requeridos hacia `develop` y `main` | MEDK-10 |
| 2026-11-19 | Prueba de humo de punta a punta `scripts/smoke-test.sh` (api y web del perfil `full`), variable local `SMOKE_SUPER_ADMIN_NEW_PASSWORD` en `.env.example`, resultado de la verificación manual de web y movil contra la api real; mínimo de iOS de la app movil sube de 13.0 a 15.0 para compilar con Xcode 27 | MEDK-11 |
| 2026-11-19 | Web: autenticación y sesión contra `user-registration.openapi.yaml`. Segundo mock Prism en el puerto local 4011 (`npm run dev:mock`). Rutas web de los enlaces de correo, con el token en el fragmento: `/invitation#t=…`, `/reset-password#t=…`, `/verify-email#t=…`; autorregistro en `/clinics/{publicCode}/register` (los correos y los deep links de la api deben apuntar a ellas). Sin variables ni secretos nuevos | MEDK-20 |
| 2026-11-19 | Mailpit en el entorno local (servicio `mailpit`, puertos 1025 y 8025); variables de duración de tokens, límite de intentos, enlaces (`KUADOR_WEB_BASE_URL`, `KUADOR_APP_LINK_BASE_URL`), SMTP (`KUADOR_SMTP_*`, `KUADOR_MAIL_FROM`) y SMS (`KUADOR_SMS_*`); secretos nuevos `KUADOR_SMTP_PASSWORD` y `KUADOR_SMS_API_KEY` (solo nombres); `/.well-known/assetlinks.json` y `apple-app-site-association` servidos como `application/json` y generados con `APP_LINKS_*`; CI: `movil` corre con cambios del contrato y `web` valida los archivos de deep links; dependencias Flyway y ShedLock documentadas | MEDK-19 |
| 2026-11-19 | Cuentas por clínica e invitaciones del personal: migración `V4__identity_clinic_accounts_and_invitations.sql` (crea `identity_user_account` y migra a ella el personal de `user_account` + `tenant_membership`, que se elimina; `user_account` queda solo para el SUPER_ADMIN; crea `identity_one_time_token` si no existe e `idempotency_record`). Dependencia `spring-boot-starter-mail` (adaptador SMTP de `NotificationSender`). Variable nueva `KUADOR_PLAN_USER_LIMIT` (opcional). Sin secretos, puertos ni servicios nuevos. V3 queda reservada para MEDK-15 | MEDK-16 |
| 2026-11-19 | App movil: autenticación, sesión y deep links. Variable de build nueva `APP_LINK_HOST` (`--dart-define`; en iOS, `ios/Flutter/AppLinks.local.xcconfig`) para App Links (`autoVerify`) y Associated Domains; `CONSENT_TEXT_VERSION` opcional (por defecto `2026-01`); tokens en Keychain/Keystore con `flutter_secure_storage`; dependencias `app_links`, `connectivity_plus` y `flutter_secure_storage`. Sin secretos ni servicios nuevos | MEDK-23 |
| 2026-11-19 | Autenticación de MEDK-12: login entre clínicas, elección de clínica y sucursal, refresh token rotativo, logout, recuperación de contraseña por clínica, `POST /auth/switch-branch` y `GET /me` con sucursal y módulos. `KUADOR_JWT_TTL` baja por defecto a `PT15M`. Migración `V3__identity_auth_tokens_and_login_throttle.sql` (tablas `identity_one_time_token`, `identity_refresh_token`, `identity_login_throttle`). Dependencia `spring-boot-starter-mail` (los correos salen por SMTP; Mailpit en local). `server.forward-headers-strategy: native`: detrás del proxy la IP del cliente sale de `X-Forwarded-For`, aceptado solo desde proxies de red interna; si el proxy de pruebas o producción no está en una red privada, configurar `server.tomcat.remoteip.internal-proxies`. Sin variables ni secretos nuevos | MEDK-15 |
| 2026-11-19 | Registro de pacientes, representantes, consentimiento LOPDP y autorregistro opcional: migración `V5__patients_registration.sql` (tablas `patients_patient`, `patients_guardian`, `patients_guardian_link`, `patients_consent` y `patients_registration_settings`, más acciones de auditoría nuevas). Endpoints `/patients`, `/patient-registration-settings`, `POST /public/clinics/{publicCode}/patient-registrations` y `POST /auth/email-verification/confirm`. El módulo `patients` queda habilitado como capacidad básica de todo plan (`enabledModules` de `/me` incluye `patients`). Variable nueva `KUADOR_CLINIC_TIME_ZONE` (opcional, `UTC` por defecto). El correo de verificación usa `KUADOR_EMAIL_VERIFICATION_TTL` y la ruta `email-verification-path`. Sin secretos, puertos, servicios ni dependencias nuevas | MEDK-17 |
| 2026-11-19 | `compose.local.yaml` con solo PostgreSQL (extiende el servicio `db` de `compose.yaml`: puerto 5432, volumen local y healthcheck) y subsección «Base de datos local» con prerrequisitos. Reutiliza `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD` de `.env.example` en lugar de `POSTGRES_*`; `.env` ya estaba en `.gitignore`. Sin variables, secretos, puertos ni servicios nuevos | MEDK-28 |
| 2026-11-19 | Web conectada a la api local por proxy: `web/proxy.conf.mjs` reemplaza a `proxy.conf.json` y reenvía `/api` y `/actuator` a la variable nueva `API_PROXY_TARGET` (por defecto `http://localhost:8080`); el proxy se aplica solo a la configuración `development` de `ng serve` (puerto 4200). `src/environments/environment.development.ts` con `apiBaseUrl` relativo (`/api/v1`); `config.json` sigue mandando en tiempo de ejecución. Sin secretos, servicios ni dependencias nuevas | MEDK-30 |
