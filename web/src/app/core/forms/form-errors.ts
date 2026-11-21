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
  if (errors['futureDate']) {
    return 'La fecha no puede ser posterior a hoy.';
  }
  if (errors['phone']) {
    return 'Escribe el teléfono con el signo + y el código de país, sin espacios.';
  }
  if (errors['samePassword']) {
    return 'La contraseña nueva debe ser distinta de la actual.';
  }
  if (errors['integer']) {
    return 'Escribe un número entero, sin decimales.';
  }
  if (errors['min']) {
    return `Debe ser como mínimo ${errors['min'].min}.`;
  }
  if (errors['max']) {
    return `Debe ser como máximo ${errors['max'].max}.`;
  }
  if (errors['amount']) {
    return 'Escribe un monto con hasta dos decimales, por ejemplo 49.00.';
  }
  if (errors['planCode']) {
    return 'Usa de 2 a 40 letras mayúsculas, números o guion bajo, empezando con una letra.';
  }
  if (errors['pastDate']) {
    return 'La fecha no puede ser anterior a hoy.';
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

/** Rejects dates (yyyy-MM-dd) after today, in the browser time zone. */
export const notInFuture: ValidatorFn = (control): ValidationErrors | null => {
  const value: unknown = control.value;
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const today = new Date();
  const local = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  return value > local ? { futureDate: true } : null;
};

/** Whole number (the value may come as a string from a text or number input). */
export const integer: ValidatorFn = (control): ValidationErrors | null => {
  const value: unknown = control.value;
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return /^-?\d+$/.test(String(value).trim()) ? null : { integer: true };
};

/** Optional phone in E.164 format, as the contract requires (`+` and 7 to 15 digits). */
export const e164Phone: ValidatorFn = (control): ValidationErrors | null => {
  const value: unknown = control.value;
  return typeof value === 'string' &&
    value.trim() !== '' &&
    !/^\+[1-9][0-9]{6,14}$/.test(value.trim())
    ? { phone: true }
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
    // Nested paths (`guardian.person.documentNumber`) land on the nested control when the form
    // has it; otherwise on the top-level field.
    const control =
      form.get(fieldError.field.replace(/\[(\d+)\]/g, '.$1')) ??
      form.get(fieldName(fieldError.field));
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
