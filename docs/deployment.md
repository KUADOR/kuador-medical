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

- sin perfil: solo `db` (`docker compose up -d db`).
- `full`: `db`, `api` y `web` en contenedores (`docker compose --profile full up -d --build`).

## Infraestructura

[POR DEFINIR: proveedor, sistema operativo y tamaño del servidor; plataforma de despliegue (por ejemplo Dokploy o Docker Compose); proxy y certificados.]

## Piezas

| Pieza | Cómo se construye | Puerto | Dominio por entorno |
|---|---|---|---|
| api | `./mvnw -B package` en `api/`; imagen con `api/Dockerfile` (JRE 25, usuario sin privilegios) | 8080 en el contenedor; `API_PORT` en local (por defecto 8080) | [POR DEFINIR: pruebas / producción] |
| web | `npm run build` en `web/` (Node.js `^22.22.3` o `^24.15.0`); imagen con `web/Dockerfile` (nginx sin privilegios, sirve el build y hace proxy de `/api/` a la api; al arrancar reescribe `config.json` si recibe `API_BASE_URL`) | 8080 en el contenedor; `WEB_PORT` en local (por defecto 4200) | [POR DEFINIR: pruebas / producción] |
| movil | Build de Flutter en `movil/` con `--dart-define=API_BASE_URL=…` (iOS mínimo 15.0) [POR DEFINIR: distribución a tiendas o canal interno] | No aplica | No aplica |

En local los puertos se publican solo en `127.0.0.1`.

## Dependencias

| Servicio | Versión | Para qué | Cómo se levanta | Datos que guarda |
|---|---|---|---|---|
| PostgreSQL | 18 (`postgres:18-alpine`) | Base de datos de la api | Local: servicio `db` de `compose.yaml`, puerto `DB_PORT` (por defecto 5432). Pruebas automáticas: Testcontainers. Pruebas y producción: [POR DEFINIR: contenedor o servicio administrado] | Datos de todos los tenants, incluidos datos de salud. Local: volumen `db-data`. [POR DEFINIR: volumen y respaldo en pruebas y producción] |
| Proveedores de notificaciones y pagos | [POR DEFINIR] | Envío por varios canales y cobro de suscripciones | Servicios externos [POR DEFINIR: proveedores] | Ninguno propio |

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
| KUADOR_JWT_SECRET | api | local, pruebas, producción | `.env` / gestor de secretos | sí | Firma de los tokens (≥ 256 bits, es decir ≥ 32 bytes; distinta por entorno). Obligatoria: sin ella la api no arranca |
| KUADOR_JWT_TTL | api | todos | `.env` / variables del entorno | no | Vida del token (por defecto `PT30M`) |
| KUADOR_BOOTSTRAP_ADMIN_EMAIL | api | local, pruebas, producción | `.env` / variables del entorno | no | Correo de la cuenta inicial SUPER_ADMIN (el acordado en MEDK-1) |
| KUADOR_BOOTSTRAP_ADMIN_PASSWORD | api | local, pruebas, producción | `.env` / gestor de secretos | sí | Clave inicial del SUPER_ADMIN; ver «Cuenta inicial del SUPER_ADMIN» |
| KUADOR_CORS_ALLOWED_ORIGINS | api | local, pruebas, producción | `.env` / variables del entorno | no | Origen permitido de la web |
| SMOKE_SUPER_ADMIN_NEW_PASSWORD | `scripts/smoke-test.sh` | local | `.env` | sí | Clave que la prueba de humo pone al SUPER_ADMIN en su primera corrida sobre una base nueva (≥ 12 caracteres) y con la que ingresa en las siguientes. Solo local; la api la ignora |
| API_PORT | api | local | `.env` | no | Puerto del host para la api en el perfil `full` |
| WEB_PORT | web | local | `.env` | no | Puerto del host para la web en el perfil `full` |
| API_BASE_URL | web (`config.json`), movil (`--dart-define`) | todos | web: `public/config.json` (por defecto `/api/v1`, mismo origen) o variable del contenedor web, que reescribe `config.json` al arrancar; movil: `--dart-define` | no | URL de la api (`…/api/v1`). En la web, una URL de otro origen exige incluir el origen de la web en `KUADOR_CORS_ALLOWED_ORIGINS` |
| [POR DEFINIR: credenciales de proveedores de notificaciones, pagos y facturación] | api | pruebas, producción | [POR DEFINIR] | sí | — |

Generar valores aleatorios para secretos locales: `openssl rand -base64 24` (clave de base) y `openssl rand -base64 48` (firma JWT). Cada entorno tiene sus propios valores; nunca se reutilizan los de local.

### Cuenta inicial del SUPER_ADMIN

La api crea la cuenta al arrancar solo si no existe ningún SUPER_ADMIN, con `KUADOR_BOOTSTRAP_ADMIN_EMAIL` y `KUADOR_BOOTSTRAP_ADMIN_PASSWORD`, y la marca para cambiar la clave en el primer ingreso. Si faltan las variables, arranca sin crearla y lo avisa en el log (sin datos).

