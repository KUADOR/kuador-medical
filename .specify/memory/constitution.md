# Constitución de Medical Kuador

Principios no negociables del proyecto. Todo plan se verifica contra ellos; cualquier excepción
se justifica por escrito en el plan.

## Principios fundamentales

### I. Aislamiento total entre tenants

Cada clínica (tenant) solo ve y modifica sus propios datos. Todo dato de negocio lleva su identificador de tenant y el acceso se filtra en el backend, nunca solo en la interfaz. Toda funcionalidad nueva incluye al menos una prueba que demuestre que un usuario del tenant A no puede leer ni escribir datos del tenant B. Solo el rol SUPER_ADMIN (Kuador) opera a nivel de plataforma.

### II. Permisos administrables, con valores iniciales sugeridos

Los roles y permisos son datos configurables por tenant, no reglas fijas en el código. El sistema se entrega con roles sugeridos (ver Glosario) que cada tenant puede ajustar. Cada endpoint exige un permiso explícito; si no lo declara, se rechaza. Los roles SUPER_ADMIN, OWNER y ADMIN tienen su alcance definido y no pueden ser eliminados por un tenant.

### III. Pruebas (NO NEGOCIABLE)

- Las reglas de dominio, el aislamiento entre tenants y los permisos se prueban **antes** de implementarlos (primero la prueba que falla, luego el código).
- Backend: `./mvnw -B verify` debe pasar. Frontend: `npm test -- --watch=false` debe pasar. Móvil: las pruebas de Flutter deben pasar.
- Una entrega con pruebas en rojo, o sin prueba de aislamiento de tenant en una funcionalidad que toca datos, no se integra.

### IV. Especificación primero

Se trabaja con Spec Driven Development: primero el qué y el porqué (spec), luego el cómo (plan) y después las tareas. No se escribe código sin una spec aprobada. Lo que la spec no define se marca «[POR DEFINIR: qué falta]» y no se inventa.

### V. Datos de salud protegidos y trazables

Los datos clínicos y personales son confidenciales. Toda lectura o cambio de historia clínica, dinero (caja, cobranzas) e inventario de medicamentos queda registrado en auditoría (quién, qué, cuándo, tenant). Los datos sensibles no se escriben en logs.

### VI. Módulos con límites claros

Cada módulo expone una API pública y no accede a las tablas ni a las clases internas de otro. Las integraciones externas (pagos, mensajería, facturación electrónica) se consumen detrás de una interfaz propia para poder cambiar de proveedor. Una prueba de arquitectura falla el build si se rompe un límite.

### VII. Planes y tarifas bajo control de Kuador

Los planes, tarifas y límites (por ejemplo, sucursales o usuarios) los define solo el SUPER_ADMIN. Las capacidades de cada módulo se habilitan según el plan del tenant, verificado en el backend.

## Restricciones

- Stack: ver Arquitectura § Stack. No se cambia sin una enmienda a esta constitución.
- Seguridad y datos: los secretos nunca van en el repositorio ni en documentos; solo viven en el gestor de secretos del entorno. Las credenciales de proveedores externos se guardan cifradas. Los datos personales se tratan según la normativa del país de cada tenant [POR DEFINIR: país de lanzamiento y normativa de protección de datos de salud aplicable].
- Idioma: la interfaz y la documentación están en español.

## Entrega y controles de calidad

- Todo cambio entra por Pull Request desde una rama de trabajo hacia `develop`; `main` solo recibe lo ya validado en pruebas.
- CI obligatorio: compila y pasa las pruebas de cada pieza afectada (api, web, movil).
- Los cambios en contratos de API se reflejan en la spec y en los clientes (web y móvil) en el mismo ciclo.
- Los cambios de despliegue actualizan el documento Despliegue en el mismo PR.
- Revisión de al menos una persona antes de integrar [POR DEFINIR: quién revisa].

## Gobernanza

Esta constitución prevalece sobre otras prácticas. Cambiarla requiere aprobación explícita del responsable del producto en Kuador, una nota de impacto y subir la versión (MAYOR: quita o redefine principios; MENOR: agrega; PARCHE: aclara).

**Versión**: 1.0.0 | **Ratificada**: {date} | **Última modificación**: {date}
