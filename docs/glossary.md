# Glosario de Medical Kuador

| Término | Significado | No confundir con |
|---|---|---|
| Kuador | Empresa dueña y operadora de la plataforma | Una clínica cliente |
| Medical Kuador | Sistema completo de gestión de clínicas de la familia Kuador Clínica, ofrecido como SaaS | Otros productos de la familia Kuador Clínica |
| Tenant | Clínica cliente con sus datos totalmente aislados de las demás | Sucursal |
| Clínica | Organización de salud que contrata el servicio; corresponde a un tenant | Sucursal |
| Sucursal | Sede física de una clínica; un tenant puede tener varias | Tenant |
| SUPER_ADMIN | Rol del equipo de Kuador: configura planes y tarifas y administra los tenants | ADMIN, OWNER |
| OWNER | Rol de la clínica que contrata el servicio [POR DEFINIR: alcance exacto frente a ADMIN] | SUPER_ADMIN |
| ADMIN | Rol de la clínica que contrata y administra el servicio [POR DEFINIR: alcance exacto frente a OWNER] | SUPER_ADMIN |
| Rol | Conjunto de permisos asignable a usuarios de un tenant; administrable por la clínica | Permiso |
| Permiso | Acción concreta que se autoriza (por ejemplo ver historia clínica) | Rol |
| Roles sugeridos iniciales | Roles que el sistema trae por defecto y la clínica puede cambiar: médico, odontólogo, enfermería, recepción, cajero, farmacéutico, bodeguero, cobranzas y paciente [POR DEFINIR: confirmar lista y permisos de cada uno] | Roles fijos e inmodificables |
| Plan | Paquete de módulos y límites que Kuador ofrece a las clínicas | Suscripción |
| Tarifa | Precio de un plan, definido por el SUPER_ADMIN | Cobranza |
| Suscripción | Contrato vigente de un tenant con un plan, que se cobra periódicamente | Plan |
| Paciente | Persona atendida en una clínica; puede usar la app móvil | Usuario del personal |
| Personal | Usuarios de la clínica que operan el sistema | Paciente |
| Historia clínica | Registro de las atenciones y datos de salud de un paciente | Ficha administrativa del paciente |
| Caja | Control diario de dinero que entra y sale en una sucursal | Cobranzas |
| Cobranzas | Seguimiento y recuperación de los montos que los pacientes adeudan a la clínica | Cobro de la suscripción a la clínica |
| Farmacia | Módulo de medicamentos, recetas y dispensación | Bodega |
| Bodega | Módulo de insumos y existencias, con sus movimientos | Farmacia |
| Odontología | Módulo de atención dental: odontograma, tratamientos y presupuestos | Atención médica general |
| Notificación | Aviso enviado a pacientes o personal por un canal (correo, SMS, WhatsApp, push, dentro del sistema) | Auditoría |
| Auditoría | Registro de quién hizo qué y cuándo sobre datos sensibles | Notificación |
| MVP | Primera versión con los módulos mínimos para vender [POR DEFINIR: módulos incluidos] | Visión completa del sistema |
