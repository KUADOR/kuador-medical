import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Renderer2,
  computed,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { ICONS, type IconName } from './icons.generated';

/**
 * Line icon of the identity guide set (Lucide, DESIGN.md § Iconography). The color comes from
 * the text (`currentColor`). Decorative by default (hidden from assistive technology); give it
 * a `label` when the icon alone carries meaning.
 */
@Component({
  selector: 'kd-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    #svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    focusable="false"
    aria-hidden="true"
  ></svg>`,
  styleUrl: './icon.scss',
  host: {
    class: 'kd-icon',
    '[class.kd-icon--20]': 'size() === 20',
    '[class.kd-icon--24]': 'size() === 24',
    '[attr.role]': 'label() ? "img" : null',
    '[attr.aria-label]': 'label() || null',
    '[attr.aria-hidden]': 'label() ? null : "true"',
  },
})
export class KdIcon {
  readonly name = input.required<IconName>();
  /** Accessible name; without it the icon is decorative. */
  readonly label = input<string | null>(null);
  readonly size = input<20 | 24>(24);

  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');
  private readonly renderer = inject(Renderer2);
  private readonly shapes = computed(() => ICONS[this.name()]);

  constructor() {
    // Plain SVG shapes from the generated set, created one by one: no innerHTML.
    effect(() => {
      const svg = this.svg().nativeElement;
      for (const child of Array.from(svg.childNodes)) {
        this.renderer.removeChild(svg, child);
      }
      for (const shape of this.shapes()) {
        const node = this.renderer.createElement(shape.tag, 'svg');
        for (const [key, value] of Object.entries(shape.attributes)) {
          this.renderer.setAttribute(node, key, value);
        }
        this.renderer.appendChild(svg, node);
      }
    });
  }
}
