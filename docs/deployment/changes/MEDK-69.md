# MEDK-69 · Cambio de plan, capacidades y modo solo lectura

Notas de despliegue de la tarjeta MEDK-69 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Migración Flyway `V12__platform_plan_changes.sql`**: tabla `plan_change` (cambios de plan aplicados o programados para la renovación, con el prorrateo de la subida), nuevas acciones de auditoría (`PLAN_CHANGE_APPLIED`, `PLAN_CHANGE_SCHEDULED`, `PLAN_CHANGE_CANCELLED`) y el permiso `subscription.read` para los roles OWNER y ADMIN de las clínicas que ya existen.
- **Cambia el significado de `KUADOR_PLAN_USER_LIMIT`** (variable existente, opcional, sin secreto): ya no es el límite de usuarios de todas las clínicas. Las clínicas con suscripción toman `MAX_USERS` de su plan; la variable solo aplica a las clínicas sin suscripción (registradas antes del alta por la plataforma). Vacío o `0` = sin límite, como antes.
- **Modo solo lectura en la api**: una clínica SUSPENDIDA o CANCELADA (en retención) solo lee; toda escritura responde `403 TENANT_READ_ONLY`. No requiere configuración.

No hay servicios, dependencias, puertos, dominios, secretos ni variables nuevas.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio; la migración corre sola al arrancar. |
| Pruebas | Nada obligatorio. Si `KUADOR_PLAN_USER_LIMIT` estaba definida, revisar que el plan de cada clínica de prueba tenga el `MAX_USERS` esperado. |
| Producción | Nada obligatorio; la migración corre sola al arrancar. Antes de desplegar, revisar que los planes tengan los límites y módulos correctos: desde este cambio se aplican (`PLAN_LIMIT_REACHED`, `MODULE_NOT_IN_PLAN`). |

## Para «Despliegue» al preparar el release

- **Variables** (filas de `KUADOR_PLAN_USER_LIMIT`): «Límite de usuarios del personal solo para clínicas sin suscripción; las demás usan `MAX_USERS` de su plan (MEDK-69). Vacío o `0` = sin límite».
- **Fila en «Cambios»**: «Cambio de plan con prorrateo, capacidades del plan (`MODULE_NOT_IN_PLAN`, `PLAN_LIMIT_REACHED`) y modo solo lectura (`TENANT_READ_ONLY`); migración V12; `KUADOR_PLAN_USER_LIMIT` pasa a aplicar solo a clínicas sin suscripción | MEDK-69».
