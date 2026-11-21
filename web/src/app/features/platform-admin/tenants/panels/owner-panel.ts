import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { Tenant } from '../../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../../core/errors/error-messages';
import { applyServerErrors, notBlank } from '../../../../core/forms/form-errors';
import {
  PlatformCreationKey,
  PlatformTenantsService,
  newPlatformCreationKey,
} from '../../../../core/platform/platform-tenants.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdDetails,
  KdField,
  KdInput,
} from '../../../../design-system';
import { isRetryable } from '../../../../core/errors/retryable';
import { INVITATION_STATUS_LABELS, TENANT_LIMITS, formatDateTime } from '../tenant-labels';

type Busy = 'resend' | 'reset' | null;

/**
 * OWNER of the clinic: onboarding state, resend of the one-time invitation (optionally to a
 * corrected email; the previous link stops working) and reset of the second factor with a
 * mandatory reason (plan D7). Both are audited by the api.
 */
@Component({
  selector: 'mk-owner-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, KdActions, KdAlert, KdButton, KdDetails, KdField, KdInput],
  templateUrl: './owner-panel.html',
  styleUrl: '../tenants.scss',
})
export class OwnerPanel {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly tenant = input.required<Tenant>();
  /** Something of the OWNER changed: the page reads the clinic again. */
  readonly changed = output<void>();

  protected readonly limits = TENANT_LIMITS;
  protected readonly invitationLabels = INVITATION_STATUS_LABELS;
  protected readonly formatDateTime = formatDateTime;

  protected readonly owner = computed(() => this.tenant().owner);
  /** The invitation can be resent while the OWNER has not accepted it. */
  protected readonly canResend = computed(() => {
    const invitation = this.owner().invitation;
    return invitation !== null && invitation.status !== 'ACCEPTED';
  });

  protected readonly busy = signal<Busy>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly confirmingReset = signal(false);

  protected readonly resendForm = this.fb.group({
    email: ['', [Validators.email, Validators.maxLength(TENANT_LIMITS.email)]],
  });
  protected readonly resetForm = this.fb.group({
    reason: ['', [Validators.required, Validators.maxLength(TENANT_LIMITS.reason), notBlank]],
  });

  private resendKey: PlatformCreationKey | null = null;

  protected resend(): void {
    if (this.busy() || !this.canResend()) {
      return;
    }
    this.clearMessages();
    if (this.resendForm.invalid) {
      this.resendForm.markAllAsTouched();
      this.focus('#owner-new-email');
      return;
    }
    const email = this.resendForm.getRawValue().email.trim() || null;
    this.resendKey ??= newPlatformCreationKey();
    this.busy.set('resend');
    this.tenants.resendOwnerInvitation(this.tenant().id, email, this.resendKey).subscribe({
      next: (invitation) => {
        this.busy.set(null);
        this.resendKey = null;
        this.resendForm.reset({ email: '' });
        this.notice.set(
          `Enviamos una nueva invitación a ${invitation.maskedEmail}. El enlace anterior ya no sirve.`,
        );
        this.changed.emit();
      },
      error: (error: unknown) => {
        if (!isRetryable(error)) {
          this.resendKey = null;
        }
        if (toProblem(error)?.code === 'VALIDATION') {
          applyServerErrors(this.resendForm, error);
        }
        this.fail(error);
      },
    });
  }

  protected askReset(): void {
    this.clearMessages();
    this.confirmingReset.set(true);
    this.focus('#owner-reset-reason');
  }

  protected cancelReset(): void {
    this.confirmingReset.set(false);
    this.resetForm.reset({ reason: '' });
    this.focus('[data-testid="reset-two-factor"]');
  }

  protected confirmReset(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    if (this.resetForm.invalid) {
      this.resetForm.markAllAsTouched();
      this.focus('#owner-reset-reason');
      return;
    }
    this.busy.set('reset');
    this.tenants
      .resetOwnerTwoFactor(this.tenant().id, this.resetForm.getRawValue().reason.trim())
      .subscribe({
        next: () => {
          this.busy.set(null);
          this.confirmingReset.set(false);
          this.resetForm.reset({ reason: '' });
          this.notice.set(
            'Se restableció la verificación en dos pasos. El OWNER deberá configurarla de nuevo en su próximo ingreso.',
          );
          this.changed.emit();
        },
        error: (error: unknown) => {
          if (toProblem(error)?.code === 'VALIDATION') {
            applyServerErrors(this.resetForm, error);
          }
          this.fail(error);
        },
      });
  }

  private fail(error: unknown): void {
    this.busy.set(null);
    this.failure.set(errorMessage(error));
    this.focus('[data-testid="owner-failure"]');
  }

  private clearMessages(): void {
    this.notice.set(null);
    this.failure.set(null);
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
