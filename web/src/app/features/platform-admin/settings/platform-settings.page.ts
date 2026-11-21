import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import type { PlatformSettings } from '../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../core/errors/error-messages';
import { applyServerErrors, focusFirstInvalid, integer } from '../../../core/forms/form-errors';
import { SETTING_FIELDS, type SettingField } from '../../../core/platform/plan-labels';
import { PlatformSettingsService } from '../../../core/platform/plans.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdFieldGroup,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../../design-system';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; settings: PlatformSettings }
  | { kind: 'failed'; message: string };

type SettingKey = SettingField['key'];

/**
 * Platform settings (SUPER_ADMIN, platform.settings.manage): the configurable periods of the
 * subscription lifecycle (MEDK-49, plan D10). New values apply to periods computed from now on.
 */
@Component({
  selector: 'mk-platform-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdFieldGroup,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './platform-settings.page.html',
  styleUrl: './platform-settings.page.scss',
})
export class PlatformSettingsPage {
  private readonly settingsService = inject(PlatformSettingsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly fields = SETTING_FIELDS;
  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly saving = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly versionConflict = signal(false);
  protected readonly notice = signal<string | null>(null);

  protected readonly form: FormGroup<Record<SettingKey, FormControl<number | null>>>;

  constructor() {
    const fb = inject(NonNullableFormBuilder);
    const controls = {} as Record<SettingKey, FormControl<number | null>>;
    for (const field of SETTING_FIELDS) {
      controls[field.key] = fb.control<number | null>(null, [
        Validators.required,
        integer,
        Validators.min(field.min),
        Validators.max(field.max),
      ]);
    }
    this.form = fb.group(controls);
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.failure.set(null);
    this.versionConflict.set(false);
    this.settingsService.get().subscribe({
      next: (settings) => {
        this.fill(settings);
        this.state.set({ kind: 'ready', settings });
      },
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected submit(): void {
    const current = this.state();
    if (this.saving() || current.kind !== 'ready') {
      return;
    }
    this.failure.set(null);
    this.versionConflict.set(false);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }
    const value = this.form.getRawValue();
    const settings = { version: current.settings.version } as PlatformSettings;
    for (const field of SETTING_FIELDS) {
      settings[field.key] = Number(value[field.key]);
    }
    this.saving.set(true);
    this.settingsService.update(settings).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.fill(saved);
        this.state.set({ kind: 'ready', settings: saved });
        this.notice.set(
          'Se guardaron los ajustes. Aplican a los plazos que se calculen desde ahora.',
        );
      },
      error: (error: unknown) => {
        this.saving.set(false);
        const code = toProblem(error)?.code;
        if (code === 'VALIDATION') {
          applyServerErrors(this.form, error);
        } else if (code === 'VERSION_CONFLICT') {
          this.versionConflict.set(true);
        }
        this.failure.set(errorMessage(error));
        afterNextRender(
          () =>
            (
              this.host.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]') ??
              this.host.nativeElement.querySelector<HTMLElement>('[data-testid="form-error"]')
            )?.focus(),
          { injector: this.injector },
        );
      },
    });
  }

  private fill(settings: PlatformSettings): void {
    const values = {} as Record<SettingKey, number | null>;
    for (const field of SETTING_FIELDS) {
      values[field.key] = settings[field.key];
    }
    this.form.reset(values);
  }
}
