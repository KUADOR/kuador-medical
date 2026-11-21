# MEDK-64 · Canal de correo de Notificaciones

Notas de despliegue de la tarjeta MEDK-64 (épica MEDK-49). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **api, módulo `notifications`:** el correo sale por la interfaz `EmailGateway` (ADR-005). El adaptador SMTP se renombra de `SmtpNotificationSender` a `SmtpEmailGateway`; en «Despliegue» (tabla de dependencias, fila Spring Mail) hay que decir «Adaptador SMTP de `EmailGateway`» en lugar de «de `NotificationSender`».
- **Plantillas en español** para la épica MEDK-49 (invitación del OWNER, cambio de tarifa, suscripción vencida, suspendida, cancelada y reactivada, 2FA restablecido). Aún no se envían: las usarán las tareas siguientes de la épica.
- **Configuración:** se reutilizan las variables existentes `KUADOR_SMTP_*` y `KUADOR_MAIL_FROM` y el secreto `KUADOR_SMTP_PASSWORD`. El plan de MEDK-49 las nombraba `MEDK_SMTP_*` / `MEDK_MAIL_FROM`; se mantienen los nombres que ya usa el repositorio para no duplicar configuración.
- **Pruebas:** dependencia de prueba `greenmail-junit5` (servidor SMTP en memoria). No llega a la imagen.

No hay servicios, puertos, dominios, variables ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada. Mailpit sigue recibiendo los correos. |
| Pruebas | Nada nuevo: verificar que `KUADOR_SMTP_HOST` y `KUADOR_MAIL_FROM` están definidos. |
| Producción | Nada nuevo: proveedor SMTP [POR DEFINIR], configurado con las mismas variables. |
