/** Client routes the auth layer redirects to. The pages themselves arrive in MEDK-8. */
export const AuthRoutes = {
  login: '/login',
  changePassword: '/change-password',
  forbidden: '/forbidden',
} as const;

export const RETURN_URL_PARAM = 'returnUrl';
