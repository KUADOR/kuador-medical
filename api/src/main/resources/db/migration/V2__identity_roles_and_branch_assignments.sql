-- MEDK-14 (epic MEDK-12): clinic roles, permissions and user + branch + role assignments (ADR-013).
-- Every business table carries tenant_id and is filtered by Hibernate @TenantId (ADR-007).

-- Branches of a clinic. Owned by the clinics module; identity only reads them through
-- BranchDirectory and stores branch ids without a foreign key (plan of MEDK-12, principle VI).
create table clinics_branch (
    id          uuid primary key,
    tenant_id   uuid         not null references tenant (id),
    name        varchar(120) not null,
    created_at  timestamptz  not null default now(),
    constraint clinics_branch_tenant_id_unique unique (id, tenant_id)
);

create index clinics_branch_tenant_idx on clinics_branch (tenant_id);

-- Roles: the identity_ prefix marks the owning module.
alter table role rename to identity_role;
alter table identity_role rename constraint role_tenant_id_unique to identity_role_tenant_id_unique;
alter table identity_role rename constraint role_system_code_check to identity_role_system_code_check;
alter index role_pkey rename to identity_role_pkey;
alter index role_tenant_name_unique rename to identity_role_tenant_name_unique;
alter index role_tenant_system_code_unique rename to identity_role_tenant_system_code_unique;
alter table identity_role rename constraint role_tenant_id_fkey to identity_role_tenant_id_fkey;

-- clinical: users holding the role need a professional registration.
-- suggested_key: stable key of a role seeded from identity/role-seed.yaml, so seeding is idempotent.
alter table identity_role add column clinical boolean not null default false;
alter table identity_role add column suggested_key varchar(40);
create unique index identity_role_tenant_suggested_key_unique
    on identity_role (tenant_id, suggested_key) where suggested_key is not null;

alter table role_permission rename to identity_role_permission;
alter index role_permission_pkey rename to identity_role_permission_pkey;
alter table identity_role_permission rename constraint role_permission_role_fk to identity_role_permission_role_fk;

-- One row per user + branch, one role per branch. branch_id is null only for the clinic-wide
-- OWNER assignment. tenant_id is repeated so that a row never links records of two clinics.
create table identity_role_assignment (
    id          uuid        primary key,
    tenant_id   uuid        not null,
    user_id     uuid        not null,
    branch_id   uuid,
    role_id     uuid        not null,
    created_at  timestamptz not null default now(),
    constraint identity_role_assignment_unique unique nulls not distinct (tenant_id, user_id, branch_id),
    constraint identity_role_assignment_user_fk foreign key (tenant_id, user_id)
        references tenant_membership (tenant_id, user_account_id) on delete cascade,
    constraint identity_role_assignment_role_fk foreign key (role_id, tenant_id)
        references identity_role (id, tenant_id)
);

create index identity_role_assignment_role_idx on identity_role_assignment (role_id);
create index identity_role_assignment_branch_idx on identity_role_assignment (tenant_id, branch_id);

-- The only assignments before this migration were made at signup (OWNER and ADMIN for the
-- owner, without branches). They become the clinic-wide OWNER assignment.
insert into identity_role_assignment (id, tenant_id, user_id, branch_id, role_id)
select gen_random_uuid(), m.tenant_id, m.user_account_id, null, r.id
from membership_role mr
join tenant_membership m on m.id = mr.membership_id
join identity_role r on r.id = mr.role_id
where r.system_code = 'OWNER';

drop table membership_role;

-- OWNER and ADMIN hold every clinic permission of the catalog, including those added by MEDK-13.
insert into identity_role_permission (role_id, tenant_id, permission_code)
select r.id, r.tenant_id, p.code
from identity_role r
cross join (values
    ('roles.read'), ('roles.create'), ('roles.update'), ('roles.delete'),
    ('identity.users.read'), ('identity.users.manage'), ('identity.assignments.manage'),
    ('patients.read'), ('patients.register'), ('patients.edit'),
    ('patients.guardians.manage'), ('patients.selfRegistration.configure')) as p (code)
where r.system_code in ('OWNER', 'ADMIN')
on conflict do nothing;

alter table audit_event drop constraint audit_event_action_check;
alter table audit_event add constraint audit_event_action_check check (action in (
    'TENANT_SIGNED_UP', 'LOGIN_SUCCEEDED', 'PASSWORD_CHANGED',
    'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED',
    'ROLES_SEEDED', 'ROLE_ASSIGNMENTS_REPLACED'));
