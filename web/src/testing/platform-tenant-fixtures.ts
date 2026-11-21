// Test-only fixtures of the SUPER_ADMIN clinics panel (MEDK-73), shaped by the platform contract.
// Fictitious data only.
import type {
  CurrentSession,
  PlanChangePreview,
  PlatformPlan,
  Subscription,
  Tenant,
  TenantPage,
} from '../app/core/api/api-types';
import { SUPER_ADMIN } from './fixtures';

/** SUPER_ADMIN with the platform permissions of the clinics panel. */
export const PLATFORM_ADMIN: CurrentSession = {
  ...SUPER_ADMIN,
  permissions: [
    'platform.permissions.read',
    'platform.plans.manage',
    'platform.tenants.manage',
    'platform.tenants.purge',
  ],
};

/** Platform account that manages clinics but cannot purge them nor read plans. */
export const TENANTS_ONLY_ADMIN: CurrentSession = {
  ...SUPER_ADMIN,
  permissions: ['platform.tenants.manage'],
};

export const TENANT_ID = '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60';
export const STANDARD_PLAN_ID = '3f2e1d0c-9b8a-4c7d-8e6f-5a4b3c2d1e0f';
export const PREMIUM_PLAN_ID = '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d';
export const TRIAL_PLAN_ID = '6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d';

const LIMITS = {
  MAX_BRANCHES: 3,
  MAX_USERS: 15,
  MAX_PATIENTS: 5000,
  MAX_APPOINTMENTS: null,
  MAX_STORAGE_MB: 10240,
};

export const TRIAL_PLAN: PlatformPlan = {
  id: TRIAL_PLAN_ID,
  code: 'TRIAL',
  name: 'Prueba',
  description: null,
  trial: true,
  status: 'ACTIVE',
  modules: ['AGENDA', 'PATIENTS'],
  limits: { ...LIMITS, MAX_BRANCHES: 1, MAX_USERS: 3 },
  currentPrices: [],
  createdAt: '2029-12-20T15:00:00Z',
  updatedAt: '2029-12-20T15:00:00Z',
  version: 0,
};

export const STANDARD_PLAN: PlatformPlan = {
  id: STANDARD_PLAN_ID,
  code: 'STANDARD',
  name: 'Standard',
  description: 'Example plan.',
  trial: false,
  status: 'ACTIVE',
  modules: ['AGENDA', 'PATIENTS', 'PHARMACY'],
  limits: LIMITS,
  currentPrices: [
    {
      id: '1b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e',
      billingPeriod: 'MONTHLY',
      currency: 'USD',
      amount: '49.00',
      effectiveFrom: '2030-01-01T05:00:00Z',
      createdAt: '2029-12-20T15:00:00Z',
    },
    {
      id: '2b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e',
      billingPeriod: 'ANNUAL',
      currency: 'USD',
      amount: '490.00',
      effectiveFrom: '2030-01-01T05:00:00Z',
      createdAt: '2029-12-20T15:00:00Z',
    },
  ],
  createdAt: '2029-12-20T15:00:00Z',
  updatedAt: '2030-01-10T15:00:00Z',
  version: 3,
};

export const PREMIUM_PLAN: PlatformPlan = {
  ...STANDARD_PLAN,
  id: PREMIUM_PLAN_ID,
  code: 'PREMIUM',
  name: 'Premium',
  currentPrices: [{ ...STANDARD_PLAN.currentPrices[0], id: 'p-premium', amount: '99.00' }],
};

export const PLANS: PlatformPlan[] = [TRIAL_PLAN, STANDARD_PLAN, PREMIUM_PLAN];

export const TENANT: Tenant = {
  id: TENANT_ID,
  legalName: 'Example Clinic S.A.',
  tradeName: 'Example Clinic',
  ruc: '0000000000001',
  clinicType: 'MEDICAL',
  countryCode: 'EC',
  city: 'Quito',
  address: '123 Example Avenue',
  phone: '+593000000000',
  contactEmail: 'contact@example.com',
  mspEstablishmentNumber: null,
  owner: {
    fullName: 'Jane Doe',
    maskedEmail: 'j***@example.com',
    twoFactorEnabled: false,
    invitation: {
      status: 'PENDING',
      maskedEmail: 'j***@example.com',
      sentAt: '2030-01-01T15:00:00Z',
      expiresAt: '2030-01-04T15:00:00Z',
    },
  },
  subscription: {
    status: 'ACTIVE',
    planId: STANDARD_PLAN_ID,
    planName: 'Standard',
    billingPeriod: 'MONTHLY',
    currentPeriodEnd: '2030-02-01T05:00:00Z',
    trialEndsAt: null,
  },
  createdAt: '2030-01-01T15:00:00Z',
  version: 2,
};

