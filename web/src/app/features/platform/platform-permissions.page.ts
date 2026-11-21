import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { PlatformPermissionInfo } from '../../core/api/api-types';
import { errorMessage } from '../../core/errors/error-messages';
import { PlatformService } from '../../core/platform/platform.service';
import { PERMISSION_SCOPE_LABELS, permissionLabel } from '../../core/roles/permission-labels';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdLoadingState,
  KdPage,
  KdTable,
} from '../../design-system';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; permissions: PlatformPermissionInfo[] }
  | { kind: 'failed'; message: string };

/** SUPER_ADMIN view of the full permission catalog (platform level, no clinic data). */
@Component({
  selector: 'mk-platform-permissions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdActions, KdAlert, KdButton, KdLoadingState, KdPage, KdTable],
  templateUrl: './platform-permissions.page.html',
  styleUrl: './platform-permissions.page.scss',
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
