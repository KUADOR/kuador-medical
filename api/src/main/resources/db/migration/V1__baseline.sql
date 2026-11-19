-- Baseline data model of the Medical Kuador skeleton (plan of MEDK-1).
-- Tenant-scoped tables carry tenant_id; Hibernate @TenantId filters them (ADR-007).
-- No personal data is stored in audit_event, only identifiers.

create table tenant (
    id          uuid primary key,
    name        varchar(200) not null,
    status      varchar(20)  not null default 'ACTIVE',
    created_at  timestamptz  not null default now(),
    constraint tenant_status_check check (status in ('ACTIVE', 'SUSPENDED'))
);

create table user_account (
    id                    uuid primary key,
    email                 varchar(320) not null,
    password_hash         varchar(255) not null,
    display_name          varchar(200) not null,
    platform_admin        boolean      not null default false,
    must_change_password  boolean      not null default false,
    status                varchar(20)  not null default 'ACTIVE',
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint user_account_email_unique unique (email),
    constraint user_account_email_lowercase check (email = lower(email)),
    constraint user_account_status_check check (status in ('ACTIVE', 'DISABLED'))
);

create table tenant_membership (
    id               uuid primary key,
    tenant_id        uuid        not null references tenant (id),
    user_account_id  uuid        not null references user_account (id),
    status           varchar(20) not null default 'ACTIVE',
    created_at       timestamptz not null default now(),
    constraint tenant_membership_unique unique (tenant_id, user_account_id),
    constraint tenant_membership_tenant_id_unique unique (id, tenant_id),
    constraint tenant_membership_status_check check (status in ('ACTIVE', 'DISABLED'))
);

create table role (
    id           uuid primary key,
    tenant_id    uuid         not null references tenant (id),
    name         varchar(100) not null,
    description  varchar(500),
    system_code  varchar(20),
    version      bigint       not null default 0,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint role_tenant_id_unique unique (id, tenant_id),
    constraint role_system_code_check check (system_code in ('OWNER', 'ADMIN'))
);

create unique index role_tenant_name_unique on role (tenant_id, lower(name));
create unique index role_tenant_system_code_unique on role (tenant_id, system_code) where system_code is not null;

-- tenant_id is repeated on the join tables so that a row can never link records of two tenants.
create table role_permission (
    role_id          uuid         not null,
    tenant_id        uuid         not null,
    permission_code  varchar(100) not null,
    primary key (role_id, permission_code),
    constraint role_permission_role_fk foreign key (role_id, tenant_id)
        references role (id, tenant_id) on delete cascade
);

create table membership_role (
    membership_id  uuid not null,
    role_id        uuid not null,
    tenant_id      uuid not null,
    primary key (membership_id, role_id),
    constraint membership_role_membership_fk foreign key (membership_id, tenant_id)
        references tenant_membership (id, tenant_id) on delete cascade,
    constraint membership_role_role_fk foreign key (role_id, tenant_id)
        references role (id, tenant_id)
);

create index membership_role_role_idx on membership_role (role_id);

create table audit_event (
    id                uuid primary key,
    tenant_id         uuid references tenant (id), -- null: platform-level event
    actor_account_id  uuid references user_account (id),
    action            varchar(50)  not null,
    target_type       varchar(50),
    target_id         uuid,
    occurred_at       timestamptz  not null default now(),
    constraint audit_event_action_check check (action in (
        'TENANT_SIGNED_UP', 'LOGIN_SUCCEEDED', 'PASSWORD_CHANGED',
        'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED'))
);

create index audit_event_tenant_occurred_idx on audit_event (tenant_id, occurred_at desc);
