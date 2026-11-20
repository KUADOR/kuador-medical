import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { KdIcon } from '../icon/icon';

/** What a KdInput tells its field: its id, and the error to show (if any). */
export interface KdFieldControl {
  readonly id: () => string;
  readonly isCheck: () => boolean;
  /** Spanish message of the control's first error, once the person has touched it. */
  readonly controlError: () => string | null;
}

let nextId = 0;

/**
 * Form field: label, optional hint and an accessible error around one `kdInput` control
 * (`input`, `select`, `textarea` or a checkbox). The control gets the `id`, `aria-describedby`,
 * `aria-invalid` and `aria-required` attributes from here.
 *
 * The error is the page's `error` input (e.g. a message from the API) or, if none, the Spanish
 * message of the reactive form control once it is touched (after blur or `markAllAsTouched()`).
 */
@Component({
  selector: 'kd-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `
    <label class="kd-field__label" [for]="controlId()">{{ label() }}</label>
    <ng-content />
    @if (hint()) {
      <p class="kd-field__hint" [id]="hintId()">{{ hint() }}</p>
    }
    @if (errorMessage(); as message) {
      <p class="kd-field__error" [id]="errorId()">
        <kd-icon name="circle-alert" [size]="20" />
        <span>{{ message }}</span>
      </p>
    }
  `,
  styleUrl: './field.scss',
  host: {
    class: 'kd-field',
    '[class.kd-field--check]': 'control()?.isCheck()',
    '[class.kd-field--invalid]': 'errorMessage() !== null',
  },
})
export class KdField {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);
  /** Error set by the page; it wins over the control's own validation message. */
  readonly error = input<string | null>(null);

  private readonly fallbackId = `kd-field-${++nextId}`;
  /** @internal set by KdInput */
  readonly control = signal<KdFieldControl | null>(null);

  readonly controlId = computed(() => this.control()?.id() ?? this.fallbackId);
  readonly hintId = computed(() => `${this.controlId()}-hint`);
  readonly errorId = computed(() => `${this.controlId()}-error`);
  readonly errorMessage = computed(() => this.error() || this.control()?.controlError() || null);
  /** Ids for the control's aria-describedby: hint first, then the error. */
  readonly describedBy = computed(() => {
    const ids = [this.hint() ? this.hintId() : null, this.errorMessage() ? this.errorId() : null];
    return ids.filter(Boolean).join(' ') || null;
  });

  /** @internal a default id for controls without their own. */
  generatedId(): string {
    return this.fallbackId;
  }
}
