# MEDK-25 · Integración de punta a punta del registro de usuarios

Notas de despliegue de la tarjeta MEDK-25 (épica MEDK-12). Al preparar el release se pasan a «Despliegue» (`docs/deployment.md`) y se borra este archivo.

## Qué cambia

- **Script nuevo** `scripts/e2e-user-registration.sh`: corre contra la api real los siete escenarios de la spec de MEDK-12. Variables opcionales del script, sin secretos: `API_URL`, `CONTRACT_URL`, `MAILPIT_URL`, `WEB_URL`, `LINK_BASE_URL` y `E2E_PSQL`. Inserta sucursales de prueba directamente en la base, así que es solo para bases locales o descartables.
- **Rutas web de los enlaces de correo**: la web atiende ahora `/password-reset#t=…` y `/email-verification#t=…`, las mismas rutas que usan la api (`application.yaml`), la app móvil y los archivos `/.well-known/` (`web/docker-entrypoint.d/50-app-links.sh`). Antes eran `/reset-password` y `/verify-email`, y los enlaces de los correos abrían «Página no encontrada». Esto corrige lo que dice la fila de MEDK-20 en «Cambios». Ningún correo apuntó a las rutas viejas, así que no hay redirecciones.
- **api**: si `KUADOR_CLINIC_TIME_ZONE` está vacía (como en `.env.example`), la api usa UTC en lugar de fallar al arrancar.
- **web**: `npm start` contra la api real pasa a ser el modo normal; el mock (`npm run dev:mock`) queda solo para trabajar sin api.

No hay servicios, dependencias, puertos, dominios ni secretos nuevos.

## Qué hacer al desplegar

| Entorno | Acción |
|---|---|
| Local | Nada obligatorio. Para verificar, seguir «Cómo correr los escenarios». |
| Pruebas | Nada obligatorio. `KUADOR_WEB_BASE_URL` y `KUADOR_APP_LINK_BASE_URL` deben apuntar a la web que tiene esta versión, para que `/password-reset` y `/email-verification` abran la página correcta. Cuando exista el entorno [POR DEFINIR: servidor y URL], se puede correr el script apuntando `API_URL`, el correo y `E2E_PSQL` a ese entorno, solo si su base es descartable. |
| Producción | Nada obligatorio. Desplegar la web con esta versión antes de enviar enlaces de recuperación o verificación. No correr el script, porque escribe en la base. |

## Para «Despliegue» al preparar el release

- **Paso a paso › Local › Entorno completo, paso 6**: cambiar la nota del mock por esta: «Desde MEDK-25 la api implementa `user-registration.openapi.yaml` completo: `npm start` contra la api real es el modo normal y el mock queda solo para trabajar sin api».
- **Verificación**: agregar la viñeta «Punta a punta del registro de usuarios (local): `scripts/e2e-user-registration.sh` con `db`, `mailpit` y la api arriba; debe terminar en 0».
- **Nueva subsección**, después de «Prueba de humo»: el contenido de abajo.
- **Fila en «Cambios»**: «Integración de punta a punta del registro de usuarios: script `scripts/e2e-user-registration.sh`; rutas web `/password-reset` y `/email-verification` (antes `/reset-password` y `/verify-email`); `KUADOR_CLINIC_TIME_ZONE` vacía usa UTC | MEDK-25».

### Escenarios de punta a punta del registro de usuarios

`scripts/e2e-user-registration.sh` (MEDK-25; requiere `bash`, `curl`, `jq`, `openssl` y Docker) recorre contra la api real los escenarios de la spec de MEDK-12, con las mismas llamadas que hacen la web y la app movil:

