-- MEDK-69 (epic MEDK-49): plan changes, capabilities and read-only mode.

-- A plan change of a clinic (plan D2, D3): an upgrade applies at once with its proration; a
-- downgrade or a billing period change is scheduled for the renewal. Tenant data: @TenantId (ADR-007).
create table plan_change (
    id               uuid           primary key,
    tenant_id        uuid           not null references tenant (id),
    subscription_id  uuid           not null,
    kind             varchar(20)    not null,
    status           varchar(20)    not null,
    from_plan_id     uuid           not null references plan (id),
    to_plan_id       uuid           not null references plan (id),
    from_billing_period varchar(20) not null,
    billing_period   varchar(20)    not null,
    requested_by     uuid           references user_account (id),
    requested_at     timestamptz    not null,
    effective_at     timestamptz    not null,
    applied_at       timestamptz,
    cancelled_at     timestamptz,
    prorated_amount  numeric(12, 2),
    currency         char(3)        not null,
    created_at       timestamptz    not null default clock_timestamp(),
    constraint plan_change_subscription_fk
        foreign key (subscription_id, tenant_id) references subscription (id, tenant_id),
    constraint plan_change_kind_check check (kind in ('UPGRADE', 'DOWNGRADE', 'PERIOD_CHANGE')),
    constraint plan_change_status_check check (status in ('SCHEDULED', 'APPLIED', 'CANCELLED')),
    constraint plan_change_billing_period_check check (billing_period in ('MONTHLY', 'ANNUAL')),
    constraint plan_change_from_billing_period_check check (from_billing_period in ('MONTHLY', 'ANNUAL')),
    constraint plan_change_prorated_check check (prorated_amount is null or prorated_amount >= 0)
);

create index plan_change_subscription_idx on plan_change (subscription_id, requested_at desc);

-- At most one change waits for the renewal: a new request replaces it.
create unique index plan_change_scheduled_idx on plan_change (subscription_id) where status = 'SCHEDULED';

-- The clinic reads its own subscription (GET /tenant/subscription). OWNER and ADMIN hold every
-- clinic permission of the catalog (role seed "*"); clinics created before get it here.
insert into identity_role_permission (role_id, tenant_id, permission_code)
select r.id, r.tenant_id, 'subscription.read'
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
    'PLAN_CHANGE_APPLIED', 'PLAN_CHANGE_SCHEDULED', 'PLAN_CHANGE_CANCELLED'));
