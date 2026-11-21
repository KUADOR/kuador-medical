# MEDK-68 · Ciclo de vida de la suscripción y reactivación

Notas de despliegue de la tarjeta MEDK-68 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Job nuevo dentro de la api** (`SubscriptionLifecycleJob`): aplica los plazos de la suscripción (fin de prueba → SUSPENDIDA, fin de período → VENCIDA, fin de gracia → SUSPENDIDA, fin de suspensión → CANCELADA) y envía los avisos de cambio de tarifa al OWNER. Corre cada hora (minuto 15, UTC) bajo el lock distribuido de ShedLock (tabla `shedlock`, la misma del job de mayoría de edad), así que con varias réplicas corre una sola vez. Es idempotente.
- **Variables nuevas, opcionales y sin secretos**:
  - `KUADOR_LIFECYCLE_JOB_CRON` (por defecto `0 15 * * * *`, cron en UTC; `-` lo desactiva). El plan de la épica la llamaba `MEDK_LIFECYCLE_JOB_CRON`; se publica con el prefijo `KUADOR_` que ya usa la api (como `KUADOR_SMTP_*`, ADR-005).
  - `KUADOR_LIFECYCLE_JOB_LOCK_AT_MOST_FOR` (por defecto `PT1H`): tope del lock si una réplica muere en medio de una corrida.
- **Migración Flyway `V11__platform_subscription_lifecycle.sql`**: tablas `subscription_transition` y `price_change_notice`, columna `created_at` en `subscription_period` y nuevas acciones de auditoría. Registra como transición inicial el estado de las suscripciones que ya existían.
- **Correos**: usa el canal de correo ya configurado (`KUADOR_SMTP_*`, `KUADOR_MAIL_FROM`); no agrega proveedor.

No hay servicios, dependencias, puertos, dominios ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio. Para ver el job más seguido, `KUADOR_LIFECYCLE_JOB_CRON=0 * * * * *` en `.env`. |
| Pruebas | Nada obligatorio; la migración corre sola al arrancar. Revisar en los logs la línea «Subscription lifecycle job finished» una vez por hora. |
| Producción | Nada obligatorio; la migración corre sola al arrancar. No desactivar el job: sin él ninguna suscripción vence ni se suspende. MEDK-71 (DevOps) puede ajustar el horario. |

## Para «Despliegue» al preparar el release

- **Variables**: agregar `KUADOR_LIFECYCLE_JOB_CRON` y `KUADOR_LIFECYCLE_JOB_LOCK_AT_MOST_FOR` con sus valores por defecto.
- **Fila en «Cambios»**: «Ciclo de vida de la suscripción: job horario `SubscriptionLifecycleJob` (ShedLock), variables `KUADOR_LIFECYCLE_JOB_CRON` y `KUADOR_LIFECYCLE_JOB_LOCK_AT_MOST_FOR`, migración V11 | MEDK-68».