1. Invitación y deep link: alta de personal con rol por sucursal, correo con `<KUADOR_APP_LINK_BASE_URL>/invitation#t=…`, reenvío (el enlace anterior deja de valer), vista previa, aceptación, enlace usado o desconocido rechazado (400 `ONE_TIME_TOKEN_INVALID`); si `WEB_URL` no está vacío, la web sirve `/.well-known/*` y la ruta `/invitation` (respaldo sin app).
2. Login con dos clínicas: mismo correo invitado en A y B; rol clínico sin registro profesional rechazado; mensaje genérico igual para clave errónea y correo inexistente; `CLINIC_SELECTION_REQUIRED` con las dos clínicas; elegir B o A (y en A, elegir sucursal).
3. Cambio de sucursal: ADMIN en «Matriz» y Recepción en «Sucursal Norte»; `/me` y los permisos cambian con `POST /auth/switch-branch`; personal permitido en Matriz y 403 en Norte; sucursal sin asignación 403 `BRANCH_NOT_ASSIGNED`.
4. Recuperación: 202 siempre; un correo por clínica; el enlace de A cambia solo la clave de A (luego el login entra directo a A y la clave anterior sigue abriendo B); enlace usado rechazado.
5. Menor con tutor: sin representante o sin consentimiento 400; con representante 201, `minor: true`, sin cuenta y con vínculo `LEGAL_GUARDIAN` vigente; invitación de cuenta para el menor rechazada; documento repetido 409.
6. Autorregistro: deshabilitado 404; habilitado 202, verificación por `/email-verification#t=…`, ingreso del paciente solo con módulos de paciente; deshabilitado de nuevo 404.
7. Aislamiento: A lee, edita o desactiva personal de B, edita un rol de B; B lee pacientes o vínculos de A o asigna roles a personal de A: todo 404 `NOT_FOUND`; los listados no mezclan clínicas.

Cada operación de `user-registration.openapi.yaml` pasa por un proxy de validación de Prism: cualquier diferencia de la respuesta con el contrato sale como `FAIL contract: …`. Las sucursales todavía no tienen API (épica de clínicas), así que el script inserta dos sucursales en la clínica A y una en la B con `psql` (por defecto `docker compose exec -T db psql`; otro comando con `E2E_PSQL`). Solo para bases locales o descartables.

```bash
docker compose up -d db mailpit                                   # con .env completo
set -a; . ./.env; set +a; (cd api && ./mvnw spring-boot:run) &    # api en http://localhost:8080
(cd web && npm start) &                                           # opcional: comprobaciones de la web (WEB_URL)
(cd web && npx prism proxy ../contracts/user-registration.openapi.yaml http://localhost:8080/api/v1 --port 4011 --errors) &
scripts/e2e-user-registration.sh                                  # PASS/FAIL por paso; termina en 0 si todo pasa
```

Variables del script (opcionales): `API_URL`, `CONTRACT_URL` (vacío: sin validación del contrato), `MAILPIT_URL`, `WEB_URL` (vacío: sin comprobaciones de la web), `LINK_BASE_URL` y `E2E_PSQL`. Genera claves por corrida y nunca imprime claves ni tokens; los datos que crea (clínicas `E2E Clínica A/B`) quedan en la base local.

Resultado de MEDK-25 (2026-11-19, macOS, Docker 29, JDK 25, api del commit de la rama sobre `develop` con MEDK-29 a MEDK-31, web con `npm start` y `API_PROXY_TARGET` apuntando a la api): 67 pasos en verde, sin diferencias con el contrato. Además:

- Web (Chrome sin interfaz, Playwright fuera del repo, contra la api real): 33 comprobaciones de los mismos siete escenarios por la interfaz: aceptar invitaciones de A y B desde el enlace del correo, mismo mensaje para clave errónea y correo inexistente, elegir clínica y sucursal, menú «Personal | Pacientes | Roles» en Matriz y solo «Pacientes» en Norte (y `/staff` lleva a «Sin permiso»), registrar un menor con su representante (sin representante el formulario no se envía), recuperar la clave desde el enlace `/password-reset` (el enlace usado muestra «El enlace no es válido…»), autorregistro deshabilitado («no está disponible para esta clínica») y habilitado con verificación en `/email-verification`, paciente sin módulos del personal, y la dueña de A sin acceso a personal de B («No encontramos lo que buscas.»). Sin errores en la consola.
- Movil (sin simulador ni emulador disponibles en esta corrida): la capa de datos de la app (`AuthApi`, `SessionApi`, `StaffApi`, `RolesApi`, `PatientsApi` y `parseDeepLink`) recorrió los siete escenarios contra la api real en una prueba temporal de `flutter test` (no versionada), incluidos los enlaces reales de los correos pasados por el parser de deep links (con `https`, como en pruebas y producción). Queda pendiente probar App Links / Universal Links en un dispositivo cuando exista el dominio [POR DEFINIR: dominio].
- Entorno de pruebas: [POR DEFINIR: servidor y URL]; los escenarios se corrieron en el entorno local. El script sirve para el entorno de pruebas apuntando `API_URL`, `MAILPIT_URL`/correo y `E2E_PSQL` a ese entorno cuando exista.
