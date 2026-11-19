# Producto: Medical Kuador

## Usuarios

- **Núcleo:** quienes contratan y operan una clínica en la plataforma: el OWNER y el ADMIN de cada clínica (tenant), que contratan el servicio y lo configuran, y el personal de la clínica (atención clínica, odontología, recepción, caja, farmacia, bodega, cobranzas) que lo usa a diario, en web y en la app móvil.
- **Secundario:**
  - Pacientes, que usan la app móvil [POR DEFINIR: qué pueden hacer los pacientes en la app].
  - SUPER_ADMIN: el equipo de Kuador, dueño del sistema, que configura planes y tarifas y administra los tenants.

## Propósito

Medical Kuador es el sistema completo de la familia Kuador Clínica para administrar y controlar clínicas bajo un modelo SaaS por suscripción. Cada clínica es un tenant aislado y puede tener varias sucursales. Reúne en un solo lugar la gestión clínica y administrativa: notificaciones, cobranzas, farmacia, odontología, caja y bodega.

Éxito: que una clínica pueda contratar un plan, operar su día a día en el sistema y que Kuador pueda vender, cobrar y administrar suscripciones. Las métricas concretas están por definir [POR DEFINIR: indicadores de éxito, p. ej. clínicas activas, ingreso recurrente, retención].

## Posicionamiento

- Una sola plataforma para todo el ciclo de la clínica (clínico, farmacia, odontología, caja, bodega, cobranzas), en lugar de varias herramientas sueltas.
- Multi-tenant y multisucursal desde el diseño.
- Roles y permisos administrables por cada clínica, con valores iniciales sugeridos.
- Planes y tarifas flexibles definidos por Kuador.
- Parte de la familia Kuador Clínica.
- Alternativas y competidores concretos: [POR DEFINIR: con qué productos se compara].

## Contexto operativo

- **Alcance de la primera versión (MVP):** los módulos que entran para poder vender están [POR DEFINIR: lista de módulos del MVP]. El resto de módulos se construirá de forma progresiva; la visión completa incluye gestión de clínicas y sucursales, notificaciones, cobranzas, farmacia, odontología, caja y bodega.
- **Modelo comercial:** suscripción por tenant. El SUPER_ADMIN configura los planes y las tarifas que se ofrecen [POR DEFINIR: periodicidad de cobro, moneda, medio de pago de la suscripción, período de prueba].
- **Roles de contratación:** OWNER y ADMIN contratan el servicio [POR DEFINIR: diferencia exacta de alcance entre OWNER y ADMIN].
- **País y normativa:** [POR DEFINIR: país de lanzamiento, normativa de facturación y de protección de datos de salud]. La facturación para otros países se agregará después.
- **Idioma:** español [POR DEFINIR: si habrá otros idiomas].
- **Notificaciones e integraciones:** se requieren todos los canales (por ejemplo correo, SMS, WhatsApp, notificaciones push de la app y avisos dentro del sistema) y las integraciones externas necesarias [POR DEFINIR: proveedores concretos de cada canal, pasarela de pagos y facturación electrónica].
- **Móvil:** app Flutter para pacientes y para personal [POR DEFINIR: funciones por tipo de usuario].
