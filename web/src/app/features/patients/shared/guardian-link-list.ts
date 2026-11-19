import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { GuardianLink } from '../../../core/api/api-types';
import {
  END_REASON_LABELS,
  LINK_TYPE_LABELS,
  formatDate,
  fullName,
} from '../../../core/patients/patient-labels';

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

/** Current and historical guardian links, with start and end dates (FR-028). */
@Component({
  selector: 'mk-guardian-link-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (links().length === 0) {
      <p class="hint" data-testid="links-empty">{{ emptyMessage() }}</p>
    } @else {
      <h3>Vigentes</h3>
      @if (groups().current.length === 0) {
        <p class="hint" data-testid="no-current">No hay vínculos vigentes.</p>
      } @else {
        <ul class="card-list" aria-label="Vínculos vigentes">
          @for (link of groups().current; track link.id) {
            <li class="card" data-testid="current-link">
              <div class="card__title">
                <strong>{{ fullName(link.guardian) }}</strong>
                <span class="badge">{{ linkTypeLabels[link.linkType] }}</span>
              </div>
              <p>{{ link.relationship }} · desde {{ formatDate(link.validFrom) }}</p>
              @if (link.authorizedBy) {
                <p class="hint">Autorizado por el paciente.</p>
              }
              @if (canEnd()) {
                <div class="actions">
                  <button
                    type="button"
                    class="button button--danger"
                    [attr.data-end-link]="link.id"
                    (click)="end.emit(link)"
                  >
                    Terminar vínculo<span class="visually-hidden">
                      con {{ fullName(link.guardian) }}</span
                    >
                  </button>
                </div>
              }
            </li>
          }
        </ul>
      }

      <h3>Historial</h3>
      @if (groups().history.length === 0) {
        <p class="hint" data-testid="no-history">No hay vínculos terminados.</p>
      } @else {
        <div class="table-wrapper">
          <table class="table" data-testid="link-history">
            <caption class="visually-hidden">
              Historial de vínculos
            </caption>
            <thead>
              <tr>
                <th scope="col">Persona</th>
                <th scope="col">Tipo</th>
                <th scope="col">Relación</th>
                <th scope="col">Desde</th>
                <th scope="col">Hasta</th>
                <th scope="col">Motivo</th>
              </tr>
            </thead>
            <tbody>
              @for (link of groups().history; track link.id) {
                <tr data-testid="past-link">
                  <th scope="row">{{ fullName(link.guardian) }}</th>
                  <td>{{ linkTypeLabels[link.linkType] }}</td>
                  <td>{{ link.relationship }}</td>
                  <td>{{ formatDate(link.validFrom) }}</td>
                  <td>{{ formatDate(link.validTo) }}</td>
                  <td>{{ link.endReason ? endReasonLabels[link.endReason] : '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }
  `,
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
}
