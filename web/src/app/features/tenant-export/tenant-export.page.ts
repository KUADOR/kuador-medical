import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import type { ExportFormat, TenantExport } from '../../core/api/api-types';
import { errorMessage } from '../../core/errors/error-messages';
import { focusFirstInvalid, mustAccept } from '../../core/forms/form-errors';
import { formatDay } from '../../core/subscription/subscription-notice';
import { SubscriptionService } from '../../core/subscription/subscription.service';
import { TenantExportService } from '../../core/subscription/tenant-export.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../design-system';

/** Milliseconds between status checks of a pending export. */
export const EXPORT_POLL_INTERVAL = new InjectionToken<number>('EXPORT_POLL_INTERVAL', {
  factory: () => 3000,
});

export const FORMAT_LABELS: Record<ExportFormat, string> = {
  JSON: 'JSON (un archivo con todos los datos estructurados)',
  CSV: 'CSV (tablas para abrir en una hoja de cálculo)',
};

/**
 * Data export of the clinic for its OWNER (spec FR-021, FR-022): before exporting, the OWNER
 * accepts that the clinic is responsible for the data and Kuador only processes it. Available
 * also while the clinic is read-only (SUSPENDED, or CANCELLED during retention).
 */
@Component({
  selector: 'mk-tenant-export-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './tenant-export.page.html',
  styleUrl: './tenant-export.page.scss',
})
export class TenantExportPage {
  private readonly exports = inject(TenantExportService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly pollInterval = inject(EXPORT_POLL_INTERVAL);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private timer: ReturnType<typeof setTimeout> | null = null;
  private destroyed = false;

  protected readonly formats = Object.entries(FORMAT_LABELS) as [ExportFormat, string][];
  protected readonly formatDay = formatDay;
  protected readonly current = signal<TenantExport | null>(null);
  protected readonly submitting = signal(false);
  protected readonly checking = signal(false);
  protected readonly failure = signal<string | null>(null);
  /** The clinic is read-only: tell the OWNER that exporting is still allowed. */
  protected readonly readOnly = this.subscriptions.readOnly;
  protected readonly inProgress = computed(() => this.current()?.status === 'PENDING');
  protected readonly form = inject(NonNullableFormBuilder).group({
    format: ['JSON' as ExportFormat],
    acceptResponsibility: [false, [mustAccept]],
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.stopPolling();
    });
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || this.inProgress()) {
      return;
    }
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }
    this.submitting.set(true);
    try {
      const created = await this.exports.request(
        this.form.controls.format.value,
        crypto.randomUUID(),
      );
      this.show(created);
    } catch (error: unknown) {
      this.failure.set(errorMessage(error));
    } finally {
      this.submitting.set(false);
    }
  }

  /** Checks the status now (also done automatically while it is pending). */
  protected async check(): Promise<void> {
    const pending = this.current();
    if (!pending || this.checking()) {
      return;
    }
    this.stopPolling();
    this.checking.set(true);
    try {
      this.show(await this.exports.get(pending.id));
    } catch (error: unknown) {
      this.failure.set(errorMessage(error));
      this.schedule();
    } finally {
      this.checking.set(false);
    }
  }

  /** Back to the form for a new export (after a ready, failed or expired one). */
  protected startOver(): void {
    this.stopPolling();
    this.current.set(null);
    this.failure.set(null);
    this.form.controls.acceptResponsibility.reset(false);
  }

  private show(result: TenantExport): void {
    this.failure.set(null);
    this.current.set(result);
    if (result.status === 'PENDING') {
      this.schedule();
    }
  }

  private schedule(): void {
    if (this.destroyed || this.current()?.status !== 'PENDING') {
      return;
    }
    this.stopPolling();
    this.timer = setTimeout(() => void this.check(), this.pollInterval);
  }

  private stopPolling(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
