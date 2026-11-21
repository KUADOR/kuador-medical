// Test-only fixtures of the OWNER flows (MEDK-74), shaped by the platform contract types.
import type {
  AuthenticatedResult,
  ClinicSubscription,
  CurrentSession,
  OwnerInvitationPreview,
  TenantExport,
  TwoFactorEnrollment,
} from '../app/core/api/api-types';
import { ACCOUNT, LOGIN } from './fixtures';

export const OWNER_TOKEN = 'owner-invitation-token-0123456789';

export const OWNER_PREVIEW: OwnerInvitationPreview = {
  clinicName: 'Example Clinic',
  ownerName: 'Jane Doe',
  maskedEmail: 'j***@example.com',
  expiresAt: '2030-01-04T15:00:00Z',
  termsVersion: '2030-01',
  dataPolicyVersion: '2030-01',
};

/** Tokens of an OWNER session that still has to pass the second factor. */
export const OWNER_PENDING_LOGIN: AuthenticatedResult = {
  ...LOGIN,
  accessToken: 'owner-pending-token',
  refreshToken: 'owner-pending-refresh',
};

/** Tokens issued after the second factor (enrollment confirmation or verification). */
export const OWNER_VERIFIED_LOGIN: AuthenticatedResult = {
  ...LOGIN,
  accessToken: 'owner-verified-token',
  refreshToken: 'owner-verified-refresh',
};

export const ENROLLMENT: TwoFactorEnrollment = {
  secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
  otpauthUri:
    'otpauth://totp/Medical%20Kuador:j***%40example.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Medical%20Kuador&algorithm=SHA1&digits=6&period=30',
  issuer: 'Medical Kuador',
  digits: 6,
  period: 30,
};

/** OWNER with the clinic permissions of the platform epic (subscription and export). */
export const OWNER_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  roles: ['OWNER'],
  permissions: [
    'roles.read',
    'subscription.read',
    'tenant.data.export',
  ] as unknown as CurrentSession['permissions'],
};

export function twoFactorRequired(twoFactor: 'ENROLLMENT_REQUIRED' | 'VERIFICATION_REQUIRED') {
  return {
    body: {
      type: 'about:blank',
      title: 'Unauthorized',
      status: 401,
      code: 'TWO_FACTOR_REQUIRED',
      twoFactor,
    },
    init: { status: 401, statusText: 'Unauthorized' },
  };
}

export const ACTIVE_SUBSCRIPTION: ClinicSubscription = {
  status: 'ACTIVE',
  accessMode: 'FULL',
  plan: { id: '3f2e1d0c-9b8a-4c7d-8e6f-5a4b3c2d1e0f', code: 'STANDARD', name: 'Standard' },
  billingPeriod: 'MONTHLY',
  priceAmount: '49.00',
  currency: 'USD',
  currentPeriodEnd: '2030-02-01T05:00:00Z',
  trialEndsAt: null,
  graceEndsAt: null,
  retentionEndsAt: null,
  modules: ['AGENDA', 'PATIENTS', 'USERS'],
  limits: [{ limit: 'MAX_USERS', limitValue: 15, usage: 8 }],
  scheduledPlanChange: null,
  notices: [],
};

export const PAST_DUE_SUBSCRIPTION: ClinicSubscription = {
  ...ACTIVE_SUBSCRIPTION,
  status: 'PAST_DUE',
  graceEndsAt: '2030-02-08T17:00:00Z',
  notices: [{ kind: 'PAYMENT_OVERDUE', effectiveAt: '2030-02-08T17:00:00Z' }],
};

export const SUSPENDED_SUBSCRIPTION: ClinicSubscription = {
  ...ACTIVE_SUBSCRIPTION,
  status: 'SUSPENDED',
  accessMode: 'READ_ONLY',
  notices: [{ kind: 'SUSPENDED', effectiveAt: null }],
};

export const CANCELLED_SUBSCRIPTION: ClinicSubscription = {
  ...ACTIVE_SUBSCRIPTION,
  status: 'CANCELLED',
  accessMode: 'RETENTION',
  retentionEndsAt: '2030-06-30T17:00:00Z',
  notices: [{ kind: 'CANCELLED', effectiveAt: '2030-06-30T17:00:00Z' }],
};

export const PENDING_EXPORT: TenantExport = {
  id: '2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f',
  format: 'JSON',
  status: 'PENDING',
  createdAt: '2030-07-01T15:00:00Z',
  expiresAt: null,
  downloadUrl: null,
};

export const READY_EXPORT: TenantExport = {
  ...PENDING_EXPORT,
  status: 'READY',
  expiresAt: '2030-07-08T17:00:00Z',
  downloadUrl: 'https://files.example.test/exports/2c3d4e5f?signature=example',
};
