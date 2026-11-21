-- MEDK-70 (epic MEDK-49): data export of a clinic and its purge (ADR-018).

-- A data export of a clinic (FR-021): generated as one file (JSON, or a ZIP of CSV files) and kept
-- until expires_at; the cleanup job then drops its content. Tenant data: @TenantId (ADR-007).
create table tenant_export (
    id             uuid          primary key,
    tenant_id      uuid          not null references tenant (id),
    format         varchar(10)   not null,
    status         varchar(20)   not null,
    requested_by   uuid          not null,
    created_at     timestamptz   not null,
    completed_at   timestamptz,
    expires_at     timestamptz,
    content        bytea,
    content_type   varchar(100),
    size_bytes     bigint,
    constraint tenant_export_format_check check (format in ('JSON', 'CSV')),
    constraint tenant_export_status_check check (status in ('PENDING', 'READY', 'FAILED', 'EXPIRED')),
    constraint tenant_export_ready_check check (status <> 'READY' or (content is not null and expires_at is not null))
);

create index tenant_export_tenant_idx on tenant_export (tenant_id, created_at desc);
create index tenant_export_expiry_idx on tenant_export (expires_at) where status = 'READY';

-- Purge of a clinic in two steps (plan D6): requested, then confirmed by typing the RUC; a
-- reactivation cancels a pending request (plan D12). Tenant data: @TenantId (ADR-007).
create table tenant_purge_request (
    id             uuid          primary key,
    tenant_id      uuid          not null references tenant (id),
    status         varchar(20)   not null,
    reason         varchar(500)  not null,
    requested_by   uuid          not null references user_account (id),
    requested_at   timestamptz   not null,
    confirmed_by   uuid          references user_account (id),
    confirmed_at   timestamptz,
    cancelled_at   timestamptz,
    created_at     timestamptz   not null default clock_timestamp(),
    constraint tenant_purge_request_status_check check (status in ('REQUESTED', 'CONFIRMED', 'CANCELLED')),
    constraint tenant_purge_request_confirmed_check check (
        status <> 'CONFIRMED' or (confirmed_by is not null and confirmed_at is not null))
);

-- At most one pending request, and one confirmed purge, per clinic.
create unique index tenant_purge_request_pending_idx on tenant_purge_request (tenant_id) where status = 'REQUESTED';
create unique index tenant_purge_request_confirmed_idx on tenant_purge_request (tenant_id) where status = 'CONFIRMED';

-- The clinic exports its own data. OWNER and ADMIN hold every clinic permission of the catalog
-- (role seed "*"); clinics created before get it here.
insert into identity_role_permission (role_id, tenant_id, permission_code)
select r.id, r.tenant_id, 'tenant.data.export'
from identity_role r
where r.system_code in ('OWNER', 'ADMIN')
on conflict do nothing;

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
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_RESET', 'TWO_FACTOR_LOCKED',
    'SUBSCRIPTION_STATUS_CHANGED', 'SUBSCRIPTION_RENEWED', 'SUBSCRIPTION_PRICE_LOCK_UPDATED',
    'SUBSCRIPTION_PAYMENT_FAILED', 'SUBSCRIPTION_PAYMENT_REJECTED', 'PRICE_CHANGE_NOTICE_SENT',
    'PLAN_CHANGE_APPLIED', 'PLAN_CHANGE_SCHEDULED', 'PLAN_CHANGE_CANCELLED',
    'TENANT_EXPORT_REQUESTED', 'TENANT_EXPORT_DOWNLOADED',
    'TENANT_PURGE_REQUESTED', 'TENANT_PURGE_CONFIRMED', 'TENANT_PURGE_CANCELLED', 'TENANT_PURGED'));
