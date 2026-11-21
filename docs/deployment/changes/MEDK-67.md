# MEDK-67 · Invitación del OWNER, términos y 2FA TOTP

Notas de despliegue de la tarjeta MEDK-67 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **api, módulo `identity`:** al dar de alta una clínica, el OWNER inicial recibe una invitación por correo (enlace `/owner-invitation#t=<token>`, un solo uso, vence según el ajuste `OWNER_INVITATION_TTL_HOURS`, 72 h por defecto). Al aceptarla define su contraseña, acepta términos y política de datos y debe enrolar el segundo factor TOTP. Todo OWNER debe pasar el segundo factor en cada inicio de sesión (ADR-019).
- **Migración Flyway `V10__identity_owner_invitation_terms_and_two_factor.sql`:** tablas `identity_owner_invitation`, `identity_terms_acceptance` e `identity_two_factor`; columna `two_factor_id` en `identity_refresh_token`; nuevas acciones de auditoría. Se numeró V10 para no chocar con MEDK-68 (en paralelo): si MEDK-68 usa V9 y se integra después, Flyway la aplica sin problema en bases nuevas; en bases locales ya migradas a V10, recrear la base local. Si MEDK-68 también redefine `audit_event_action_check`, su lista debe incluir las acciones de esta migración (y viceversa).
- **Secreto nuevo `KUADOR_TOTP_ENCRYPTION_KEY`:** cifra con AES-256-GCM los secretos TOTP. Mínimo 32 bytes (por ejemplo `openssl rand -base64 48`). La api no arranca sin él. El plan y ADR-019 lo nombraban `MEDK_TOTP_ENCRYPTION_KEY`; se usa el prefijo `KUADOR_` que ya usa el repositorio (igual que MEDK-64 con SMTP). **Cambiarlo invalida todos los segundos factores enrolados** (hay que restablecerlos desde el panel de plataforma).
- **Variables nuevas, opcionales (con valor por defecto):**
  - `KUADOR_TWO_FACTOR_MAX_FAILURES` (5) y `KUADOR_TWO_FACTOR_LOCK_DURATION` (`PT15M`): códigos erróneos permitidos antes de bloquear temporalmente el segundo factor.
  - `KUADOR_TERMS_VERSION` y `KUADOR_DATA_POLICY_VERSION` (`2026-11`): versión vigente de los términos y de la política de datos que acepta el OWNER. [POR DEFINIR: texto final de términos y política de datos, provisto por Kuador]; al publicar un texto nuevo se sube la versión.
- **Variables reutilizadas:** `KUADOR_WEB_BASE_URL` / `KUADOR_APP_LINK_BASE_URL` (enlace de la invitación y del aviso de 2FA restablecido) y la configuración SMTP de MEDK-64.
- **Perfil local:** `LocalEnvironmentValidator` exige ahora también `KUADOR_TOTP_ENCRYPTION_KEY` (agregada a `.env.example` y `api/.env.example`). Los usuarios OWNER de la semilla local deberán enrolar el segundo factor con una app de autenticación en su primer inicio de sesión.

No hay servicios, puertos ni dominios nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Agregar `KUADOR_TOTP_ENCRYPTION_KEY` al `.env` (valor aleatorio propio, nunca commitearlo). Si la base local ya tenía una V9 de otra rama, recrearla (`docker compose -f compose.local.yaml down -v`). |
| Pruebas | Crear el secreto `KUADOR_TOTP_ENCRYPTION_KEY` en el gestor de secretos y pasarlo a la api. La migración V10 corre sola al arrancar. |
| Producción | Igual que pruebas, con un valor distinto y respaldado en el gestor de secretos (si se pierde, todos los OWNER deberán volver a enrolar). Los OWNER existentes deberán enrolar el segundo factor en su próximo inicio de sesión. |
