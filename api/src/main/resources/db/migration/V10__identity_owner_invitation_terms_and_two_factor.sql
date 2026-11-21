-- MEDK-67 (epic MEDK-49): OWNER invitation, terms acceptance and mandatory TOTP second factor.
-- Numbered V10 so that it does not collide with MEDK-68, developed in parallel.

-- One-time invitation of the initial OWNER (ADR-014, FR-008): only the SHA-256 of the token is
-- stored. A new invitation revokes the pending one; expiry comes from OWNER_INVITATION_TTL_HOURS.
create table identity_owner_invitation (
    id           uuid         primary key,
    tenant_id    uuid         not null references tenant (id),
    account_id   uuid         not null,
    token_hash   char(64)     not null,
    expires_at   timestamptz  not null,
    created_at   timestamptz  not null,
    created_by   uuid         references user_account (id),
    used_at      timestamptz,
    revoked_at   timestamptz,
    constraint identity_owner_invitation_hash_unique unique (token_hash),
    constraint identity_owner_invitation_account_fk
        foreign key (tenant_id, account_id) references identity_user_account (tenant_id, id),
    constraint identity_owner_invitation_expiry_check check (expires_at > created_at)
);

create index identity_owner_invitation_tenant_idx on identity_owner_invitation (tenant_id, created_at desc);

-- Acceptance of the terms of service and the data policy, with their versions (FR-008).
create table identity_terms_acceptance (
    id                   uuid         primary key,
    tenant_id            uuid         not null references tenant (id),
    user_id              uuid         not null,
    terms_version        varchar(40)  not null,
    data_policy_version  varchar(40)  not null,
    accepted_at          timestamptz  not null,
    constraint identity_terms_acceptance_account_fk
        foreign key (tenant_id, user_id) references identity_user_account (tenant_id, id)
);

create index identity_terms_acceptance_user_idx on identity_terms_acceptance (tenant_id, user_id);

-- TOTP second factor (ADR-019): one row per enrollment, the secret encrypted with AES-GCM. A row
-- is pending until enabled_at, and stops counting once reset_at is set (reset by SUPER_ADMIN, D7).
-- last_used_step rejects a code already used; failed_attempts and locked_until limit guessing.
create table identity_two_factor (
    id                uuid          primary key,
    tenant_id         uuid          not null references tenant (id),
    user_id           uuid          not null,
    secret_encrypted  varchar(200)  not null,
    created_at        timestamptz   not null,
    enabled_at        timestamptz,
    last_used_step    bigint,
    failed_attempts   integer       not null default 0,
    locked_until      timestamptz,
    reset_at          timestamptz,
    reset_by          uuid          references user_account (id),
    reset_reason      varchar(500),
    constraint identity_two_factor_account_fk
        foreign key (tenant_id, user_id) references identity_user_account (tenant_id, id),
    constraint identity_two_factor_reset_check check (reset_at is null or (reset_by is not null and length(trim(reset_reason)) > 0))
);

-- At most one current (not reset) factor per account.
create unique index identity_two_factor_current_idx on identity_two_factor (tenant_id, user_id) where reset_at is null;

-- The verified factor of a session survives a refresh (ADR-013, ADR-019).
alter table identity_refresh_token add column two_factor_id uuid;

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
    'TENANT_ONBOARDED', 'TENANT_UPDATED',
    'OWNER_INVITATION_SENT', 'OWNER_INVITATION_ACCEPTED', 'TERMS_ACCEPTED',
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_RESET', 'TWO_FACTOR_LOCKED'));
