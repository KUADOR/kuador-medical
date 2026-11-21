import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { KdIcon } from '../icon/icon';

let nextId = 0;

/**
 * Group of related controls on a native `<fieldset>`: a legend, an optional hint, the projected
 * controls (usually `kd-field` checkboxes or radios) and an accessible group error. The
 * fieldset gets `aria-describedby` (hint, then error) and `aria-invalid` while it has an error.
 * With `groupId` the hint and the error get the ids `<groupId>-hint` and `<groupId>-error`.
 */
@Component({
  selector: 'fieldset[kdFieldGroup]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `
    <legend class="kd-field-group__legend">{{ legend() }}</legend>
    @if (hint()) {
      <p class="kd-field-group__hint" [id]="hintId()">{{ hint() }}</p>
    }
    <ng-content select="[kdFieldGroupHint]" />
    <div class="kd-field-group__controls"><ng-content /></div>
    @if (error()) {
      <p class="kd-field-group__error" [id]="errorId()">
        <kd-icon name="circle-alert" [size]="20" />
        <span>{{ error() }}</span>
      </p>
    }
  `,
  styleUrl: './field-group.scss',
  host: {
    class: 'kd-field-group',
    '[class.kd-field-group--boxed]': 'boxed()',
    '[class.kd-field-group--invalid]': '!!error()',
    '[attr.aria-describedby]': 'describedBy()',
    '[attr.aria-invalid]': 'error() ? "true" : null',
  },
})
export class KdFieldGroup {
  readonly legend = input.required<string>();
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);
  readonly groupId = input(`kd-field-group-${++nextId}`);
  /**
   * Id of a hint projected with `[kdFieldGroupHint]` (a hint with markup or conditions): it is
   * added to `aria-describedby` like the `hint` input.
   */
  readonly projectedHintId = input<string | null>(null);
  /** Hairline frame around the group (sections of a long form). */
  readonly boxed = input(false, { transform: booleanAttribute });

  readonly hintId = computed(() => `${this.groupId()}-hint`);
  readonly errorId = computed(() => `${this.groupId()}-error`);
  protected readonly describedBy = computed(() => {
    const ids = [
      this.hint() ? this.hintId() : null,
      this.projectedHintId(),
      this.error() ? this.errorId() : null,
    ];
    return ids.filter(Boolean).join(' ') || null;
  });
}