1. Local: escribir ambas variables en `.env` (nunca en `.env.example`, código, documentos ni comentarios del tablero).
2. Pruebas y producción: cargar `KUADOR_BOOTSTRAP_ADMIN_PASSWORD` como secreto en el gestor del entorno y `KUADOR_BOOTSTRAP_ADMIN_EMAIL` como variable normal.
3. Arrancar la api, ingresar con la cuenta y cambiar la clave de inmediato.
4. Después del primer arranque, borrar `KUADOR_BOOTSTRAP_ADMIN_PASSWORD` del entorno: ya no se usa y la clave vigente es la cambiada.

## Paso a paso

### Local

1. Requisitos: Docker con Compose v2, JDK 25, Node.js LTS, Flutter estable.
2. `cp .env.example .env` y completar `DB_PASSWORD`, `KUADOR_JWT_SECRET`, `KUADOR_BOOTSTRAP_ADMIN_EMAIL` y `KUADOR_BOOTSTRAP_ADMIN_PASSWORD`.
3. `docker compose up -d db` y esperar a que `docker compose ps` muestre `db` como `healthy`.
4. Comprobar la base: `docker compose exec db psql -U "$DB_USERNAME" -d "$DB_NAME" -c 'select version();'` (con las variables de `.env` cargadas) o con cualquier cliente en `127.0.0.1:${DB_PORT}`.
5. api en el host: `set -a; . ./.env; set +a` y luego `cd api && ./mvnw spring-boot:run`.
6. web en el host: `cd web && npm install && npm start` (`http://localhost:4200`; el servidor de desarrollo hace proxy de `/api` a `http://localhost:8080`).
   - Sin api: `cd web && npm run dev:mock` levanta los mocks Prism de los contratos (`identity.openapi.yaml` en `http://localhost:4010` y `user-registration.openapi.yaml` en `http://localhost:4011`) y la web en `http://localhost:4200` con proxy de `/api/v1` a los mocks. Mientras la api no implemente `user-registration.openapi.yaml` (MEDK-14 a MEDK-16), el login de la web solo funciona contra el mock.
7. Alternativa con todo en contenedores: `docker compose --profile full up -d --build`.
8. Detener: `docker compose down` (con `-v` se borran los datos locales).

Si el puerto 5432 del equipo ya está ocupado, cambiar `DB_PORT` y el puerto de `DB_URL` en `.env` (por ejemplo 55432).

### Primera vez (pruebas y producción)

1. Preparar el servidor [POR DEFINIR: proveedor y plataforma].
2. Crear las aplicaciones (api, web) y la base PostgreSQL 18 por entorno.
3. Cargar variables y secretos en el gestor del entorno, sin escribir sus valores en el repo; `SPRING_PROFILES_ACTIVE=prod`.
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
| `web` | `web/**`, `contracts/**` | Node.js 24, `npm ci`, `npm run api:check` (tipos generados al día con el contrato), `npm run build`, `npm test -- --watch=false` |
| `movil` | `movil/**` | Flutter estable fijado en `3.47.9` (la 3.50 depreca `package:flutter/material.dart` y `flutter analyze` falla; subir la versión es una tarea aparte que migra el código), `flutter pub get`, `flutter analyze`, `flutter test` |
| `contract` | `contracts/**`, `redocly.yaml`, `.redocly.lint-ignore.yaml` | `npx @redocly/cli@2 lint` con la configuración de la raíz |

Cualquier cambio en `.github/workflows/ci.yml` corre los cinco checks. El filtro por ruta lo hace el job `changes` dentro del workflow: un check cuya pieza no cambió queda «skipped», y GitHub lo cuenta como aprobado, así los checks pueden ser requeridos sin bloquear PRs que no tocan esa pieza.

Checks requeridos hacia `develop` (y `main`): `ci`, `api`, `web`, `movil` y `contract`, con «Require branches to be up to date». Se configuran en *Settings → Branches* (o *Rules → Rulesets*) del repo. [POR DEFINIR: hoy el repo es privado en un plan de la organización sin protección de ramas (la API responde 403); hace falta subir el plan a GitHub Team o hacer público el repo para poder marcarlos como requeridos.]

Comprobar en local lo mismo que el CI: ver «Comandos del proyecto» en `AGENTS.md`; el contrato con `npx @redocly/cli@2 lint` desde la raíz.

### Entrega continua

[POR DEFINIR: publicación de imágenes y despliegue de `develop` a pruebas y de `main` a producción.]

## Respaldos y vuelta atrás

[POR DEFINIR: qué se respalda (como mínimo la base PostgreSQL, por contener datos de salud), frecuencia, dónde se guarda, pruebas de restauración y cómo volver a la versión anterior.] En local no hay respaldo: el volumen `db-data` es descartable.

## Verificación

- Local: `docker compose ps` muestra `db` como `healthy` y la base responde a `select version();`.
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