/** Tenant whose OWNER already accepted and enrolled the second factor. */
export const ACTIVE_OWNER_TENANT: Tenant = {
  ...TENANT,
  owner: {
    ...TENANT.owner,
    twoFactorEnabled: true,
    invitation: { ...TENANT.owner.invitation!, status: 'ACCEPTED' },
  },
};

export const ACTIVE_SUBSCRIPTION: Subscription = {
  id: '8f7e6d5c-4b3a-4291-8f0e-7d6c5b4a3928',
  status: 'ACTIVE',
  accessMode: 'FULL',
  plan: { id: STANDARD_PLAN_ID, code: 'STANDARD', name: 'Standard' },
  billingPeriod: 'MONTHLY',
  priceAmount: '49.00',
  currency: 'USD',
  currentPeriodStart: '2030-01-01T05:00:00Z',
  currentPeriodEnd: '2030-02-01T05:00:00Z',
  trialEndsAt: null,
  graceEndsAt: null,
  suspendedAt: null,
  cancelledAt: null,
  retentionEndsAt: null,
  reactivatedAt: null,
  priceLockedUntil: null,
  purged: false,
  reactivable: false,
  allowedTargets: ['SUSPENDED', 'CANCELLED'],
  scheduledPlanChange: null,
  periods: [
    {
      planName: 'Standard',
      periodStart: '2030-01-01T05:00:00Z',
      periodEnd: '2030-02-01T05:00:00Z',
      amount: '49.00',
      currency: 'USD',
    },
  ],
  transitions: [
    {
      from: 'TRIAL',
      to: 'ACTIVE',
      cause: 'PAYMENT_RECEIVED',
      actor: null,
      reason: null,
      at: '2030-01-01T05:00:00Z',
    },
  ],
  version: 4,
};

export const SUSPENDED_SUBSCRIPTION: Subscription = {
  ...ACTIVE_SUBSCRIPTION,
  status: 'SUSPENDED',
  accessMode: 'READ_ONLY',
  suspendedAt: '2030-02-08T05:00:00Z',
  allowedTargets: ['ACTIVE', 'CANCELLED'],
  version: 5,
};

/** Cancelled inside the retention (ends in 2999): reactivable by the SUPER_ADMIN. */
export const CANCELLED_REACTIVABLE: Subscription = {
  ...ACTIVE_SUBSCRIPTION,
  status: 'CANCELLED',
  accessMode: 'RETENTION',
  suspendedAt: '2030-02-08T05:00:00Z',
  cancelledAt: '2030-05-09T05:00:00Z',
  retentionEndsAt: '2999-08-07T05:00:00Z',
  reactivable: true,
  allowedTargets: ['ACTIVE'],
  version: 7,
};

/** Cancelled with the retention over: not reactivable any more, purge available. */
export const CANCELLED_RETENTION_OVER: Subscription = {
  ...CANCELLED_REACTIVABLE,
  retentionEndsAt: '2025-08-07T05:00:00Z',
  reactivable: false,
  // Even if the api listed it, the panel trusts `reactivable` (plan D12).
  allowedTargets: ['ACTIVE'],
};

export const TENANT_PAGE: TenantPage = {
  items: [
    {
      id: TENANT_ID,
      legalName: 'Example Clinic S.A.',
      tradeName: 'Example Clinic',
      ruc: '0000000000001',
      clinicType: 'MEDICAL',
      planName: 'Standard',
      subscriptionStatus: 'ACTIVE',
      createdAt: '2030-01-01T15:00:00Z',
    },
    {
      id: '6c1d9f3e-3a51-4d6e-9f0a-1c2b3d4e5f61',
      legalName: 'Sample Dental Cía. Ltda.',
      tradeName: null,
      ruc: '0000000000002',
      clinicType: 'DENTAL',
      planName: 'Prueba',
      subscriptionStatus: 'TRIAL',
      createdAt: '2030-01-05T15:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalItems: 2,
};

export const UPGRADE_PREVIEW: PlanChangePreview = {
  kind: 'UPGRADE',
  targetPlan: { id: PREMIUM_PLAN_ID, code: 'PREMIUM', name: 'Premium' },
  billingPeriod: 'MONTHLY',
  effectiveAt: '2030-01-16T15:00:00Z',
  proratedAmount: '25.00',
  currency: 'USD',
  newPriceAmount: '99.00',
  excess: [],
  disabledModules: [],
};

export const DOWNGRADE_PREVIEW: PlanChangePreview = {
  kind: 'DOWNGRADE',
  targetPlan: { id: STANDARD_PLAN_ID, code: 'BASIC', name: 'Basic' },
  billingPeriod: 'MONTHLY',
  effectiveAt: '2030-02-01T05:00:00Z',
  proratedAmount: null,
  currency: 'USD',
  newPriceAmount: '29.00',
  excess: [{ limit: 'MAX_USERS', limitValue: 5, usage: 8 }],
  disabledModules: ['PHARMACY'],
};
