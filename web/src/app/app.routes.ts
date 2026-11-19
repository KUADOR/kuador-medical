import { Routes } from '@angular/router';
import {
  guestGuard,
  homeGuard,
  pendingPasswordChangeGuard,
  requirePermissions,
} from './core/auth/auth.guards';
import { ChangePasswordPage } from './features/change-password/change-password.page';
import { LoginPage } from './features/login/login.page';
import { MessagePage } from './features/message/message.page';
import { PlatformPermissionsPage } from './features/platform/platform-permissions.page';
import { RoleFormPage } from './features/roles/role-form.page';
import { RolesPage } from './features/roles/roles.page';
import { SignupPage } from './features/signup/signup.page';
import { StatusPage } from './features/status/status.page';

const APP_TITLE = 'Medical Kuador';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeGuard], children: [] },
  { path: 'status', component: StatusPage, title: `Estado del sistema · ${APP_TITLE}` },
  {
    path: 'login',
    component: LoginPage,
    canActivate: [guestGuard],
    title: `Ingresar · ${APP_TITLE}`,
  },
  {
    path: 'signup',
    component: SignupPage,
    canActivate: [guestGuard],
    title: `Registrar clínica · ${APP_TITLE}`,
  },
  {
    path: 'change-password',
    component: ChangePasswordPage,
    canActivate: [pendingPasswordChangeGuard],
    title: `Cambiar contraseña · ${APP_TITLE}`,
  },
  {
    path: 'roles',
    component: RolesPage,
    canActivate: [requirePermissions('roles.read')],
    title: `Roles · ${APP_TITLE}`,
  },
  {
    path: 'roles/new',
    component: RoleFormPage,
    canActivate: [requirePermissions('roles.read', 'roles.create')],
    title: `Nuevo rol · ${APP_TITLE}`,
  },
  {
    path: 'roles/:id',
    component: RoleFormPage,
    canActivate: [requirePermissions('roles.read')],
    title: `Rol · ${APP_TITLE}`,
  },
  {
    path: 'platform/permissions',
    component: PlatformPermissionsPage,
    canActivate: [requirePermissions('platform.permissions.read')],
    title: `Catálogo de permisos · ${APP_TITLE}`,
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
