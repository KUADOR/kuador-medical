import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * On/off switch on a native `<button role="switch">`: the page keeps the click handler and the
 * label (projected text or `aria-labelledby`); this sets `aria-checked` and the look. The state
 * is said by the position and the page's text, not only by color (R-08).
 */
@Component({
  selector: 'button[kdSwitch]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="kd-switch__track" aria-hidden="true"></span>
    <span class="kd-switch__label"><ng-content /></span>`,
  styleUrl: './switch.scss',
  host: {
    class: 'kd-switch',
    role: 'switch',
    '[attr.aria-checked]': 'checked() ? "true" : "false"',
  },
})
export class KdSwitch {
  readonly checked = input(false, { transform: booleanAttribute });
}
