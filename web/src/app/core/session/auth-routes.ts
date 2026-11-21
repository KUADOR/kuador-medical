/** Client routes the auth and session layer redirects to. */
export const AuthRoutes = {
  login: '/login',
  loginClinic: '/login/clinic',
  loginBranch: '/login/branch',
  forgotPassword: '/forgot-password',
  /** Target of the password reset email: /password-reset#t=<token> (ADR-014, same path as the api links). */
  resetPassword: '/password-reset',
  /** Target of the invitation email: /invitation#t=<token> (ADR-014). */
  invitation: '/invitation',
  /** Target of the self-registration verification email: /email-verification#t=<token> (ADR-014). */
  verifyEmail: '/email-verification',
  /** Target of the OWNER invitation email: /owner-invitation#t=<token> (ADR-014, MEDK-49). */
  ownerInvitation: '/owner-invitation',
  /** Mandatory TOTP enrollment of an OWNER session (ADR-019). */
  twoFactorEnroll: '/two-factor/enroll',
  /** TOTP verification after the login of an OWNER (ADR-019). */
  twoFactorVerify: '/two-factor/verify',
  changePassword: '/change-password',
  forbidden: '/forbidden',
} as const;

export const RETURN_URL_PARAM = 'returnUrl';

/** Public self-registration page of a clinic, shared by the clinic with its public code. */
export function selfRegistrationUrl(publicCode: string): string {
  return `/clinics/${encodeURIComponent(publicCode)}/register`;
}
