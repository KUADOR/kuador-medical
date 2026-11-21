import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { RegistrationSettings } from '../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../core/errors/error-messages';
import { PatientsService } from '../../../core/patients/patients.service';
import { selfRegistrationUrl } from '../../../core/session/auth-routes';
import { AppRoutes } from '../../../core/session/navigation';
import { SessionStore } from '../../../core/session/session.store';
import {
  KdAlert,
  KdBadge,
  KdButton,
  KdErrorState,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
  KdSwitch,
} from '../../../design-system';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; settings: RegistrationSettings }
  | { kind: 'failed'; message: string };

/**
 * Patient self-registration switch of the clinic (disabled by default) and its public link
 * (FR-003). Anyone with patients.read sees the state; only patients.selfRegistration.configure
 * changes it.
 */
@Component({
  selector: 'mk-self-registration-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    KdAlert,
    KdBadge,
    KdButton,
    KdErrorState,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
    KdSwitch,
  ],
  templateUrl: './self-registration-settings.page.html',
  styleUrl: './self-registration-settings.page.scss',
})
export class SelfRegistrationSettingsPage {
  private readonly patients = inject(PatientsService);
  private readonly session = inject(SessionStore);
  private readonly document = inject(DOCUMENT);

  protected readonly patientsLink = AppRoutes.patients;
  protected readonly canConfigure = computed(() =>
    this.session.permissions().has('patients.selfRegistration.configure'),
  );
  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly settings = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.settings : null;
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  /** Absolute public link of the clinic, to share with patients. */
  protected readonly publicLink = computed(() => {
    const settings = this.settings();
    return settings
      ? `${this.document.location.origin}${selfRegistrationUrl(settings.publicCode)}`
      : null;
  });

  protected readonly saving = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly versionConflict = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly copied = signal(false);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.failure.set(null);
    this.versionConflict.set(false);
    this.patients.registrationSettings().subscribe({
      next: (settings) => this.state.set({ kind: 'ready', settings }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected toggle(): void {
    const settings = this.settings();
    if (!settings || this.saving() || !this.canConfigure()) {
      return;
    }
    const enable = !settings.selfRegistrationEnabled;
    this.saving.set(true);
    this.failure.set(null);
    this.versionConflict.set(false);
    this.notice.set(null);
    this.copied.set(false);
    this.patients
      .updateRegistrationSettings({ selfRegistrationEnabled: enable, version: settings.version })
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.state.set({ kind: 'ready', settings: saved });
          this.notice.set(
            saved.selfRegistrationEnabled
              ? 'Se habilitó el autorregistro de pacientes. Comparte el enlace público.'
              : 'Se deshabilitó el autorregistro: el enlace público ya no permite registrarse.',
          );
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const conflict = toProblem(error)?.code === 'VERSION_CONFLICT';
          this.versionConflict.set(conflict);
          this.failure.set(
            conflict
              ? 'Otra persona cambió esta configuración mientras la veías. Recarga para ver el estado actual.'
              : errorMessage(error),
          );
        },
      });
  }

  protected async copy(): Promise<void> {
    const link = this.publicLink();
    if (!link) {
      return;
    }
    try {
      await navigator.clipboard.writeText(link);
      this.copied.set(true);
    } catch {
      this.copied.set(false);
      this.failure.set('No se pudo copiar el enlace. Selecciónalo y cópialo manualmente.');
    }
  }
}
