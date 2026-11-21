import {
  AfterContentInit,
  DestroyRef,
  Directive,
  ElementRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { NgControl, Validators } from '@angular/forms';
import { controlErrorMessage } from '../../core/forms/form-errors';
import { KdField, type KdFieldControl } from './field';

/**
 * Marks the control of a `kd-field` and wires its accessibility: id, `aria-describedby`
 * (hint and error), `aria-invalid` while an error is shown and `aria-required` for required
 * reactive controls. Works with `input`, `select` and `textarea`.
 */
@Directive({
  selector: 'input[kdInput], select[kdInput], textarea[kdInput]',
  host: {
    class: 'kd-input',
    '[id]': 'id()',
    '[attr.aria-describedby]': 'field?.describedBy() ?? null',
    '[attr.aria-invalid]': 'field?.errorMessage() ? "true" : null',
    '[attr.aria-required]': 'required() ? "true" : null',
  },
})
export class KdInput implements KdFieldControl, AfterContentInit {
  protected readonly field = inject(KdField, { optional: true });
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly ngControl = inject(NgControl, { optional: true, self: true });

  private readonly ownId = this.element.getAttribute('id');
  /** Id bound by the page (`[id]="..."`); a static `id="..."` also lands here. */
  readonly boundId = input<string | null | undefined>(undefined, { alias: 'id' });
  /** Bumped on every value, status or touched change of the reactive control. */
  private readonly version = signal(0);

  readonly id = () => this.boundId() || this.ownId || this.field?.generatedId() || '';
  readonly isCheck = () =>
    this.element instanceof HTMLInputElement &&
    (this.element.type === 'checkbox' || this.element.type === 'radio');

  readonly controlError = computed(() => {
    this.version();
    const control = this.ngControl?.control;
    return control?.touched ? controlErrorMessage(control) : null;
  });

  protected readonly required = computed(() => {
    this.version();
    return this.ngControl?.control?.hasValidator(Validators.required) ?? false;
  });

  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.field?.control.set(this);
    this.destroyRef.onDestroy(() => {
      if (this.field?.control() === this) {
        this.field.control.set(null);
      }
    });
  }

  // The form directives create the control in ngOnChanges, so it exists from here on.
  ngAfterContentInit(): void {
    const control = this.ngControl?.control;
    if (!control) {
      return;
    }
    const subscription = control.events.subscribe(() => this.version.update((v) => v + 1));
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
    this.version.update((v) => v + 1);
  }
}
