/** Client routes the auth and session layer redirects to. */
export const AuthRoutes = {
  login: '/login',
  loginClinic: '/login/clinic',
  loginBranch: '/login/branch',
  forgotPassword: '/forgot-password',
  /** Target of the password reset email: /reset-password#t=<token> (ADR-014). */
  resetPassword: '/reset-password',
  /** Target of the invitation email: /invitation#t=<token> (ADR-014). */
  invitation: '/invitation',
  /** Target of the self-registration verification email: /verify-email#t=<token> (ADR-014). */
  verifyEmail: '/verify-email',
  changePassword: '/change-password',
  forbidden: '/forbidden',
} as const;

export const RETURN_URL_PARAM = 'returnUrl';

/** Public self-registration page of a clinic, shared by the clinic with its public code. */
export function selfRegistrationUrl(publicCode: string): string {
  return `/clinics/${encodeURIComponent(publicCode)}/register`;
}
