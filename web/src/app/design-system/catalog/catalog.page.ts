import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  KdAlert,
  KdButton,
  KdCard,
  KdDialog,
  KdEmptyState,
  KdErrorState,
  KdErrorSummary,
  KdField,
  type KdFormError,
  KdIcon,
  KdIconButton,
  KdInput,
  KdList,
  KdListItem,
  KdLoadingState,
} from '../index';

/**
 * Living catalog of the design system (development builds only, route /design-system).
 * Synthetic data only. The visual tests (e2e/visual/design-system.visual.ts) take screenshots
 * of it in light and dark and check axe, focus and 44 px targets on every control.
 */
@Component({
  selector: 'mk-design-system-catalog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    KdAlert,
    KdButton,
    KdCard,
    KdDialog,
    KdEmptyState,
    KdErrorState,
    KdErrorSummary,
    KdField,
    KdIcon,
    KdIconButton,
    KdInput,
    KdList,
    KdListItem,
    KdLoadingState,
  ],
  templateUrl: './catalog.page.html',
  styleUrl: './catalog.page.scss',
})
export class DesignSystemCatalogPage {
  protected readonly dialogOpen = signal(false);
  protected readonly summary = signal<KdFormError[]>([]);
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['Paciente Ficticio', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    specialty: ['general'],
    notes: [''],
    reminders: [true],
    disabledField: [{ value: 'Sucursal Centro', disabled: true }],
  });

  protected submit(): void {
    this.form.markAllAsTouched();
    this.summary.set(
      this.form.controls.email.invalid
        ? [{ message: 'Escribe el correo del paciente.', fieldId: 'catalog-email' }]
        : [],
    );
  }
}
