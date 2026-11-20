import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { GuardianLink } from '../../../core/api/api-types';
import {
  END_REASON_LABELS,
  LINK_TYPE_LABELS,
  formatDate,
  fullName,
} from '../../../core/patients/patient-labels';
import { KdButton, KdEmptyState, KdList, KdListItem } from '../../../design-system';

/** Current links first, then the history, each newest first. */
export function splitLinks(links: readonly GuardianLink[]): {
  current: GuardianLink[];
  history: GuardianLink[];
} {
  const newestFirst = [...links].sort((a, b) => b.validFrom.localeCompare(a.validFrom));
  return {
    current: newestFirst.filter((link) => link.current),
    history: newestFirst.filter((link) => !link.current),
  };
}

/** Current and historical guardian links, with start and end dates (FR-028). Design system list. */
@Component({
  selector: 'mk-guardian-link-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdButton, KdEmptyState, KdList, KdListItem],
  templateUrl: './guardian-link-list.html',
  styleUrl: './guardian-link-list.scss',
})
export class GuardianLinkList {
  readonly links = input.required<readonly GuardianLink[]>();
  readonly canEnd = input(false);
  readonly emptyMessage = input('No hay vínculos registrados.');
  readonly end = output<GuardianLink>();

  protected readonly groups = computed(() => splitLinks(this.links()));
  protected readonly linkTypeLabels = LINK_TYPE_LABELS;
  protected readonly endReasonLabels = END_REASON_LABELS;
  protected readonly formatDate = formatDate;
  protected readonly fullName = fullName;

  /** Relationship, start date and, for links of an adult, who authorized it. */
  protected describe(link: GuardianLink): string {
    const parts = [link.relationship, `desde ${formatDate(link.validFrom)}`];
    if (link.authorizedBy) {
      parts.push('Autorizado por el paciente.');
    }
    return parts.join(' · ');
  }
}
