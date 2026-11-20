import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  model,
  output,
  viewChild,
} from '@angular/core';
import { KdIconButton } from '../button/button';

let nextId = 0;

/**
 * Modal dialog on the native `<dialog>`: the browser traps the focus, closes it with Escape and
 * returns the focus to the element that opened it. Bind `[(open)]`; `closed` fires each time it
 * closes. Actions go in `[kdDialogActions]` (one primary or danger action at most, R-02).
 */
@Component({
  selector: 'kd-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIconButton],
  template: `
    <dialog
      #dialog
      class="kd-dialog"
      [attr.aria-labelledby]="headingId"
      [attr.aria-describedby]="description() ? descriptionId : null"
      (close)="onClose()"
    >
      <div class="kd-dialog__header">
        <h2 class="kd-dialog__heading" [id]="headingId">{{ heading() }}</h2>
        <button kdIconButton type="button" icon="x" [label]="closeLabel()" (click)="open.set(false)"></button>
      </div>
      @if (description()) {
        <p class="kd-dialog__description" [id]="descriptionId">{{ description() }}</p>
      }
      <div class="kd-dialog__body"><ng-content /></div>
      <div class="kd-dialog__actions"><ng-content select="[kdDialogActions]" /></div>
    </dialog>
  `,
  styleUrl: './dialog.scss',
})
export class KdDialog {
  readonly heading = input.required<string>();
  readonly description = input<string | null>(null);
  readonly closeLabel = input('Cerrar');
  readonly open = model(false);
  readonly closed = output<void>();

  protected readonly headingId = `kd-dialog-${++nextId}-title`;
  protected readonly descriptionId = `kd-dialog-${nextId}-description`;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const dialog = this.dialog().nativeElement;
      if (this.open() && !dialog.open) {
        dialog.showModal();
      } else if (!this.open() && dialog.open) {
        dialog.close();
      }
    });
  }

  protected onClose(): void {
    this.open.set(false);
    this.closed.emit();
  }
}
