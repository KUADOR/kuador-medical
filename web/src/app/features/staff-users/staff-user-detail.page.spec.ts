import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import type { StaffUser } from '../../core/api/api-types';
import {
  ACTIVE_RECEPTIONIST,
  ADMIN_ROLE,
  DOCTOR_ROLE,
  INVITED_DOCTOR,
  MAIN_BRANCH,
  NORTH_BRANCH,
  OWNER_ROLE,
  RECEPTION_ROLE,
  STAFF_ADMIN_ACCOUNT,
  STAFF_READER_ACCOUNT,
  problem,
} from '../../../testing/fixtures';
import {
  AppHarness,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../testing/app-harness';
import {
  REGISTRATION_NEEDED_FOR_CLINICAL_ROLE,
  REGISTRATION_NEEDED_WHILE_CLINICAL,
  changedFields,
} from './staff-user-detail.page';

const ROLES = [OWNER_ROLE, ADMIN_ROLE, RECEPTION_ROLE, DOCTOR_ROLE];

describe('StaffUserDetailPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(user: StaffUser = ACTIVE_RECEPTIONIST, account = STAFF_ADMIN_ACCOUNT) {
    await signIn(app, account);
    await app.harness.navigateByUrl(`/staff/${user.id}`);
    app.backend.expectOne(`/api/v1/staff-users/${user.id}`).flush(user);
    if (account.permissions.includes('identity.assignments.manage')) {
      app.backend.expectOne('/api/v1/roles').flush(ROLES);
    }
    await app.settle();
  }

  function select(selector: string, value: string): void {
    const element = app.page().querySelector(selector) as HTMLSelectElement;
    element.value = value;
    element.dispatchEvent(new Event('change'));
  }

  function input(selector: string): HTMLInputElement {
    return app.page().querySelector(selector) as HTMLInputElement;
  }

  describe('personal data', () => {
    it('shows the record and saves only the fields that changed, with the version', async () => {
      await open();

      expect(app.page().querySelector('h1')?.textContent?.trim()).toBe('Luis Gómez');
      expect(byTestId(app.page(), 'status')?.textContent?.trim()).toBe('Activo');
      expect(byTestId(app.page(), 'email')?.textContent?.trim()).toBe('luis.gomez@example.com');
      expect(app.page().querySelector('#staff-email')).toBeNull();

      type(app.page(), '#staff-job-title', ' Jefe de recepción ');
      submit(app.page());

      const request = app.backend.expectOne({
        method: 'PATCH',
        url: `/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}`,
      });
      expect(request.request.body).toEqual({ jobTitle: 'Jefe de recepción', version: 5 });
      request.flush({ ...ACTIVE_RECEPTIONIST, jobTitle: 'Jefe de recepción', version: 6 });
      await app.settle();

      expect(byTestId(app.page(), 'notice')?.textContent).toContain(
        'Se guardaron los datos de Luis Gómez.',
      );
      expect(input('#staff-job-title').value).toBe('Jefe de recepción');
    });

    it('does not call the API when nothing changed', async () => {
      await open();
      submit(app.page());
      await app.settle();

      expect(byTestId(app.page(), 'notice')?.textContent).toContain('No hay cambios');
      app.backend.expectNone({ method: 'PATCH' });
    });

    it('keeps the professional registration while a clinical role is assigned', async () => {
      await open(INVITED_DOCTOR);
      type(app.page(), '#staff-registration', '');
      submit(app.page());
      await app.settle();

      expect(app.page().querySelector('#staff-registration-error')?.textContent?.trim()).toBe(
        REGISTRATION_NEEDED_WHILE_CLINICAL,
      );
      expect(document.activeElement).toBe(input('#staff-registration'));
      app.backend.expectNone({ method: 'PATCH' });
    });

    it('offers to reload when someone else changed the record', async () => {
      await open();
      type(app.page(), '#staff-first-names', 'Luis Alberto');
      submit(app.page());
      const { body, init } = problem(409, 'VERSION_CONFLICT');
      app.backend
        .expectOne({ method: 'PATCH', url: `/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}` })
        .flush(body, init);
      await app.settle();

      expect(byTestId(app.page(), 'data-error')?.textContent).toContain(
        ERROR_MESSAGES.VERSION_CONFLICT,
      );
      buttonByText(app.page(), 'Recargar los datos').click();
      app.backend
        .expectOne(`/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}`)
        .flush({ ...ACTIVE_RECEPTIONIST, firstNames: 'Luis Ángel', version: 6 });
      app.backend.expectOne('/api/v1/roles').flush(ROLES);
      await app.settle();

      expect(input('#staff-first-names').value).toBe('Luis Ángel');
      expect(byTestId(app.page(), 'data-error')).toBeNull();
    });

    it('builds the PATCH body from the changed fields only', () => {
      const value = {
        firstNames: 'Luis',
        lastNames: 'Gómez',
        jobTitle: 'Recepcionista',
        professionalRegistration: '',
      };
      expect(changedFields(ACTIVE_RECEPTIONIST, value)).toBeNull();
      expect(
        changedFields(INVITED_DOCTOR, {
          firstNames: 'Ana',
          lastNames: 'Pérez',
          jobTitle: 'Médica general',
          professionalRegistration: ' ',
        }),
      ).toEqual({ professionalRegistration: null, version: 2 });
    });
  });

  describe('role assignments', () => {
    it('starts from the current assignments and saves a role per branch', async () => {
      await open(INVITED_DOCTOR);

      expect((byTestId(app.page(), 'mode-all') as HTMLInputElement).checked).toBe(true);
      expect((app.page().querySelector('#staff-assignments-role') as HTMLSelectElement).value).toBe(
        DOCTOR_ROLE.id,
      );

      (byTestId(app.page(), 'mode-per-branch') as HTMLInputElement).click();
      await app.settle();
      select(`#staff-assignments-branch-${MAIN_BRANCH.id}`, ADMIN_ROLE.id);
      await app.settle();
      byTestId(app.page(), 'save-assignments')?.click();

      const request = app.backend.expectOne({
        method: 'PUT',
        url: `/api/v1/staff-users/${INVITED_DOCTOR.id}/role-assignments`,
      });
      expect(request.request.body).toEqual({
        mode: 'PER_BRANCH',
        assignments: [
          { branchId: MAIN_BRANCH.id, roleId: ADMIN_ROLE.id },
          { branchId: NORTH_BRANCH.id, roleId: DOCTOR_ROLE.id },
        ],
      });
      request.flush([
        { branch: MAIN_BRANCH, role: { id: ADMIN_ROLE.id, name: 'ADMIN' } },
        { branch: NORTH_BRANCH, role: { id: DOCTOR_ROLE.id, name: 'Médico' } },
      ]);
      await app.settle();

      expect(byTestId(app.page(), 'notice')?.textContent).toContain(
        'Se guardaron los roles de Ana Pérez.',
      );
      expect((byTestId(app.page(), 'mode-per-branch') as HTMLInputElement).checked).toBe(true);
    });

    it('reads a role in only some branches as one role per branch', async () => {
      // ACTIVE_RECEPTIONIST works only in the main branch; the caller sees two branches.
      await open();
      expect((byTestId(app.page(), 'mode-per-branch') as HTMLInputElement).checked).toBe(true);
      expect(
        (
          app
            .page()
            .querySelector(`#staff-assignments-branch-${NORTH_BRANCH.id}`) as HTMLSelectElement
        ).value,
      ).toBe('');
    });

    it('asks for the professional registration before assigning a clinical role', async () => {
      await open();
      (byTestId(app.page(), 'mode-all') as HTMLInputElement).click();
      await app.settle();
      select('#staff-assignments-role', DOCTOR_ROLE.id);
      await app.settle();
      byTestId(app.page(), 'save-assignments')?.click();
      await app.settle();

      expect(byTestId(app.page(), 'assignments-editor')?.textContent).toContain(
        REGISTRATION_NEEDED_FOR_CLINICAL_ROLE,
      );
      app.backend.expectNone({ method: 'PUT' });
    });

    it.each([
      [403, 'PRIVILEGE_ESCALATION'],
      [409, 'LAST_OWNER'],
      [409, 'SELF_MODIFICATION_FORBIDDEN'],
    ] as const)('explains a %s %s in Spanish', async (status, code) => {
      await open();
      (byTestId(app.page(), 'mode-all') as HTMLInputElement).click();
      await app.settle();
      select('#staff-assignments-role', ADMIN_ROLE.id);
      await app.settle();
      byTestId(app.page(), 'save-assignments')?.click();
      const { body, init } = problem(status, code);
      app.backend
        .expectOne({
          method: 'PUT',
          url: `/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}/role-assignments`,
        })
        .flush(body, init);
      await app.settle();

      const alert = byTestId(app.page(), 'assignments-failure');
      expect(alert?.textContent?.trim()).toBe(ERROR_MESSAGES[code]);
      expect(document.activeElement).toBe(alert);
    });

    it('only shows the summary without identity.assignments.manage', async () => {
      await open(INVITED_DOCTOR, STAFF_READER_ACCOUNT);

      expect(byTestId(app.page(), 'assignments-editor')).toBeNull();
      expect(byTestId(app.page(), 'assignments-summary')?.textContent).toContain(
        'Médico en Main branch · Médico en North branch',
      );
    });
  });

  describe('access', () => {
    it('resends a pending invitation and shows the new expiry', async () => {
      await open(INVITED_DOCTOR);
      expect(byTestId(app.page(), 'invitation-expiry')).not.toBeNull();
      byTestId(app.page(), 'resend')?.click();

      app.backend
        .expectOne({ method: 'POST', url: `/api/v1/staff-users/${INVITED_DOCTOR.id}/invitation` })
        .flush(null, { status: 202, statusText: 'Accepted' });
      await app.settle();
      app.backend
        .expectOne(`/api/v1/staff-users/${INVITED_DOCTOR.id}`)
        .flush({ ...INVITED_DOCTOR, invitationExpiresAt: '2030-01-07T15:00:00Z', version: 3 });
      await app.settle();

      expect(byTestId(app.page(), 'notice')?.textContent).toContain(
        'Enviamos una nueva invitación a ana.perez@example.com.',
      );
    });

    it('does not offer to resend once the account is active', async () => {
      await open();
      expect(byTestId(app.page(), 'resend')).toBeNull();
      expect(byTestId(app.page(), 'invitation-expiry')).toBeNull();
    });

    it('explains when the account was activated meanwhile', async () => {
      await open(INVITED_DOCTOR);
      byTestId(app.page(), 'resend')?.click();
      const { body, init } = problem(409, 'ACCOUNT_ALREADY_ACTIVE');
      app.backend
        .expectOne(`/api/v1/staff-users/${INVITED_DOCTOR.id}/invitation`)
        .flush(body, init);
      await app.settle();

      expect(byTestId(app.page(), 'access-failure')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.ACCOUNT_ALREADY_ACTIVE,
      );
    });

    it('asks for confirmation and disables the access', async () => {
      await open();
      byTestId(app.page(), 'disable')?.click();
      await app.settle();

      const confirm = byTestId(app.page(), 'confirm-disable') as HTMLButtonElement;
      expect(app.page().querySelector('[role="alertdialog"]')?.textContent).toContain(
        '¿Deshabilitar el acceso de Luis Gómez?',
      );
      expect(document.activeElement).toBe(confirm);
      confirm.click();

      app.backend
        .expectOne({ method: 'POST', url: `/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}/disable` })
        .flush({ ...ACTIVE_RECEPTIONIST, status: 'DISABLED', version: 6 });
      await app.settle();

      expect(byTestId(app.page(), 'status')?.textContent?.trim()).toBe('Deshabilitado');
      expect(byTestId(app.page(), 'notice')?.textContent).toContain(
        'Se deshabilitó el acceso de Luis Gómez.',
      );
      expect(byTestId(app.page(), 'enable')).not.toBeNull();
      expect(byTestId(app.page(), 'disable')).toBeNull();
    });

    it('does not disable when the person cancels', async () => {
      await open();
      byTestId(app.page(), 'disable')?.click();
      await app.settle();
      buttonByText(app.page(), 'Cancelar').click();
      await app.settle();

      expect(app.page().querySelector('[role="alertdialog"]')).toBeNull();
      expect(document.activeElement).toBe(byTestId(app.page(), 'disable'));
      app.backend.expectNone(`/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}/disable`);
    });

    it('explains that the last OWNER cannot be disabled', async () => {
      await open();
      byTestId(app.page(), 'disable')?.click();
      await app.settle();
      (byTestId(app.page(), 'confirm-disable') as HTMLButtonElement).click();
      const { body, init } = problem(409, 'LAST_OWNER');
      app.backend
        .expectOne(`/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}/disable`)
        .flush(body, init);
      await app.settle();

      expect(byTestId(app.page(), 'access-failure')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.LAST_OWNER,
      );
      expect(byTestId(app.page(), 'status')?.textContent?.trim()).toBe('Activo');
    });

    it('enables a disabled account and explains the plan limit', async () => {
      await open({ ...ACTIVE_RECEPTIONIST, status: 'DISABLED' });
      byTestId(app.page(), 'enable')?.click();
      const { body, init } = problem(409, 'PLAN_LIMIT_REACHED');
      app.backend
        .expectOne({ method: 'POST', url: `/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}/enable` })
        .flush(body, init);
      await app.settle();
      expect(byTestId(app.page(), 'access-failure')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.PLAN_LIMIT_REACHED,
      );

      byTestId(app.page(), 'enable')?.click();
      app.backend
        .expectOne(`/api/v1/staff-users/${ACTIVE_RECEPTIONIST.id}/enable`)
        .flush({ ...ACTIVE_RECEPTIONIST, version: 7 });
      await app.settle();
      expect(byTestId(app.page(), 'status')?.textContent?.trim()).toBe('Activo');
      expect(byTestId(app.page(), 'access-failure')).toBeNull();
    });
  });

  describe('visibility', () => {
    it('shows the own record read-only, without assignments nor access actions', async () => {
      const self = { ...ACTIVE_RECEPTIONIST, id: STAFF_ADMIN_ACCOUNT.accountId };
      await open(self);

      expect(byTestId(app.page(), 'self-notice')).not.toBeNull();
      expect(input('#staff-first-names').disabled).toBe(true);
      expect(app.page().querySelector('button[type="submit"]')).toBeNull();
      expect(byTestId(app.page(), 'assignments-editor')).toBeNull();
      expect(byTestId(app.page(), 'access')).toBeNull();
    });

    it('shows a reader the record without forms or actions', async () => {
      await open(ACTIVE_RECEPTIONIST, STAFF_READER_ACCOUNT);

      expect(input('#staff-first-names').disabled).toBe(true);
      expect(app.page().querySelector('button[type="submit"]')).toBeNull();
      expect(byTestId(app.page(), 'access')).toBeNull();
      expect(byTestId(app.page(), 'self-notice')).toBeNull();
    });

    it('says so when the user does not exist in the clinic', async () => {
      await signIn(app, STAFF_ADMIN_ACCOUNT);
      await app.harness.navigateByUrl('/staff/00000000-0000-0000-0000-00000000dead');
      const { body, init } = problem(404, 'NOT_FOUND');
      app.backend
        .expectOne('/api/v1/staff-users/00000000-0000-0000-0000-00000000dead')
        .flush(body, init);
      // The roles request is cancelled together with the failed load.
      expect(app.backend.expectOne('/api/v1/roles').cancelled).toBe(true);
      await app.settle();

      expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.NOT_FOUND,
      );
      expect(app.page().querySelector('form')).toBeNull();
    });
  });
});
