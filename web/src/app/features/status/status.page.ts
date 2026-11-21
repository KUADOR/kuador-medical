import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { HealthStatus, SystemInfo } from '../../core/api/api-types';
import { errorMessage } from '../../core/errors/error-messages';
import { SystemInfoService } from '../../core/system/system-info.service';
import {
  KdActions,
  KdAlert,
  KdBadge,
  KdButton,
  KdDetails,
  KdLoadingState,
  KdPage,
} from '../../design-system';

type LoadState =
  { kind: 'loading' } | { kind: 'loaded'; info: SystemInfo } | { kind: 'failed'; message: string };

const HEALTH_LABELS: Record<HealthStatus, string> = {
  UP: 'Disponible',
  DOWN: 'No disponible',
};

/** Example end-to-end flow: web → API → database, shown as the system status. */
@Component({
  selector: 'mk-status-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdActions, KdAlert, KdBadge, KdButton, KdDetails, KdLoadingState, KdPage],
  templateUrl: './status.page.html',
  styleUrl: './status.page.scss',
})
export class StatusPage {
  private readonly systemInfo = inject(SystemInfoService);

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly loading = computed(() => this.state().kind === 'loading');
  protected readonly info = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.info : null;
  });
  protected readonly failure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly degraded = computed(() => {
    const info = this.info();
    return info !== null && (info.status === 'DOWN' || info.database === 'DOWN');
  });
  protected readonly healthLabels = HEALTH_LABELS;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.systemInfo.get().subscribe({
      next: (info) => this.state.set({ kind: 'loaded', info }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }
}
