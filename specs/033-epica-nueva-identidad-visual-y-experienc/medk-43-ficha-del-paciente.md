# MEDK-43 · Web: ficha del paciente (rediseño)

Tarea 10 del plan de MEDK-33. Según la auditoría aprobada en MEDK-34 (`audit.md` § 6, propuesta 2), **la gestión de citas no existe ni tiene API** (R2): esta tarea cubre **solo la ficha del paciente** (`/patients/:id`). Sin cambios de API ni de datos.

## Qué cambió

- La ficha usa solo componentes del sistema de diseño (`kd-card`, `kd-button`, `kd-field`, `kd-input`, `kd-alert`, `kd-dialog`, `kd-list`, `kd-icon` y los estados de carga, error y vacío). Sin clases globales heredadas (`button`, `alert`, `card`, `card-list`).
- Se piden y muestran los mismos datos que antes (prueba «shows the same personal data fields as before the redesign»).
- Una sola acción principal por vista (R-02): primero el consentimiento faltante o desactualizado, luego la invitación; si el formulario de representante está abierto, su envío.
- Terminar un vínculo se confirma en un diálogo modal (`kd-dialog`) que devuelve el foco al botón que lo abrió.
- Accesibilidad: la tabla del historial de vínculos, que se desplaza en móvil, ahora es una región alcanzable con teclado (la detectó axe en el navegador real).

## Pasos por tarea (antes → después)

**Acción** = un campo que se completa, un clic o un envío (misma definición que `audit.md` § 3). Línea base: el código de `develop` antes de esta tarea.

| Tarea en la ficha | Antes | Después |
|---|---|---|
| Abrir la ficha desde la búsqueda (F2) | 3 | 3 (la búsqueda es de la tarea 9) |
| Registrar consentimiento (adulto) | 1–2 (quién otorga + «Registrar») | 1–2 |
| Invitar a activar la cuenta | 1–2 (canal + «Enviar») | 1–2 |
| Agregar representante | abrir + campos + enviar | igual |
| Terminar un vínculo | 2 («Terminar» + confirmar) | 2 |
| Ir a editar datos | 1 | 1 |

No sube ninguna acción. La meta de −20 % de tiempo (SC-008) se aprobó en MEDK-34 con alcance F1, F2 y F4 y se mide con sesiones moderadas en el cierre (tarea 15); el tiempo guionado de la ficha (0,3 s) no lo mueve un cambio visual.

## Cómo se verifica

```bash
cd web && npm test -- --watch=false     # lint, guardas y pruebas unitarias (axe, permisos, R-02)
cd web && npm run build
cd web && npm run test:visual:docker    # capturas Linux, como en CI
```

- `web/src/app/features/patients/patient-detail/patient-detail.page.spec.ts`: estados de carga, error y vacío; visibilidad por permiso (`patients.read`, `patients.register`, `patients.edit`, `patients.guardians.manage`); una acción principal; axe.
- `web/e2e/visual/patient-detail.visual.ts`: capturas en claro, oscuro y móvil (menor con vínculos, adulto con pendientes, solo lectura, diálogo de terminar vínculo y error de carga) y axe con contraste en navegador real. Datos sintéticos (fixtures ficticios), con sesión y API simuladas.

## Notas

- Despliegue: no cambia.
- Bundle inicial: `develop` (con MEDK-42) ya supera el presupuesto de 500 kB: 585,9 kB. Con esta rama queda en 577,1 kB (−8,8 kB), porque la ficha ahora se carga de forma diferida (`loadComponent`). El presupuesto es la propuesta 4 de `audit.md`; conviene revisarlo junto con la carga diferida de las demás rutas.
