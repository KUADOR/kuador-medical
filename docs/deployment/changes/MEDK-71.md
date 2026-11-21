# MEDK-71 · Secretos, job y retención de respaldos

Notas de despliegue de la tarjeta MEDK-71 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Nombres:** la tarjeta y el plan de la épica usaban el prefijo `MEDK_`. Se mantiene el prefijo `KUADOR_` que ya usa la api (igual que MEDK-64, MEDK-67 y MEDK-68): `KUADOR_SMTP_PASSWORD`, `KUADOR_TOTP_ENCRYPTION_KEY`, `KUADOR_SMTP_*`, `KUADOR_MAIL_FROM`, `KUADOR_WEB_BASE_URL` y `KUADOR_LIFECYCLE_JOB_CRON`. No se crean variables `MEDK_*`.
- **api, arranque en el perfil `prod` (pruebas y producción):** nuevo `ProdEnvironmentValidator`. La api no arranca si falta o está vacía alguna de `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KUADOR_JWT_SECRET`, `KUADOR_TOTP_ENCRYPTION_KEY`, `KUADOR_SMTP_HOST`, `KUADOR_MAIL_FROM` o `KUADOR_WEB_BASE_URL`; y, salvo `KUADOR_SMTP_AUTH=false`, también `KUADOR_SMTP_USERNAME` y `KUADOR_SMTP_PASSWORD`. El error nombra las variables, nunca sus valores. Antes, sin SMTP o sin URL de la web la api arrancaba y fallaba recién al enviar la primera invitación.
- **Respaldos de la base:** `scripts/db-backup.sh` (POSIX sh, corre con el cliente de PostgreSQL 18 o dentro de `postgres:18-alpine`): `pg_dump` en formato custom a `BACKUP_DIR`, comprueba que el dump se pueda leer (`pg_restore --list`) y borra los dumps con más de `BACKUP_RETENTION_DAYS` días (30 por defecto, decisión del Equipo Kuador en MEDK-49: los respaldos se conservan 30 días tras el borrado definitivo). Si el dump falla, no purga nada. Prueba: `scripts/test-db-backup.sh` (Docker).
- **Variables nuevas del respaldo** (las lee el script, no la api): `BACKUP_DIR` (obligatoria), `BACKUP_RETENTION_DAYS` (30), `BACKUP_PREFIX` (`medical`) y la conexión estándar de PostgreSQL `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` y `PGPASSWORD` (secreto: el mismo valor que `DB_PASSWORD` o, mejor, un usuario de solo lectura para respaldos).
- **Job del ciclo de vida (MEDK-68):** sin cambios de código. Horario por defecto `0 15 * * * *` (cada hora, minuto 15, UTC) con ShedLock; se recomienda dejar el valor por defecto en pruebas y producción.

No hay servicios, puertos ni dominios nuevos.

## Qué hacer al desplegar

Valores: nunca en el repo ni en este documento; distintos por entorno.

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio (el perfil `local` no cambia). Para probar el respaldo: `scripts/test-db-backup.sh`. |
| Pruebas | 1) En el gestor de secretos del entorno [POR DEFINIR: cuál], crear `KUADOR_SMTP_PASSWORD` y `KUADOR_TOTP_ENCRYPTION_KEY` (`openssl rand -base64 48`). 2) Como variables normales: `KUADOR_SMTP_HOST`, `KUADOR_SMTP_PORT`, `KUADOR_SMTP_USERNAME`, `KUADOR_SMTP_AUTH`, `KUADOR_SMTP_STARTTLS`, `KUADOR_MAIL_FROM`, `KUADOR_WEB_BASE_URL` y, si se quiere otro horario, `KUADOR_LIFECYCLE_JOB_CRON`. 3) Programar `scripts/db-backup.sh` una vez al día [POR DEFINIR: plataforma y almacenamiento de los dumps, fuera del servidor de la base y cifrado] con `BACKUP_RETENTION_DAYS=30`. 4) Verificar (ver abajo). |
| Producción | Igual que pruebas, con valores propios. `KUADOR_TOTP_ENCRYPTION_KEY` debe quedar respaldada en el gestor: si se pierde o cambia, todos los OWNER deben volver a enrolar el segundo factor. Requiere aprobación de una persona antes de tocar producción. |

## Cómo se verifica

1. Arranque: con `SPRING_PROFILES_ACTIVE=prod` y alguna variable de la lista sin definir, la api no arranca y el mensaje «Missing or blank environment variables: …» la nombra; con todas, `GET /actuator/health` responde 200 `UP`.
2. Job: en los logs aparece «Subscription lifecycle job finished» una vez por hora (minuto 15 UTC) o según `KUADOR_LIFECYCLE_JOB_CRON`.
3. Respaldo: tras la corrida diaria hay un `medical-<fecha>.dump` nuevo en `BACKUP_DIR`, ninguno con más de 30 días, y una restauración de prueba (`pg_restore -d <base vacía>`) termina sin errores. [POR DEFINIR: frecuencia de la prueba de restauración; se sugiere mensual.]

Verificado en local (2026-11-21): api empaquetada con perfil `prod` contra PostgreSQL 18 en contenedor; sin `KUADOR_TOTP_ENCRYPTION_KEY`, SMTP, remitente ni URL de la web no arranca y los nombra; con todo, health `UP` y el job corrió cada minuto con `KUADOR_LIFECYCLE_JOB_CRON=0 * * * * *`. `scripts/test-db-backup.sh`: 12 comprobaciones en verde.

## Pendiente de una persona (fuera del repo)

- Elegir el gestor de secretos, la plataforma de pruebas y producción, y dónde se guardan los respaldos («Despliegue» § Infraestructura y § Respaldos siguen [POR DEFINIR]).
- Cargar los secretos y variables de la tabla en cada entorno y programar el respaldo diario. Este agente no tiene acceso a esos entornos.

## Para «Despliegue» al preparar el release

- **Variables y secretos:** marcar `KUADOR_TOTP_ENCRYPTION_KEY`, `KUADOR_SMTP_HOST`, `KUADOR_MAIL_FROM`, `KUADOR_WEB_BASE_URL` (y `KUADOR_SMTP_USERNAME`/`KUADOR_SMTP_PASSWORD` con autenticación SMTP) como obligatorias en pruebas y producción; agregar `BACKUP_DIR`, `BACKUP_RETENTION_DAYS`, `BACKUP_PREFIX` y `PG*`.
- **Respaldos y vuelta atrás:** base PostgreSQL con `scripts/db-backup.sh`, diario, retención de 30 días, restauración con `pg_restore`.
- **Fila en «Cambios»:** «Arranque del perfil `prod` exige variables y secretos de correo, TOTP y enlaces; respaldo de la base con `scripts/db-backup.sh` y retención de 30 días; se mantienen los nombres `KUADOR_*` en lugar de `MEDK_*` | MEDK-71».
