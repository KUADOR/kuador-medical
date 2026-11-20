import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { KdAlert, KdField, KdIcon, KdInput } from '../../../design-system';
import { GENERIC_ERROR_MESSAGE, errorMessage } from '../../../core/errors/error-messages';
import { AuthService } from '../../../core/session/auth.service';
import { landingUrl } from '../../../core/session/navigation';
import { SessionStore } from '../../../core/session/session.store';

/**
 * Active branch of the session in the header. With more than one assigned branch it becomes a
 * selector: switching issues new tokens with the permissions of that branch (ADR-013), reloads
 * GET /me and goes to the landing page, because the current page may no longer be allowed.
 */
@Component({
  selector: 'mk-branch-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdField, KdInput, KdAlert, KdIcon],
  template: `
    @if (activeBranch(); as active) {
      <div class="branch-switcher">
        @if (branches().length > 1) {
          <kd-field label="Sucursal">
            <select
              kdInput
              id="branch-switcher"
              data-testid="branch-switcher"
              [value]="active.id"
              [disabled]="switching()"
              (change)="switchTo($event)"
            >
              @for (branch of branches(); track branch.id) {
                <option [value]="branch.id" [selected]="branch.id === active.id">
                  {{ branch.name }}
                </option>
              }
            </select>
          </kd-field>
        } @else {
          <p class="branch-switcher__active" data-testid="active-branch">
            <kd-icon name="map-pin" [size]="20" />
            <span>Sucursal: {{ active.name }}</span>
          </p>
        }
        @if (failure(); as message) {
          <kd-alert tone="error">{{ message }}</kd-alert>
        }
        @if (switching()) {
          <span class="visually-hidden" role="status">Cambiando de sucursal…</span>
        }
      </div>
    }
  `,
  styleUrl: './branch-switcher.scss',
})
export class BranchSwitcher {
  private readonly session = inject(SessionStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly activeBranch = computed(() => this.session.account()?.activeBranch ?? null);
  protected readonly branches = computed(() => this.session.account()?.availableBranches ?? []);
  protected readonly switching = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected async switchTo(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const branchId = select.value;
    const current = this.activeBranch();
    if (!current || branchId === current.id || this.switching()) {
      return;
    }
    this.failure.set(null);
    this.switching.set(true);
    try {
      const account = await this.auth.switchBranch(branchId);
      if (!account) {
        this.failure.set(GENERIC_ERROR_MESSAGE);
        return;
      }
      await this.router.navigateByUrl(landingUrl(account));
    } catch (error: unknown) {
      select.value = current.id;
      this.failure.set(errorMessage(error));
    } finally {
      this.switching.set(false);
    }
  }
}
