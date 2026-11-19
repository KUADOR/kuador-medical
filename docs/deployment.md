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
| web | `npm run build` en `web/`; imagen con `web/Dockerfile` (nginx sin privilegios, sirve el build y hace proxy de `/api/` a la api) | 8080 en el contenedor; `WEB_PORT` en local (por defecto 4200) | [POR DEFINIR: pruebas / producción] |
| movil | Build de Flutter en `movil/` con `--dart-define=API_BASE_URL=…` [POR DEFINIR: distribución a tiendas o canal interno] | No aplica | No aplica |

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
| API_PORT | api | local | `.env` | no | Puerto del host para la api en el perfil `full` |
| WEB_PORT | web | local | `.env` | no | Puerto del host para la web en el perfil `full` |
| API_BASE_URL | web (`config.json`), movil (`--dart-define`) | todos | Configuración de cada pieza | no | URL de la api (`…/api/v1`) |
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
6. web en el host: `cd web && npm install && npm start` (`http://localhost:4200`).
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

[POR DEFINIR: workflow que construye (`./mvnw -B package`, `npm run build`), ejecuta pruebas (`./mvnw -B verify`, `npm test -- --watch=false`), publica y despliega cada rama en su entorno.]

## Respaldos y vuelta atrás

[POR DEFINIR: qué se respalda (como mínimo la base PostgreSQL, por contener datos de salud), frecuencia, dónde se guarda, pruebas de restauración y cómo volver a la versión anterior.] En local no hay respaldo: el volumen `db-data` es descartable.

## Verificación

- Local: `docker compose ps` muestra `db` como `healthy` y la base responde a `select version();`.
- api: `POST /api/v1/auth/login` con la cuenta inicial responde 200 con `scope: PLATFORM` y `mustChangePassword: true`; hasta cambiar la clave (`POST /api/v1/auth/password`) solo responden `/auth/password` y `/me`.
- api: `GET /api/v1/system/info` (público) responde 200 con `status` y `database` en `UP`; si la base no responde, devuelve `DOWN` en ambos (sigue respondiendo 200). Las migraciones Flyway (`api/src/main/resources/db/migration`) se aplican al arrancar.
- [POR DEFINIR: revisión de logs y una prueba de punta a punta por entorno, incluyendo que un usuario de un tenant no accede a datos de otro.]

## Cambios

| Fecha | Qué cambió | Tarjeta |
|---|---|---|
| {date} | Primera versión | — |
| 2026-11-19 | Entorno local con Docker Compose (PostgreSQL 18 y perfil `full` con api y web), `.env.example`, perfiles `local`/`test`/`prod`, variables y secretos de la api (renombra `SPRING_DATASOURCE_*` a `DB_*` según el plan), carga de la clave inicial del SUPER_ADMIN como secreto | MEDK-3 |
| 2026-11-19 | Esqueleto de la api: migración base Flyway `V1__baseline.sql` aplicada al arrancar, comprobación de salud `GET /api/v1/system/info`, `./mvnw -B verify` necesita Docker (Testcontainers). Sin variables ni secretos nuevos | MEDK-4 |
| 2026-11-19 | Autorregistro, login JWT, `GET /me`, cambio de clave y creación de la cuenta inicial SUPER_ADMIN al arrancar. `KUADOR_JWT_SECRET` pasa a ser obligatoria (la api no arranca sin ella o si tiene menos de 32 bytes); `KUADOR_BOOTSTRAP_ADMIN_*` ya se usan. Sin variables, puertos ni servicios nuevos | MEDK-5 |
