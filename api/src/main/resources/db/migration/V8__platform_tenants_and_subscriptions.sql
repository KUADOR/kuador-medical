-- MEDK-66 (epic MEDK-49): clinic onboarding by the SUPER_ADMIN and the initial subscription.

-- Onboarding data of a clinic (FR-007). Columns are nullable because clinics created by the
-- earlier self-signup have none of them; the platform only administers onboarded clinics.
alter table tenant
    add column legal_name                varchar(200),
    add column trade_name                varchar(200),
    add column ruc                       varchar(13),
    add column clinic_type               varchar(20),
    add column country_code              char(2),
    add column city                      varchar(120),
    add column address                   varchar(300),
    add column phone                     varchar(20),
    add column contact_email             varchar(254),
    add column msp_establishment_number  varchar(40),
    add column owner_full_name           varchar(160),
    add column owner_email               varchar(254),
    add column created_by                uuid references user_account (id),
    add column updated_at                timestamptz,
    add column purged_at                 timestamptz,
    add column version                   bigint not null default 0,
    add constraint tenant_ruc_format_check check (ruc is null or ruc ~ '^[0-9]{13}$'),
    add constraint tenant_clinic_type_check check (clinic_type is null or clinic_type in ('MEDICAL', 'DENTAL', 'MIXED'));

-- RUC unique among non-purged clinics (plan D8).
create unique index tenant_ruc_unique_idx on tenant (ruc) where purged_at is null and ruc is not null;

-- One subscription per clinic, whose status follows ADR-016. Tenant data: @TenantId (ADR-007).
create table subscription (
    id                       uuid           primary key,
    tenant_id                uuid           not null references tenant (id),
    plan_id                  uuid           not null references plan (id),
    billing_period           varchar(20)    not null,
    status                   varchar(20)    not null,
    price_amount_snapshot    numeric(12, 2),
    price_currency_snapshot  char(3),
    current_period_start     timestamptz,
    current_period_end       timestamptz,
    trial_ends_at            timestamptz,
    grace_ends_at            timestamptz,
    suspended_at             timestamptz,
    cancelled_at             timestamptz,
    retention_ends_at        timestamptz,
    reactivated_at           timestamptz,
    price_locked_until       timestamptz,
    created_at               timestamptz    not null default now(),
    updated_at               timestamptz    not null default now(),
    version                  bigint         not null default 0,
    constraint subscription_tenant_unique unique (tenant_id),
    constraint subscription_tenant_id_unique unique (id, tenant_id),
    constraint subscription_billing_period_check check (billing_period in ('MONTHLY', 'ANNUAL')),
    constraint subscription_status_check check (status in ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    constraint subscription_price_check check (price_amount_snapshot is null or price_amount_snapshot >= 0)
);

create index subscription_status_idx on subscription (status);

-- Contracted price of every period (ADR-015): price changes never alter it.
create table subscription_period (
    id               uuid           primary key,
    tenant_id        uuid           not null references tenant (id),
    subscription_id  uuid           not null,
    plan_id          uuid           not null references plan (id),
    period_start     timestamptz    not null,
    period_end       timestamptz    not null,
    amount           numeric(12, 2) not null,
    currency         char(3)        not null,
    constraint subscription_period_subscription_fk
        foreign key (subscription_id, tenant_id) references subscription (id, tenant_id),
    constraint subscription_period_range_check check (period_end > period_start),
    constraint subscription_period_amount_check check (amount >= 0)
);

create index subscription_period_subscription_idx on subscription_period (subscription_id, period_start desc);

alter table audit_event drop constraint audit_event_action_check;
alter table audit_event add constraint audit_event_action_check check (action in (
    'TENANT_SIGNED_UP', 'LOGIN_SUCCEEDED', 'PASSWORD_CHANGED',
    'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED',
    'ROLES_SEEDED', 'ROLE_ASSIGNMENTS_REPLACED',
    'LOGIN_FAILED', 'LOGIN_LOCKED', 'LOGOUT', 'BRANCH_SWITCHED',
    'REFRESH_TOKEN_REUSED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'STAFF_USER_CREATED', 'STAFF_USER_UPDATED', 'STAFF_USER_DISABLED', 'STAFF_USER_ENABLED',
    'INVITATION_SENT', 'INVITATION_ACCEPTED', 'ACCOUNT_PROVISIONED',
    'PATIENTS_LISTED', 'PATIENT_VIEWED', 'PATIENT_REGISTERED', 'PATIENT_UPDATED', 'PATIENT_SELF_REGISTERED',
    'PATIENT_CONSENT_RECORDED', 'GUARDIAN_LINK_CREATED', 'GUARDIAN_LINK_ENDED', 'PATIENT_ACCOUNT_INVITED',
    'PATIENT_REGISTRATION_SETTINGS_UPDATED', 'EMAIL_VERIFICATION_SENT', 'EMAIL_VERIFIED',
    'PATIENT_BECAME_ADULT', 'ADULTHOOD_INVITATION_PENDING', 'GUARDIAN_LINK_AUTHORIZED',
    'PLAN_CREATED', 'PLAN_UPDATED', 'PLAN_ARCHIVED', 'PLAN_PRICE_CREATED', 'PLATFORM_SETTINGS_UPDATED',
    'TENANT_ONBOARDED', 'TENANT_UPDATED'));
