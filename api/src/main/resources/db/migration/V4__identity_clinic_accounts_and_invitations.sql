-- MEDK-16 (epic MEDK-12): one account per clinic (ADR-012), staff invitations (ADR-014) and
-- idempotent creations. V3 belongs to MEDK-15 (refresh tokens, login throttle); the shared
-- one-time token table is created here only if V3 did not create it, with the same definition.

-- Clinic accounts: staff, patients and guardians. The email is unique only inside the clinic.
create table identity_user_account (
    id                         uuid         primary key,
    tenant_id                  uuid         not null references tenant (id),
    email_normalized           varchar(320) not null,
    password_hash              varchar(255), -- null while the account is invited
    status                     varchar(30)  not null,
    kind                       varchar(20)  not null,
    first_names                varchar(120) not null,
    last_names                 varchar(120) not null,
    job_title                  varchar(120),
    professional_registration  varchar(60),
    patient_ref                uuid,         -- patient of the patients module, without foreign key (principle VI)
    created_at                 timestamptz  not null default now(),
    created_by                 uuid,
    updated_at                 timestamptz  not null default now(),
    updated_by                 uuid,
    version                    bigint       not null default 0,
    constraint identity_user_account_tenant_id_unique unique (tenant_id, id),
    constraint identity_user_account_email_unique unique (tenant_id, email_normalized),
    constraint identity_user_account_email_lowercase check (email_normalized = lower(email_normalized)),
    constraint identity_user_account_status_check check (status in ('INVITED', 'PENDING_EMAIL_VERIFICATION', 'ACTIVE', 'DISABLED')),
    constraint identity_user_account_kind_check check (kind in ('STAFF', 'PATIENT', 'GUARDIAN')),
    constraint identity_user_account_password_check check (password_hash is not null or status in ('INVITED', 'DISABLED'))
);

-- Login across clinics looks the email up in every clinic (ADR-012).
create index identity_user_account_email_idx on identity_user_account (email_normalized);

-- Existing clinic staff (one membership each, ADR-010) keep their id, so audit history and role
-- assignments stay linked. The display name is split at the first space.
insert into identity_user_account (
    id, tenant_id, email_normalized, password_hash, status, kind, first_names, last_names, created_at, updated_at)
select a.id,
       m.tenant_id,
       a.email,
       a.password_hash,
       case when a.status = 'ACTIVE' and m.status = 'ACTIVE' then 'ACTIVE' else 'DISABLED' end,
       'STAFF',
       left(split_part(btrim(a.display_name), ' ', 1), 120),
       left(btrim(substr(btrim(a.display_name), length(split_part(btrim(a.display_name), ' ', 1)) + 1)), 120),
       a.created_at,
       a.updated_at
from user_account a
join tenant_membership m on m.user_account_id = a.id
where not a.platform_admin;

alter table identity_role_assignment drop constraint identity_role_assignment_user_fk;
alter table identity_role_assignment add constraint identity_role_assignment_user_fk
    foreign key (tenant_id, user_id) references identity_user_account (tenant_id, id) on delete cascade;

-- The actor of an audit event is a clinic account or the platform account: no foreign key.
alter table audit_event drop constraint audit_event_actor_account_id_fkey;

drop table tenant_membership;

-- user_account keeps only the platform (SUPER_ADMIN) accounts.
delete from user_account where not platform_admin;
alter table user_account add constraint user_account_platform_only check (platform_admin);

-- One-time tokens (ADR-014), shared with MEDK-15 (V3). Same definition as V3.
create table if not exists identity_one_time_token (
    id          uuid        primary key,
    tenant_id   uuid        not null references tenant (id),
    user_id     uuid        not null,
    purpose     varchar(30) not null,
    token_hash  varchar(64) not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_at  timestamptz not null default now(),
    constraint identity_one_time_token_hash_unique unique (token_hash),
    constraint identity_one_time_token_purpose_check check (purpose in (
        'INVITATION', 'PASSWORD_RESET', 'EMAIL_VERIFICATION', 'PRE_AUTH'))
);

create index if not exists identity_one_time_token_user_idx on identity_one_time_token (tenant_id, user_id, purpose);

-- Idempotency-Key of creation requests (plan of MEDK-12, rule 7), per clinic and operation.
create table idempotency_record (
    id               uuid        primary key,
    tenant_id        uuid        not null references tenant (id),
    operation        varchar(60) not null,
    idempotency_key  varchar(64) not null,
    request_hash     varchar(64) not null,
    resource_id      uuid        not null,
    created_at       timestamptz not null default now(),
    constraint idempotency_record_key_unique unique (tenant_id, operation, idempotency_key)
);

-- Every audited action so far, including those of MEDK-15 (V3), whatever the order they land in.
alter table audit_event drop constraint audit_event_action_check;
alter table audit_event add constraint audit_event_action_check check (action in (
    'TENANT_SIGNED_UP', 'LOGIN_SUCCEEDED', 'PASSWORD_CHANGED',
    'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED',
    'ROLES_SEEDED', 'ROLE_ASSIGNMENTS_REPLACED',
    'LOGIN_FAILED', 'LOGIN_LOCKED', 'LOGOUT', 'BRANCH_SWITCHED',
    'REFRESH_TOKEN_REUSED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'STAFF_USER_CREATED', 'STAFF_USER_UPDATED', 'STAFF_USER_DISABLED', 'STAFF_USER_ENABLED',
    'INVITATION_SENT', 'INVITATION_ACCEPTED', 'ACCOUNT_PROVISIONED'));
