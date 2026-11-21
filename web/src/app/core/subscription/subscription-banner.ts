import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { KdAlert, KdButton } from '../../design-system';
import { AppRoutes } from '../session/navigation';
import { SessionStore } from '../session/session.store';
import { subscriptionNotice } from './subscription-notice';
import { SubscriptionService } from './subscription.service';

/** Permission of the clinic data export (platform contract). */
const EXPORT_PERMISSION = 'tenant.data.export';

/**
 * Notice of the subscription status above every page of a clinic account: overdue (grace with
 * full access), suspended or cancelled (read and export only). Reloads the subscription whenever
 * the account of GET /me changes (login, branch switch, logout).
 */
@Component({
  selector: 'mk-subscription-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, KdAlert, KdButton],
  template: `
    @if (notice(); as view) {
      <kd-alert [tone]="view.tone" [heading]="view.heading" data-testid="subscription-notice">
        <p class="subscription-banner__text">{{ view.message }}</p>
        @if (view.readOnly && canExport()) {
          <a
            kdButton
            variant="tertiary"
            icon="file-text"
            data-testid="subscription-export"
            [routerLink]="exportLink"
            >Exportar datos</a
          >
        }
      </kd-alert>
    }
  `,
  styleUrl: './subscription-banner.scss',
})
export class SubscriptionBanner {
  private readonly session = inject(SessionStore);
  private readonly subscriptions = inject(SubscriptionService);

  protected readonly exportLink = AppRoutes.dataExport;
  protected readonly notice = computed(() => subscriptionNotice(this.subscriptions.subscription()));
  protected readonly canExport = computed(() =>
    (this.session.account()?.permissions as readonly string[] | undefined)?.includes(
      EXPORT_PERMISSION,
    ),
  );

  constructor() {
    effect(() => {
      const account = this.session.account();
      untracked(() => {
        if (account) {
          void this.subscriptions.load();
        } else {
          this.subscriptions.clear();
        }
      });
    });
  }
}
