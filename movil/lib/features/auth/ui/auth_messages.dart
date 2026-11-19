import '../../../core/api/api_exception.dart';

/// Spanish user-facing message for an [ApiException] of the auth flows.
///
/// Login and recovery errors are generic on purpose (FR-006): they never say
/// whether the email exists.
String authErrorMessage(Object error) {
  if (error is! ApiException) return _unexpected;
  return switch (error.code) {
    ApiException.unreachableCode =>
      'No pudimos conectarnos con el servidor. Revisa tu conexión e inténtalo de nuevo.',
    'INVALID_CREDENTIALS' => 'El correo o la contraseña no son correctos.',
    'TOO_MANY_ATTEMPTS' => _tooManyAttempts(error.retryAfter),
    'INVALID_SESSION_TOKEN' =>
      'El inicio de sesión expiró. Vuelve a ingresar tu correo y contraseña.',
    'BRANCH_NOT_ASSIGNED' => 'No tienes una asignación en esa sucursal.',
    'ONE_TIME_TOKEN_INVALID' =>
      'El enlace expiró o ya se usó. Solicita uno nuevo.',
    'WEAK_PASSWORD' =>
      'La contraseña no cumple la política. Usa una más larga y distinta de la actual.',
    'VALIDATION' => 'Revisa los datos marcados e inténtalo de nuevo.',
    'NOT_FOUND' =>
      'No encontramos esa clínica o no tiene habilitado el autorregistro.',
    'IDEMPOTENCY_KEY_REUSED' || 'RESOURCE_ID_TAKEN' =>
      'Este registro ya se envió con otros datos. Vuelve a abrir el formulario.',
    'UNAUTHENTICATED' => 'Tu sesión terminó. Inicia sesión de nuevo.',
    'FORBIDDEN' => 'No tienes permiso para esta acción.',
    'INTERNAL_ERROR' =>
      'El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.',
    _ => _unexpected,
  };
}

const String _unexpected =
    'Recibimos una respuesta inesperada del servidor. Inténtalo de nuevo.';

String _tooManyAttempts(Duration? retryAfter) {
  const base = 'Demasiados intentos. El acceso quedó bloqueado temporalmente';
  if (retryAfter == null) return '$base; inténtalo más tarde.';
  final minutes = (retryAfter.inSeconds / 60).ceil();
  return minutes <= 1
      ? '$base; inténtalo de nuevo en un minuto.'
      : '$base; inténtalo de nuevo en $minutes minutos.';
}

/// Spanish message for a field-level `code` of a `VALIDATION` problem.
String fieldErrorMessage(String code) => switch (code) {
  'REQUIRED' => 'Este dato es obligatorio.',
  'TOO_LONG' => 'Es demasiado largo.',
  'INVALID_FORMAT' => 'El formato no es válido.',
  'MINOR_NOT_ALLOWED' =>
    'Solo los adultos pueden registrarse solos. Un menor se registra en la clínica con su representante.',
  'DUPLICATE' => 'Ya está registrado.',
  _ => 'Revisa este dato.',
};
