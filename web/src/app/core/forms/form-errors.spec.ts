import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import {
  applyServerErrors,
  controlErrorMessage,
  fieldName,
  matchingFields,
  notBlank,
} from './form-errors';

describe('form errors', () => {
  it('translates client validation errors to Spanish', () => {
    const control = new FormControl('', [Validators.required, notBlank]);
    expect(controlErrorMessage(control)).toBe('Este campo es obligatorio.');
    control.setValue('   ');
    expect(controlErrorMessage(control)).toBe('Este campo no puede quedar solo con espacios.');
    control.setValue('ok');
    expect(controlErrorMessage(control)).toBeNull();
    expect(controlErrorMessage(new FormControl('x', Validators.minLength(12)))).toBe(
      'Debe tener al menos 12 caracteres.',
    );
  });

  it('flags a confirmation that does not match and clears it once it does', () => {
    const form = new FormGroup(
      { password: new FormControl('a-long-password'), confirm: new FormControl('other') },
      { validators: matchingFields('password', 'confirm') },
    );
    expect(controlErrorMessage(form.controls.confirm)).toBe('Las contraseñas no coinciden.');
    form.controls.confirm.setValue('a-long-password');
    expect(form.controls.confirm.errors).toBeNull();
  });

  it('puts VALIDATION field errors on the matching controls', () => {
    const form = new FormGroup({ name: new FormControl('x'), permissions: new FormControl([]) });
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        code: 'VALIDATION',
        errors: [
          { field: 'permissions[0]', code: 'UNKNOWN_PERMISSION' },
          { field: 'other', code: 'REQUIRED' },
        ],
      },
    });

    const unmatched = applyServerErrors(form, error);

    expect(controlErrorMessage(form.controls.permissions)).toBe(
      'El permiso no existe en el catálogo del sistema.',
    );
    expect(unmatched).toEqual([{ field: 'other', code: 'REQUIRED' }]);
    expect(fieldName('a.b')).toBe('a');
  });
});
