-- MEDK-15 (epic MEDK-12): refresh tokens, one-time tokens and login throttling (ADR-012 to ADR-014).
-- Tokens are never stored in clear text, only as SHA-256 hashes (hex). A token row is found by
-- the hash of a secret only its holder knows; the clinic of the row is then bound as the tenant.

-- One-time tokens: password reset (MEDK-15), invitations (MEDK-16), email verification (MEDK-17)
-- and the pre-auth tokens of the login (ADR-012). A pre-auth token lists the clinics where the
-- password matched: one row per clinic, grouped by batch_id; only the first row carries the hash
-- of the token itself (the others hash it with their clinic), and using it marks the batch used.
create table identity_one_time_token (
    id          uuid        primary key,
    tenant_id   uuid        not null references tenant (id),
    user_id     uuid        not null,
    purpose     varchar(30) not null,
    token_hash  varchar(64) not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    batch_id    uuid,
    created_at  timestamptz not null default now(),
    constraint identity_one_time_token_hash_unique unique (token_hash),
    constraint identity_one_time_token_purpose_check check (purpose in (
        'INVITATION', 'PASSWORD_RESET', 'EMAIL_VERIFICATION', 'PRE_AUTH'))
);

create index identity_one_time_token_user_idx on identity_one_time_token (tenant_id, user_id, purpose);
create index identity_one_time_token_batch_idx on identity_one_time_token (batch_id) where batch_id is not null;

-- Rotating refresh tokens. tenant_id is null only for the platform (SUPER_ADMIN) account.
-- family_id groups the rotations of one session: reusing a revoked token revokes the family.
create table identity_refresh_token (
    id          uuid        primary key,
    tenant_id   uuid        references tenant (id),
    user_id     uuid        not null,
    branch_id   uuid,
    family_id   uuid        not null,
    token_hash  varchar(64) not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    created_at  timestamptz not null default now(),
    constraint identity_refresh_token_hash_unique unique (token_hash)
);

create index identity_refresh_token_family_idx on identity_refresh_token (family_id);
create index identity_refresh_token_user_idx on identity_refresh_token (user_id);

-- Failed login attempts per key: hash of (email + IP) or of the email (every account of it).
-- Holds no personal data in clear text and no tenant: it is checked before any clinic is known.
create table identity_login_throttle (
    key_hash      varchar(64) primary key,
    failures      integer     not null,
    window_start  timestamptz not null,
    locked_until  timestamptz
);

alter table audit_event drop constraint audit_event_action_check;
alter table audit_event add constraint audit_event_action_check check (action in (
    'TENANT_SIGNED_UP', 'LOGIN_SUCCEEDED', 'PASSWORD_CHANGED',
    'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED',
    'ROLES_SEEDED', 'ROLE_ASSIGNMENTS_REPLACED',
    'LOGIN_FAILED', 'LOGIN_LOCKED', 'LOGOUT', 'BRANCH_SWITCHED',
    'REFRESH_TOKEN_REUSED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED'));
