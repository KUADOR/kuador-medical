import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { startWith, switchMap } from 'rxjs';
import type { GuardianPersonInput } from '../../../core/api/api-types';
import { DOCUMENT_TYPES } from '../../../core/catalogs/patient-catalogs';
import { controlErrorMessage, e164Phone, notBlank } from '../../../core/forms/form-errors';

export type GuardianPersonGroup = FormGroup<{
  documentType: FormControl<string>;
  documentNumber: FormControl<string>;
  firstNames: FormControl<string>;
  lastNames: FormControl<string>;
  email: FormControl<string>;
  phone: FormControl<string>;
}>;

type PersonField = keyof GuardianPersonGroup['controls'];

/** Form group of a new guardian or authorized person (GuardianPersonInput of the contract). */
export function guardianPersonGroup(fb: NonNullableFormBuilder): GuardianPersonGroup {
  return fb.group({
    documentType: [DOCUMENT_TYPES[0]?.code ?? '', [Validators.required]],
    documentNumber: ['', [Validators.required, Validators.maxLength(30), notBlank]],
    firstNames: ['', [Validators.required, Validators.maxLength(120), notBlank]],
    lastNames: ['', [Validators.required, Validators.maxLength(120), notBlank]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.maxLength(20), e164Phone]],
  });
}

export function toGuardianPerson(group: GuardianPersonGroup): GuardianPersonInput {
  const value = group.getRawValue();
  return {
    documentType: value.documentType,
    documentNumber: value.documentNumber.trim(),
    firstNames: value.firstNames.trim(),
    lastNames: value.lastNames.trim(),
    email: optional(value.email),
    phone: optional(value.phone),
  };
}

export function optional(text: string): string | undefined {
  return text.trim() ? text.trim() : undefined;
}

/**
 * Identification and contact fields of a guardian or authorized person. Shared by the patient
 * registration, the "add legal guardian" form and the adult patient authorization.
 */
@Component({
  selector: 'mk-guardian-person-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <ng-container [formGroup]="group()">
      <div class="field">
        <label [for]="id('documentType')">Tipo de documento</label>
        <select
          [id]="id('documentType')"
          formControlName="documentType"
          [attr.aria-invalid]="error('documentType') ? 'true' : null"
          [attr.aria-describedby]="error('documentType') ? id('documentType-error') : null"
        >
          @for (option of documentTypes; track option.code) {
            <option [value]="option.code">{{ option.label }}</option>
          }
        </select>
        @if (error('documentType'); as message) {
          <p [id]="id('documentType-error')" class="field__error">{{ message }}</p>
        }
      </div>

      @for (field of textFields; track field.name) {
        <div class="field">
          <label [for]="id(field.name)">{{ field.label }}</label>
          <input
            [id]="id(field.name)"
            [type]="field.type"
            [formControlName]="field.name"
            [attr.autocomplete]="field.autocomplete"
            [attr.aria-invalid]="error(field.name) ? 'true' : null"
            [attr.aria-describedby]="describedBy(field.name, field.hint)"
          />
          @if (field.hint) {
            <p [id]="id(field.name + '-hint')" class="field__hint">{{ field.hint }}</p>
          }
          @if (error(field.name); as message) {
            <p [id]="id(field.name + '-error')" class="field__error">{{ message }}</p>
          }
        </div>
      }
    </ng-container>
  `,
})
export class GuardianPersonFields {
  readonly group = input.required<GuardianPersonGroup>();
  /** Prefix of the element ids, unique per page (e.g. "guardian"). */
  readonly idPrefix = input.required<string>();

  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly textFields: readonly {
    name: Exclude<PersonField, 'documentType'>;
    label: string;
    type: string;
    autocomplete: string;
    hint?: string;
  }[] = [
    { name: 'documentNumber', label: 'Número de documento', type: 'text', autocomplete: 'off' },
    { name: 'firstNames', label: 'Nombres', type: 'text', autocomplete: 'off' },
    { name: 'lastNames', label: 'Apellidos', type: 'text', autocomplete: 'off' },
    { name: 'email', label: 'Correo (opcional)', type: 'email', autocomplete: 'off' },
    {
      name: 'phone',
      label: 'Teléfono (opcional)',
      type: 'tel',
      autocomplete: 'off',
      hint: 'Con el signo + y el código de país, sin espacios.',
    },
  ];

  /** Re-renders when the parent marks the group as touched or the server sets errors. */
  private readonly changes = toSignal(
    toObservable(this.group).pipe(switchMap((group) => group.events.pipe(startWith(null)))),
  );

  protected id(suffix: string): string {
    return `${this.idPrefix()}-${suffix}`;
  }

  protected error(name: PersonField): string | null {
    this.changes();
    const control = this.group().controls[name];
    return control.touched ? controlErrorMessage(control) : null;
  }

  protected describedBy(name: PersonField, hint?: string): string | null {
    const ids = [
      hint ? this.id(`${name}-hint`) : null,
      this.error(name) ? this.id(`${name}-error`) : null,
    ];
    return ids.filter(Boolean).join(' ') || null;
  }
}
