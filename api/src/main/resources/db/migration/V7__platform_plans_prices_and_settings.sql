-- MEDK-65 (epic MEDK-49): plans, versioned prices, platform settings and platform idempotency.
-- Platform data of Kuador: no tenant_id, reachable only with platform.* permissions (Constitution VII).

-- A plan is never deleted: it is archived (status ARCHIVED) and existing subscriptions keep it.
create table plan (
    id           uuid         primary key,
    code         varchar(40)  not null,
    name         varchar(80)  not null,
    description  varchar(500),
    is_trial     boolean      not null default false,
    status       varchar(20)  not null default 'ACTIVE',
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    version      bigint       not null default 0,
    constraint plan_code_unique unique (code),
    constraint plan_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

-- Only one trial plan (plan D9).
create unique index plan_single_trial_idx on plan ((true)) where is_trial;

-- Modules of the fixed catalog (ModuleCode) enabled by a plan.
create table plan_module (
    plan_id      uuid         not null references plan (id),
    module_code  varchar(40)  not null,
    primary key (plan_id, module_code)
);

-- Limits of the fixed catalog (LimitCode). A missing row or a null value means unlimited.
create table plan_limit (
    plan_id      uuid         not null references plan (id),
    limit_code   varchar(40)  not null,
    limit_value  integer,
    primary key (plan_id, limit_code),
    constraint plan_limit_value_check check (limit_value is null or limit_value >= 0)
);

-- Immutable, versioned price of a plan per billing period (ADR-015): amounts without VAT.
create table plan_price (
    id              uuid           primary key,
    plan_id         uuid           not null references plan (id),
    billing_period  varchar(20)    not null,
    currency        char(3)        not null default 'USD',
    amount          numeric(12, 2) not null,
    effective_from  timestamptz    not null,
    created_by      uuid           references user_account (id),
    created_at      timestamptz    not null default now(),
    constraint plan_price_billing_period_check check (billing_period in ('MONTHLY', 'ANNUAL')),
    constraint plan_price_amount_check check (amount >= 0)
);

create index plan_price_plan_idx on plan_price (plan_id, billing_period, effective_from desc);

-- Configurable periods of the subscription lifecycle (plan D10). The settings are versioned as a
-- whole: every row carries the same version, written together under a row lock.
create table platform_setting (
    key         varchar(60)  primary key,
    value       integer      not null,
    updated_by  uuid         references user_account (id),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0,
    constraint platform_setting_key_check check (key in (
        'TRIAL_DAYS', 'GRACE_DAYS', 'SUSPENSION_DAYS_BEFORE_CANCEL', 'RETENTION_DAYS',
        'OWNER_INVITATION_TTL_HOURS', 'PRICE_CHANGE_NOTICE_DAYS'))
);

insert into platform_setting (key, value) values
    ('TRIAL_DAYS', 14),
    ('GRACE_DAYS', 7),
    ('SUSPENSION_DAYS_BEFORE_CANCEL', 90),
    ('RETENTION_DAYS', 90),
    ('OWNER_INVITATION_TTL_HOURS', 72),
    ('PRICE_CHANGE_NOTICE_DAYS', 30);

-- Trial plan decided by Equipo Kuador (plan D9): 14 days, no prices, the modules and limits below.
insert into plan (id, code, name, description, is_trial) values
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'TRIAL', 'Prueba', 'Prueba gratuita sin tarjeta.', true);

insert into plan_module (plan_id, module_code) values
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'AGENDA'),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'PATIENTS'),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'CLINICAL_RECORDS'),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'CONSULTATION'),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'ODONTOGRAM'),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'USERS');

insert into plan_limit (plan_id, limit_code, limit_value) values
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'MAX_BRANCHES', 1),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'MAX_USERS', 3),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'MAX_PATIENTS', 50),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'MAX_APPOINTMENTS', 100),
    ('6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'MAX_STORAGE_MB', 500);

-- Idempotency-Key of platform creations: platform requests have no tenant, so keys are kept per
-- SUPER_ADMIN account and operation (same rules as idempotency_record).
create table platform_idempotency_record (
    id               uuid        primary key,
    actor_id         uuid        not null references user_account (id),
    operation        varchar(60) not null,
    idempotency_key  varchar(64) not null,
    request_hash     varchar(64) not null,
    resource_id      uuid        not null,
    created_at       timestamptz not null default now(),
    constraint platform_idempotency_record_key_unique unique (actor_id, operation, idempotency_key)
);

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
    'PLAN_CREATED', 'PLAN_UPDATED', 'PLAN_ARCHIVED', 'PLAN_PRICE_CREATED', 'PLATFORM_SETTINGS_UPDATED'));
