import { Routes } from '@angular/router';
import {
  guestGuard,
  homeGuard,
  loginStepGuard,
  pendingPasswordChangeGuard,
  requireAccess,
  requirePermissions,
} from './core/session/auth.guards';
import { Modules } from './core/session/navigation';
import { AcceptInvitationPage } from './features/auth/accept-invitation/accept-invitation.page';
import { ForgotPasswordPage } from './features/auth/forgot-password/forgot-password.page';
import { LoginSelectionPage } from './features/auth/login-selection/login-selection.page';
import { LoginPage } from './features/auth/login/login.page';
import { ResetPasswordPage } from './features/auth/reset-password/reset-password.page';
import { SelfRegistrationPage } from './features/auth/self-registration/self-registration.page';
import { VerifyEmailPage } from './features/auth/verify-email/verify-email.page';
import { ChangePasswordPage } from './features/change-password/change-password.page';
import { MessagePage } from './features/message/message.page';
import { MyGuardianLinksPage } from './features/patients/my-guardian-links/my-guardian-links.page';
import { PatientDetailPage } from './features/patients/patient-detail/patient-detail.page';
import { PatientFormPage } from './features/patients/patient-form/patient-form.page';
import { PatientsListPage } from './features/patients/patients-list/patients-list.page';
import { SelfRegistrationSettingsPage } from './features/patients/self-registration-settings/self-registration-settings.page';
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
    path: 'login/clinic',
    component: LoginSelectionPage,
    canActivate: [loginStepGuard('CLINIC_SELECTION_REQUIRED')],
    data: { step: 'CLINIC_SELECTION_REQUIRED' },
    title: `Elegir clínica · ${APP_TITLE}`,
  },
  {
    path: 'login/branch',
    component: LoginSelectionPage,
    canActivate: [loginStepGuard('BRANCH_SELECTION_REQUIRED')],
    data: { step: 'BRANCH_SELECTION_REQUIRED' },
    title: `Elegir sucursal · ${APP_TITLE}`,
  },
  {
    path: 'forgot-password',
    component: ForgotPasswordPage,
    canActivate: [guestGuard],
    title: `Recuperar contraseña · ${APP_TITLE}`,
  },
  {
    // Email links carry the one-time token in the fragment (#t=…), never in the path or query.
    path: 'reset-password',
    component: ResetPasswordPage,
    title: `Crear contraseña nueva · ${APP_TITLE}`,
  },
  {
    path: 'invitation',
    component: AcceptInvitationPage,
    title: `Activar cuenta · ${APP_TITLE}`,
  },
  {
    path: 'verify-email',
    component: VerifyEmailPage,
    title: `Verificar correo · ${APP_TITLE}`,
  },
  {
    path: 'clinics/:publicCode/register',
    component: SelfRegistrationPage,
    canActivate: [guestGuard],
    title: `Registro de pacientes · ${APP_TITLE}`,
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
    canActivate: [requireAccess({ permissions: ['roles.read'], module: Modules.identity })],
    title: `Roles · ${APP_TITLE}`,
  },
  {
    path: 'roles/new',
    component: RoleFormPage,
    canActivate: [
      requireAccess({ permissions: ['roles.read', 'roles.create'], module: Modules.identity }),
    ],
    title: `Nuevo rol · ${APP_TITLE}`,
  },
  {
    path: 'roles/:id',
    component: RoleFormPage,
    canActivate: [requireAccess({ permissions: ['roles.read'], module: Modules.identity })],
    title: `Rol · ${APP_TITLE}`,
  },
  {
    path: 'patients',
    component: PatientsListPage,
    canActivate: [requireAccess({ permissions: ['patients.read'], module: Modules.patients })],
    title: `Pacientes · ${APP_TITLE}`,
  },
  {
    path: 'patients/new',
    component: PatientFormPage,
    canActivate: [requireAccess({ permissions: ['patients.register'], module: Modules.patients })],
    title: `Registrar paciente · ${APP_TITLE}`,
  },
  {
    path: 'patients/self-registration',
    component: SelfRegistrationSettingsPage,
    canActivate: [requireAccess({ permissions: ['patients.read'], module: Modules.patients })],
    title: `Autorregistro de pacientes · ${APP_TITLE}`,
  },
  {
    path: 'patients/:id',
    component: PatientDetailPage,
    canActivate: [requireAccess({ permissions: ['patients.read'], module: Modules.patients })],
    title: `Paciente · ${APP_TITLE}`,
  },
  {
    path: 'patients/:id/edit',
    component: PatientFormPage,
    canActivate: [
      requireAccess({ permissions: ['patients.read', 'patients.edit'], module: Modules.patients }),
    ],
    title: `Editar paciente · ${APP_TITLE}`,
  },
  {
    // Adult patients authorize an emergency contact or delegated access (FR-028).
    path: 'my-contacts',
    component: MyGuardianLinksPage,
    canActivate: [requireAccess({ kinds: ['PATIENT'] })],
    title: `Mis contactos autorizados · ${APP_TITLE}`,
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
