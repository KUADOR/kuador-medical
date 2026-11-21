import { FormControl, Validators } from '@angular/forms';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import { totpCode } from '../../../core/forms/form-errors';

/** Control of the 6-digit TOTP code. */
export function createCodeControl(): FormControl<string> {
  return new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, totpCode],
  });
}

/** The code as the contract expects it: only its 6 digits. */
export function normalizedCode(control: FormControl<string>): string {
  return control.value.replace(/\s/g, '');
}

/**
 * Shows a rejected code on its field (TWO_FACTOR_CODE_INVALID or a malformed one) and returns
 * null; any other error returns its Spanish message for the page alert (e.g. 429 attempts).
 */
export function codeFailure(error: unknown, control: FormControl<string>): string | null {
  const code = toProblem(error)?.code;
  if (code === 'TWO_FACTOR_CODE_INVALID' || code === 'VALIDATION') {
    control.setValue('');
    control.setErrors({ server: ERROR_MESSAGES.TWO_FACTOR_CODE_INVALID });
    control.markAsTouched();
    return null;
  }
  return errorMessage(error);
}

/** Help shown on both pages when the authenticator app is lost (plan D7, ADR-019). */
export const LOST_DEVICE_HELP =
  '¿Perdiste el acceso a tu app de autenticación? Pide a Kuador que restablezca tu verificación en dos pasos: al volver a ingresar la configurarás de nuevo.';
