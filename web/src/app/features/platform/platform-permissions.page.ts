import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { PermissionInfo } from '../../core/api/api-types';
import { errorMessage } from '../../core/errors/error-messages';
import { PlatformService } from '../../core/platform/platform.service';
import { PERMISSION_SCOPE_LABELS, permissionLabel } from '../../core/roles/permission-labels';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; permissions: PermissionInfo[] }
  | { kind: 'failed'; message: string };

/** SUPER_ADMIN view of the full permission catalog (platform level, no clinic data). */
@Component({
  selector: 'mk-platform-permissions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './platform-permissions.page.html',
})
export class PlatformPermissionsPage {
  private readonly platform = inject(PlatformService);

  protected readonly scopeLabels = PERMISSION_SCOPE_LABELS;
  protected readonly permissionLabel = permissionLabel;
  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly permissions = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.permissions : [];
  });
  protected readonly failure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.platform.permissions().subscribe({
      next: (permissions) => this.state.set({ kind: 'loaded', permissions }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }
}
