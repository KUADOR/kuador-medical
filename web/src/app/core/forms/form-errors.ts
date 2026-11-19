import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import type { FieldError } from '../api/api-types';
import { fieldErrorMessage, toProblem } from '../errors/error-messages';

/** Password policy of the contract: at least 12 characters (WEAK_PASSWORD otherwise). */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;

/** Spanish message for the first client-side or server-side error of a control. */
export function controlErrorMessage(control: AbstractControl | null): string | null {
  const errors = control?.errors;
  if (!errors) {
    return null;
  }
  if (typeof errors['server'] === 'string') {
    return errors['server'];
  }
  if (errors['required']) {
    return 'Este campo es obligatorio.';
  }
  if (errors['email']) {
    return 'Escribe un correo válido, por ejemplo nombre@clinica.com.';
  }
  if (errors['minlength']) {
    return `Debe tener al menos ${errors['minlength'].requiredLength} caracteres.`;
  }
  if (errors['maxlength']) {
    return `Debe tener como máximo ${errors['maxlength'].requiredLength} caracteres.`;
  }
  if (errors['whitespace']) {
    return 'Este campo no puede quedar solo con espacios.';
  }
  if (errors['mismatch']) {
    return 'Las contraseñas no coinciden.';
  }
  if (errors['samePassword']) {
    return 'La contraseña nueva debe ser distinta de la actual.';
  }
  return 'El valor no es válido.';
}

/** Rejects values made only of spaces (the API trims and requires at least one character). */
export const notBlank: ValidatorFn = (control): ValidationErrors | null => {
  const value: unknown = control.value;
  return typeof value === 'string' && value.length > 0 && value.trim().length === 0
    ? { whitespace: true }
    : null;
};

/** Group validator: `confirm` must equal `source`; the error is set on the `confirm` control. */
export function matchingFields(source: string, confirm: string): ValidatorFn {
  return (group): ValidationErrors | null => {
    const target = group.get(confirm);
    if (!target) {
      return null;
    }
    const mismatch = target.value !== '' && group.get(source)?.value !== target.value;
    const { mismatch: _previous, ...others } = target.errors ?? {};
    if (mismatch) {
      target.setErrors({ ...others, mismatch: true });
    } else if (target.hasError('mismatch')) {
      target.setErrors(Object.keys(others).length ? others : null);
    }
    return null;
  };
}

/** Field name of a contract error path: `permissions[0]` → `permissions`, `a.b` → `a`. */
export function fieldName(path: string): string {
  return path.split(/[.[]/, 1)[0];
}

/**
 * Puts the field errors of a VALIDATION problem on the matching form controls.
 * Returns the errors that did not match any control, so the page can show them in its summary.
 */
export function applyServerErrors(form: FormGroup, error: unknown): FieldError[] {
  const fieldErrors = toProblem(error)?.errors ?? [];
  const unmatched: FieldError[] = [];
  for (const fieldError of fieldErrors) {
    const control = form.get(fieldName(fieldError.field));
    if (control) {
      control.setErrors({ ...control.errors, server: fieldErrorMessage(fieldError) });
      control.markAsTouched();
    } else {
      unmatched.push(fieldError);
    }
  }
  return unmatched;
}

/** Moves focus to the first invalid field so keyboard and screen reader users land on it. */
export function focusFirstInvalid(host: HTMLElement): void {
  host.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
}
