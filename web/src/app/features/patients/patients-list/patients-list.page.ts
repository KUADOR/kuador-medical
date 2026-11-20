import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { Patient, PatientPage } from '../../../core/api/api-types';
import { errorMessage } from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  ACCOUNT_STATUS_LABELS,
  documentTypeLabel,
  formatDate,
  fullName,
} from '../../../core/patients/patient-labels';
import { PatientsService } from '../../../core/patients/patients.service';
import { AppRoutes, patientUrl } from '../../../core/session/navigation';
import { SessionStore } from '../../../core/session/session.store';
import {
  KdAlert,
  KdButton,
  KdEmptyState,
  KdErrorState,
  KdField,
  KdInput,
  KdList,
  KdListItem,
  KdLoadingState,
} from '../../../design-system';

export const PATIENTS_PAGE_SIZE = 20;

type LoadState =
  { kind: 'loading' } | { kind: 'loaded'; page: PatientPage } | { kind: 'failed'; message: string };

/**
 * Patients of the clinic with search by name or document (patients.read). Redesigned with the
 * design system (MEDK-42): "Buscar" is the one primary action; loading, empty and error use the
 * library states.
 */
@Component({
  selector: 'mk-patients-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    FormsModule,
    KdAlert,
    KdButton,
    KdEmptyState,
    KdErrorState,
    KdField,
    KdInput,
    KdList,
    KdListItem,
    KdLoadingState,
  ],
  templateUrl: './patients-list.page.html',
  styleUrl: './patients-list.page.scss',
})
export class PatientsListPage {
  private readonly patients = inject(PatientsService);
  private readonly session = inject(SessionStore);

  protected readonly newPatientLink = AppRoutes.newPatient;
  protected readonly settingsLink = AppRoutes.selfRegistrationSettings;
  protected readonly patientUrl = patientUrl;
  protected readonly fullName = fullName;
  protected readonly accountStatusLabels = ACCOUNT_STATUS_LABELS;
  protected readonly canRegister = computed(() =>
    this.session.permissions().has('patients.register'),
  );

  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  protected readonly query = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly result = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.page : null;
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly totalPages = computed(() => {
    const page = this.result();
    return page ? Math.max(1, Math.ceil(page.totalItems / page.size)) : 1;
  });

  /** Search term of the last request, shown in the empty state. */
  protected readonly searchedTerm = signal('');

  constructor() {
    this.load();
  }

  protected search(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.pageIndex.set(page);
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.searchedTerm.set(this.query().trim());
    this.patients
      .list({ q: this.searchedTerm(), page: this.pageIndex(), size: PATIENTS_PAGE_SIZE })
      .subscribe({
        next: (page) => this.state.set({ kind: 'loaded', page }),
        error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
      });
  }

  protected emptyMessage(): string {
    const term = this.searchedTerm();
    return term
      ? `No hay pacientes que coincidan con «${term}».`
      : 'Tu clínica todavía no tiene pacientes registrados.';
  }

  /** Second line of a row: document, birth date and, for minors, says so in text (R-08). */
  protected summary(patient: Patient): string {
    const parts = [
      `${documentTypeLabel(patient.documentType)} ${patient.documentNumber}`,
      `Nacimiento ${formatDate(patient.birthDate)}`,
    ];
    if (patient.minor) {
      parts.push('Menor de edad');
    }
    return parts.join(' · ');
  }
}
