import { Routes } from '@angular/router';
import { pendingPasswordChangeGuard } from './core/auth/auth.guards';
import { MessagePage } from './features/message/message.page';
import { StatusPage } from './features/status/status.page';

const APP_TITLE = 'Medical Kuador';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'status' },
  { path: 'status', component: StatusPage, title: `Estado del sistema · ${APP_TITLE}` },
  {
    // Placeholder: the login page arrives in MEDK-8.
    path: 'login',
    component: MessagePage,
    title: `Ingresar · ${APP_TITLE}`,
    data: { heading: 'Ingresar', message: 'El ingreso estará disponible próximamente.' },
  },
  {
    // Placeholder: the password change page arrives in MEDK-8.
    path: 'change-password',
    component: MessagePage,
    canActivate: [pendingPasswordChangeGuard],
    title: `Cambiar contraseña · ${APP_TITLE}`,
    data: {
      heading: 'Cambiar contraseña',
      message: 'Debes cambiar tu contraseña antes de continuar.',
    },
  },
  {
    path: 'forbidden',
    component: MessagePage,
    title: `Sin permiso · ${APP_TITLE}`,
    data: { heading: 'Sin permiso', message: 'No tienes permiso para ver esta página.' },
  },
  {
    path: '**',
    component: MessagePage,
    title: `Página no encontrada · ${APP_TITLE}`,
    data: { heading: 'Página no encontrada', message: 'La página que buscas no existe.' },
  },
];
