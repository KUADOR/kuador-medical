# Despliegue de Medical Kuador

Documento vivo: se actualiza en el mismo Pull Request que cambia cómo se despliega.

## Entornos

| Entorno | Rama | Dónde corre | URL |
|---|---|---|---|
| Pruebas | develop | [POR DEFINIR: servidor o plataforma] | [POR DEFINIR: URL] |
| Producción | main | [POR DEFINIR: servidor o plataforma] | [POR DEFINIR: URL] |

## Infraestructura

[POR DEFINIR: proveedor, sistema operativo y tamaño del servidor; plataforma de despliegue (por ejemplo Dokploy o Docker Compose); proxy y certificados.]

## Piezas

| Pieza | Cómo se construye | Puerto | Dominio por entorno |
|---|---|---|---|
| api | `./mvnw -B package` en `api/` [POR DEFINIR: imagen Docker] | [POR DEFINIR] | [POR DEFINIR: pruebas / producción] |
| web | `npm run build` en `web/` [POR DEFINIR: imagen Docker o publicación de archivos estáticos] | [POR DEFINIR] | [POR DEFINIR: pruebas / producción] |
| movil | Build de Flutter en `movil/` [POR DEFINIR: distribución a tiendas o canal interno] | No aplica | No aplica |

## Dependencias

| Servicio | Versión | Para qué | Cómo se levanta | Datos que guarda |
|---|---|---|---|---|
| PostgreSQL | [POR DEFINIR: versión] | Base de datos de la api | [POR DEFINIR: contenedor o servicio administrado] | Datos de todos los tenants, incluidos datos de salud. [POR DEFINIR: volumen y respaldo] |
| Proveedores de notificaciones y pagos | [POR DEFINIR] | Envío por varios canales y cobro de suscripciones | Servicios externos [POR DEFINIR: proveedores] | Ninguno propio |

## Variables y secretos

Solo nombres: los valores nunca van en este documento ni en el repo.

| Nombre | Pieza | Entornos | Dónde vive | Secreto |
|---|---|---|---|---|
| SPRING_DATASOURCE_URL | api | pruebas, producción | [POR DEFINIR] | no |
| SPRING_DATASOURCE_USERNAME | api | pruebas, producción | [POR DEFINIR] | sí |
| SPRING_DATASOURCE_PASSWORD | api | pruebas, producción | [POR DEFINIR] | sí |
| [POR DEFINIR: credenciales de proveedores de notificaciones, pagos y facturación] | api | pruebas, producción | [POR DEFINIR] | sí |
| [POR DEFINIR: URL de la api para web y móvil] | web, movil | pruebas, producción | [POR DEFINIR] | no |

## Paso a paso

### Primera vez

1. Preparar el servidor [POR DEFINIR: proveedor y plataforma].
2. Crear las aplicaciones (api, web) y la base PostgreSQL por entorno.
3. Cargar variables y secretos en el lugar definido, sin escribir sus valores en el repo.
4. Primer despliegue en pruebas y verificación; luego producción.

### Cada despliegue

Al fusionar en `develop` se construye y despliega a pruebas; al fusionar en `main` se construye y despliega a producción. [POR DEFINIR: ejecución de migraciones de base de datos y orden de despliegue de las piezas.]

## CI/CD

[POR DEFINIR: workflow que construye (`./mvnw -B package`, `npm run build`), ejecuta pruebas (`./mvnw -B verify`, `npm test -- --watch=false`), publica y despliega cada rama en su entorno.]

## Respaldos y vuelta atrás

[POR DEFINIR: qué se respalda (como mínimo la base PostgreSQL, por contener datos de salud), frecuencia, dónde se guarda, pruebas de restauración y cómo volver a la versión anterior.]

## Verificación

[POR DEFINIR: comprobación de salud de la api, revisión de logs, y una prueba de punta a punta por entorno, incluyendo que un usuario de un tenant no accede a datos de otro.]

## Cambios

| Fecha | Qué cambió | Tarjeta |
|---|---|---|
| {date} | Primera versión | — |
