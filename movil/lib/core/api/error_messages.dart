import 'api_exception.dart';

/// Spanish user-facing message for an error of the staff, roles and patients
/// screens. Mirrors the messages of the web (`error-messages.ts`) so both
/// clients say the same for the same `code` (SC-008).
String apiErrorMessage(Object error) {
  if (error is! ApiException) return _unexpected;
  return switch (error.code) {
    ApiException.unreachableCode =>
      'No pudimos conectarnos con el servidor. Revisa tu conexión e inténtalo de nuevo; no se guardó nada en el teléfono.',
    'VALIDATION' =>
      'Revisa los datos ingresados: hay campos incompletos o con un formato no válido.',
    'EMAIL_TAKEN' => 'Ya existe una cuenta con ese correo en la clínica.',
    'UNAUTHENTICATED' => 'Tu sesión terminó. Inicia sesión de nuevo.',
    'FORBIDDEN' => 'No tienes permiso para realizar esta acción.',
    'PASSWORD_CHANGE_REQUIRED' =>
      'Debes cambiar tu contraseña antes de continuar.',
    'NOT_FOUND' => 'No encontramos lo que buscas en esta clínica.',
    'ROLE_NAME_TAKEN' => 'Ya existe un rol con ese nombre en tu clínica.',
    'VERSION_CONFLICT' =>
      'Otra persona modificó estos datos mientras los editabas. Vuelve a abrirlos para ver los cambios.',
    'SYSTEM_ROLE_IMMUTABLE' =>
      'Los roles OWNER y ADMIN están protegidos: no se pueden eliminar y OWNER no se puede editar.',
    'ROLE_IN_USE' =>
      'No se puede eliminar el rol porque está asignado a personas de tu clínica.',
    'TOO_MANY_ATTEMPTS' =>
      'Hiciste demasiados intentos. Inténtalo de nuevo en unos minutos.',
    'BRANCH_NOT_ASSIGNED' => 'No tienes una asignación en esa sucursal.',
    'PRIVILEGE_ESCALATION' =>
      'No puedes otorgar permisos o roles que tú no tienes.',
    'SELF_MODIFICATION_FORBIDDEN' =>
      'No puedes cambiar tus propios datos, roles ni acceso.',
    'LAST_OWNER' =>
      'La clínica debe conservar al menos una persona con el rol OWNER.',
    'PLAN_LIMIT_REACHED' =>
      'Tu clínica alcanzó el límite de usuarios de su plan.',
    'IDEMPOTENCY_KEY_REUSED' || 'RESOURCE_ID_TAKEN' =>
      'Esta solicitud ya se envió con otros datos. Vuelve a abrir el formulario.',
    'ACCOUNT_ALREADY_ACTIVE' => 'La cuenta ya está activa.',
    'PATIENT_DUPLICATE' =>
      'Ya existe un paciente con ese tipo y número de documento en la clínica.',
    'MINOR_CANNOT_HAVE_ACCOUNT' =>
      'Un paciente menor de edad no puede tener una cuenta propia.',
    'ADULT_AUTHORIZATION_REQUIRED' =>
      'El paciente es mayor de edad: solo él puede autorizar a un contacto desde su cuenta.',
    'LAST_LEGAL_GUARDIAN' =>
      'Un paciente menor de edad debe conservar al menos un representante legal.',
    'CONTACT_CHANNEL_UNAVAILABLE' =>
      'El paciente no tiene registrado ese medio de contacto, o el envío por ese medio aún no está disponible.',
    'INTERNAL_ERROR' =>
      'El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.',
    _ => _unexpected,
  };
}

const String _unexpected =
    'Recibimos una respuesta inesperada del servidor. Inténtalo de nuevo.';

/// Spanish message for a field-level `code` of a `VALIDATION` problem.
String apiFieldErrorMessage(String code) => switch (code) {
  'REQUIRED' => 'Este dato es obligatorio.',
  'TOO_LONG' => 'Es demasiado largo.',
  'TOO_SHORT' => 'Es demasiado corto.',
  'INVALID_FORMAT' => 'El formato no es válido.',
  'UNKNOWN_PERMISSION' => 'El permiso no existe en el catálogo.',
  'UNKNOWN_BRANCH' => 'La sucursal no existe en la clínica.',
  'UNKNOWN_ROLE' => 'El rol no existe en la clínica.',
  'DUPLICATE' => 'Ya está registrado.',
  'MINOR_NOT_ALLOWED' => 'No se permite para un menor de edad.',
  _ => 'Revisa este dato.',
};

/// Field errors of [error] keyed by the API field path, with Spanish messages.
Map<String, String> apiFieldErrors(Object error) => error is ApiException
    ? {for (final f in error.fieldErrors) f.field: apiFieldErrorMessage(f.code)}
    : const {};
