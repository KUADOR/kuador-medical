-- MEDK-68 (epic MEDK-49): subscription lifecycle, reactivation, payment events and price change notices.

-- Order of periods that start on the same day (a clinic reactivated the day it was cancelled).
alter table subscription_period add column created_at timestamptz not null default clock_timestamp();

-- Every status change of a subscription (ADR-016). Tenant data: @TenantId (ADR-007).
create table subscription_transition (
    id                 uuid          primary key,
    tenant_id          uuid          not null references tenant (id),
    subscription_id    uuid          not null,
    from_status        varchar(20),
    to_status          varchar(20)   not null,
    cause              varchar(20)   not null,
    actor_id           uuid          references user_account (id),
    actor_name         varchar(160),
    reason             varchar(500),
    payment_reference  varchar(120),
    at                 timestamptz   not null,
    created_at         timestamptz   not null default clock_timestamp(),
    constraint subscription_transition_subscription_fk
        foreign key (subscription_id, tenant_id) references subscription (id, tenant_id),
    constraint subscription_transition_from_check check (from_status is null or from_status in
        ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    constraint subscription_transition_to_check check (to_status in
        ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    constraint subscription_transition_cause_check check (cause in
        ('MANUAL', 'PAYMENT_RECEIVED', 'PAYMENT_FAILED', 'SCHEDULER')),
    -- Plan D12: leaving CANCELLED is a manual reactivation with a reason.
    constraint subscription_transition_reactivation_check check (
        from_status is distinct from 'CANCELLED'
        or (cause = 'MANUAL' and actor_id is not null and reason is not null and length(btrim(reason)) > 0))
);

create index subscription_transition_subscription_idx on subscription_transition (subscription_id, at desc);

-- A payment is applied once even if its event is delivered twice.
create unique index subscription_transition_payment_idx
    on subscription_transition (tenant_id, payment_reference) where payment_reference is not null;

-- Initial status of the subscriptions opened before this migration (MEDK-66).
insert into subscription_transition (id, tenant_id, subscription_id, from_status, to_status, cause, actor_id, at)
select gen_random_uuid(), s.tenant_id, s.id, null, s.status, 'MANUAL', t.created_by, s.created_at
from subscription s join tenant t on t.id = s.tenant_id;

-- Notice to the OWNER of a new price of its plan (plan D1, FR-005): one per subscription and price version.
create table price_change_notice (
    id               uuid           primary key,
    tenant_id        uuid           not null references tenant (id),
    subscription_id  uuid           not null,
    plan_price_id    uuid           not null references plan_price (id),
    amount           numeric(12, 2) not null,
    currency         char(3)        not null,
    notified_at      timestamptz    not null,
    applies_from     timestamptz    not null,
    constraint price_change_notice_subscription_fk
        foreign key (subscription_id, tenant_id) references subscription (id, tenant_id),
    constraint price_change_notice_unique unique (subscription_id, plan_price_id),
    constraint price_change_notice_amount_check check (amount >= 0)
);

create index price_change_notice_subscription_idx on price_change_notice (subscription_id, applies_from);

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
    'SUBSCRIPTION_PAYMENT_FAILED', 'SUBSCRIPTION_PAYMENT_REJECTED', 'PRICE_CHANGE_NOTICE_SENT'));
