import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render, settle } from '../../../testing/design-system';
import { KdErrorSummary, type KdFormError } from './error-summary';
import { KdField } from './field';
import { KdInput } from './input';

@Component({
  imports: [ReactiveFormsModule, KdField, KdInput, KdErrorSummary],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <kd-error-summary [errors]="errors()" />
      <kd-field label="Correo" hint="Usa el correo de tu cuenta.">
        <input kdInput type="email" formControlName="email" autocomplete="email" />
      </kd-field>
      <kd-field label="Especialidad">
        <select kdInput formControlName="specialty">
          <option value="">Elige una</option>
          <option value="general">Medicina general</option>
        </select>
      </kd-field>
      <kd-field label="Notas" [error]="serverError()">
        <textarea kdInput id="notes" formControlName="notes"></textarea>
      </kd-field>
      <kd-field label="Acepto los términos">
        <input kdInput type="checkbox" formControlName="terms" />
      </kd-field>
      <button type="submit">Guardar</button>
    </form>
  `,
})
class Host {
  readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    specialty: [''],
    notes: [''],
    terms: [false],
  });
  readonly serverError = signal<string | null>(null);
  readonly errors = signal<KdFormError[]>([]);

  submit(): void {
    this.form.markAllAsTouched();
  }
}

@Component({
  imports: [KdField, KdInput],
  template: `
    <kd-field label="Rol" error="Elige un rol.">
      <select kdInput [id]="'branch-' + branch()"><option>Sin acceso</option></select>
    </kd-field>
  `,
})
class BoundIdHost {
  readonly branch = signal('b1');
}

const q = <T extends Element>(root: ParentNode, selector: string) =>
  root.querySelector<T>(selector)!;

describe('KdField and KdInput', () => {
  it('links the label and the hint to the control', async () => {
    const root = el(await render(Host));
    const input = q<HTMLInputElement>(root, 'input[type="email"]');
    const label = q<HTMLLabelElement>(root, 'kd-field label');

    expect(input.id).toMatch(/^kd-field-\d+$/);
    expect(label.htmlFor).toBe(input.id);
    expect(label.textContent?.trim()).toBe('Correo');
    const hint = q(root, `#${input.getAttribute('aria-describedby')}`);
    expect(hint.textContent?.trim()).toBe('Usa el correo de tu cuenta.');
  });

  it('keeps an id set by the page', async () => {
    const root = el(await render(Host));

    expect(q<HTMLLabelElement>(root, 'label[for="notes"]').textContent?.trim()).toBe('Notas');
  });

  it('keeps an id bound by the page, e.g. one per row (MEDK-44)', async () => {
    const root = el(await render(BoundIdHost));
    const select = q<HTMLSelectElement>(root, 'select');

    expect(select.id).toBe('branch-b1');
    expect(q<HTMLLabelElement>(root, 'kd-field label').htmlFor).toBe('branch-b1');
    expect(select.getAttribute('aria-describedby')).toBe('branch-b1-error');
  });

  it('marks required controls with aria-required', async () => {
    const root = el(await render(Host));

    expect(q(root, 'input[type="email"]').getAttribute('aria-required')).toBe('true');
    expect(q(root, 'select').getAttribute('aria-required')).toBeNull();
  });

  it('shows no error before the person interacts with the field', async () => {
    const root = el(await render(Host));

    expect(root.querySelector('.kd-field__error')).toBeNull();
    expect(q(root, 'input[type="email"]').getAttribute('aria-invalid')).toBeNull();
  });

  it('shows the Spanish error after submit, linked with aria-describedby and aria-invalid', async () => {
    const fixture = await render(Host);
    const root = el(fixture);
    q<HTMLButtonElement>(root, 'button[type="submit"]').click();
    await settle(fixture);

    const input = q(root, 'input[type="email"]');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const ids = input.getAttribute('aria-describedby')!.split(' ');
    expect(ids).toHaveLength(2);
    const error = q(root, `#${ids[1]}`);
    expect(error.textContent?.trim()).toBe('Este campo es obligatorio.');
    // R-08: the error state is said with an icon and text, not only with color.
    expect(error.querySelector('kd-icon')).not.toBeNull();
  });

  it('updates the error as the value changes', async () => {
    const fixture = await render(Host);
    const root = el(fixture);
    const control = fixture.componentInstance.form.controls.email;
    control.markAsTouched();
    control.setValue('no-es-correo');
    await settle(fixture);
    expect(q(root, '.kd-field__error').textContent).toContain('Escribe un correo válido');

    control.setValue('persona@clinica.example.test');
    await settle(fixture);
    expect(root.querySelector('.kd-field__error')).toBeNull();
    expect(q(root, 'input[type="email"]').getAttribute('aria-invalid')).toBeNull();
  });

  it('shows an error given by the page (e.g. from the API)', async () => {
    const fixture = await render(Host);
    fixture.componentInstance.serverError.set('Ya existe una nota igual.');
    await settle(fixture);
    const root = el(fixture);

    expect(q(root, '#notes').getAttribute('aria-invalid')).toBe('true');
    expect(q(root, '#notes-error').textContent?.trim()).toBe('Ya existe una nota igual.');
  });

  it('lays out checkboxes with the label after the control', async () => {
    const root = el(await render(Host));
    const fields = root.querySelectorAll('kd-field');

    expect(fields[3].classList).toContain('kd-field--check');
    expect(fields[0].classList).not.toContain('kd-field--check');
  });

  it('has no accessibility violations, also with errors (axe)', async () => {
    const fixture = await render(Host);
    await expectNoAxeViolations(el(fixture));

    q<HTMLButtonElement>(el(fixture), 'button[type="submit"]').click();
    fixture.componentInstance.serverError.set('Ya existe una nota igual.');
    fixture.componentInstance.errors.set([{ message: 'Revisa el correo.', fieldId: 'notes' }]);
    await settle(fixture);
    await expectNoAxeViolations(el(fixture));
  });
});

describe('KdErrorSummary', () => {
  it('is not rendered without errors', async () => {
    expect(el(await render(Host)).querySelector('.kd-error-summary')).toBeNull();
  });

  it('lists the errors as an alert and moves focus to the field of a link', async () => {
    const fixture = await render(Host);
    fixture.componentInstance.errors.set([
      { message: 'Las notas son demasiado largas.', fieldId: 'notes' },
      { message: 'No pudimos validar la especialidad.' },
    ]);
    await settle(fixture);
    const root = el(fixture);
    const summary = q(root, '.kd-error-summary');

    expect(summary.getAttribute('role')).toBe('alert');
    expect(summary.querySelector('h2')?.textContent?.trim()).toBe('Revisa los datos marcados');
    const items = summary.querySelectorAll('li');
    expect(items).toHaveLength(2);
    expect(items[1].querySelector('a')).toBeNull();

    q<HTMLAnchorElement>(summary, 'a[href="#notes"]').click();
    expect(document.activeElement?.id).toBe('notes');
  });
});
