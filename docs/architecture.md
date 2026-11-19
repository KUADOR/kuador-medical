# Arquitectura de Medical Kuador

## Stack

_Lo escribe el tablero a partir de las piezas del proyecto (Configuración → Piezas y repositorio): no lo edites a mano._

| Pieza | Tipo | Stack | Dónde | Compilar | Probar | Levantar |
|---|---|---|---|---|---|---|
| api | Backend | Java 25 · Spring Boot 4 · PostgreSQL | `api/` | `./mvnw -B package` | `./mvnw -B verify` | `./mvnw spring-boot:run` |
| web | Frontend | Angular · TypeScript | `web/` | `npm run build` | `npm test -- --watch=false` | `npm start` |
| movil | Móvil | Flutter | `movil/` | — | — | — |

## Módulos

Propuesta inicial de módulos de dominio; el orden de construcción depende del MVP [POR DEFINIR: módulos del MVP].

| Módulo | Responsabilidad | API pública |
|---|---|---|
| Plataforma y suscripciones | Tenants, planes, tarifas, suscripción y límites por plan; administración por el SUPER_ADMIN | Consulta de plan y capacidades habilitadas del tenant; eventos de alta, cambio y suspensión de suscripción |
| Identidad y permisos | Usuarios, roles y permisos administrables por tenant, con roles sugeridos iniciales; autenticación | Contexto de usuario y tenant; verificación de permiso |
| Clínicas y sucursales | Datos de la clínica, sucursales, profesionales y configuración | Consulta de clínica y sucursal; eventos de cambio |
| Pacientes e historia clínica | Ficha del paciente, historia clínica y atenciones | Consulta de paciente; eventos de atención registrada |
| Agenda y citas | Disponibilidad y citas por profesional y sucursal | Consulta y reserva de citas; eventos de cita creada, cambiada o cancelada |
| Odontología | Odontograma, tratamientos y presupuestos | Consulta de tratamientos; eventos de tratamiento realizado |
| Farmacia | Medicamentos, recetas y dispensación | Consulta de existencias; eventos de dispensación |
| Bodega e inventario | Insumos, existencias, movimientos y traslados entre sucursales | Consulta de stock; eventos de movimiento de inventario |
| Caja | Apertura y cierre de caja, ingresos y egresos | Consulta de estado de caja; eventos de cobro registrado |
| Cobranzas | Cuentas por cobrar, seguimiento y recordatorios de pago | Consulta de saldos; eventos de cobro pendiente o pagado |
| Facturación | Comprobantes según el país del tenant [POR DEFINIR: normativa y país inicial] | Emisión y consulta de comprobantes; eventos de comprobante emitido |
| Notificaciones | Envío por múltiples canales según plantillas y preferencias | Solicitud de envío de notificación; eventos de estado de envío |
| Auditoría | Registro de accesos y cambios sensibles | Registro de eventos de auditoría; consulta por permiso |

## Integraciones

Se necesitan todos los canales de notificación y las integraciones externas que correspondan [POR DEFINIR: proveedores concretos de correo, SMS, WhatsApp, push, pasarela de pagos de la suscripción, y facturación electrónica por país]. Cada integración se aísla detrás de una interfaz propia del módulo que la usa, con un adaptador por proveedor, de modo que cambiar de proveedor o agregar un país no toque el dominio. Las credenciales se guardan por entorno como secretos (ver Despliegue).

## Decisiones (ADR)

- **ADR-001 · Monolito modular** ({date}): el backend se organiza en módulos con límites verificados por pruebas de arquitectura. Permite construir de forma progresiva los módulos y separar servicios más adelante si hace falta.
- **ADR-002 · Multi-tenant en una sola base con identificador de tenant** ({date}): los datos de todas las clínicas comparten base y esquema, y cada registro lleva su tenant. El filtro se aplica en el backend de forma central. Reduce costo operativo por clínica; el aislamiento se valida con pruebas obligatorias [POR DEFINIR: si se refuerza además a nivel de base de datos].
- **ADR-003 · Roles y permisos como datos** ({date}): los permisos son fijos en el código como catálogo y los roles se componen en datos por tenant. Así cada clínica administra sus roles y el sistema trae roles sugeridos iniciales.
- **ADR-004 · Capacidades por plan** ({date}): los módulos y límites habilitados salen del plan que el SUPER_ADMIN configura; el backend los verifica en cada operación.
- **ADR-005 · Integraciones tras interfaces propias** ({date}): ningún módulo depende directamente del SDK de un proveedor externo.
