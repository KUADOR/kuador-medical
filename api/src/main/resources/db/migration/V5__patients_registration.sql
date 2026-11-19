-- MEDK-17 (epic MEDK-12): administrative patient registration, guardians of minors, personal
-- data consent (LOPDP) and optional self-registration. Owned by the patients module.
-- Every table carries tenant_id and is filtered by Hibernate @TenantId (ADR-007). References to
-- other modules (accounts of identity) are stored as uuid without foreign key (principle VI).

create table patients_patient (
    id               uuid         primary key,
    tenant_id        uuid         not null references tenant (id),
    document_type    varchar(30)  not null,
    document_number  varchar(30)  not null,
    first_names      varchar(120) not null,
    last_names       varchar(120) not null,
    birth_date       date         not null,
    sex              varchar(30)  not null,
    email            varchar(320),
    phone            varchar(20),
    address          varchar(250),
    adult_since      date,         -- set by the adulthood transition (MEDK-18)
    origin           varchar(10)  not null,
    account_ref      uuid,         -- account of the identity module, without foreign key
    created_at       timestamptz  not null default now(),
    created_by       uuid,
    updated_at       timestamptz  not null default now(),
    updated_by       uuid,
    version          bigint       not null default 0,
    constraint patients_patient_tenant_id_unique unique (tenant_id, id),
    constraint patients_patient_document_unique unique (tenant_id, document_type, document_number),
    constraint patients_patient_origin_check check (origin in ('STAFF', 'SELF'))
);

create index patients_patient_names_idx on patients_patient (tenant_id, lower(last_names), lower(first_names));

-- People who represent a patient (legal guardians, and after adulthood the people the patient authorizes).
create table patients_guardian (
    id               uuid         primary key,
    tenant_id        uuid         not null references tenant (id),
    document_type    varchar(30)  not null,
    document_number  varchar(30)  not null,
    first_names      varchar(120) not null,
    last_names       varchar(120) not null,
    email            varchar(320),
    phone            varchar(20),
    account_ref      uuid,
    created_at       timestamptz  not null default now(),
    created_by       uuid,
    version          bigint       not null default 0,
    constraint patients_guardian_tenant_id_unique unique (tenant_id, id),
    constraint patients_guardian_document_unique unique (tenant_id, document_type, document_number)
);

-- Links between a patient and a guardian, with validity. Ended links are kept as history.
create table patients_guardian_link (
    id             uuid        primary key,
    tenant_id      uuid        not null,
    patient_id     uuid        not null,
    guardian_id    uuid        not null,
    link_type      varchar(20) not null,
    relationship   varchar(60) not null,
    valid_from     timestamptz not null,
    valid_to       timestamptz,
    end_reason     varchar(20),
    authorized_by  uuid,
    created_by     uuid,
    constraint patients_guardian_link_patient_fk
        foreign key (tenant_id, patient_id) references patients_patient (tenant_id, id),
    constraint patients_guardian_link_guardian_fk
        foreign key (tenant_id, guardian_id) references patients_guardian (tenant_id, id),
    constraint patients_guardian_link_type_check
        check (link_type in ('LEGAL_GUARDIAN', 'EMERGENCY_CONTACT', 'DELEGATED_ACCESS')),
    constraint patients_guardian_link_end_reason_check check (end_reason in ('ADULTHOOD', 'MANUAL')),
    constraint patients_guardian_link_end_check check ((valid_to is null) = (end_reason is null))
);

create index patients_guardian_link_patient_idx on patients_guardian_link (tenant_id, patient_id);

-- Accepted versions of the personal data consent text; previous ones stay as history.
create table patients_consent (
    id                   uuid        primary key,
    tenant_id            uuid        not null,
    patient_id           uuid        not null,
    text_version         varchar(20) not null,
    granted_at           timestamptz not null,
    granted_by           varchar(10) not null,
    granted_by_user      uuid,        -- staff account that recorded it, null for self-registration
    granted_by_guardian  uuid,        -- guardian who accepted it for a minor
    channel              varchar(10) not null,
    revoked_at           timestamptz,
    constraint patients_consent_patient_fk
        foreign key (tenant_id, patient_id) references patients_patient (tenant_id, id),
    constraint patients_consent_granted_by_check check (granted_by in ('PATIENT', 'GUARDIAN')),
    constraint patients_consent_channel_check check (channel in ('WEB', 'MOBILE', 'SELF'))
);

create index patients_consent_patient_idx on patients_consent (tenant_id, patient_id, granted_at desc);

-- Self-registration settings of each clinic; disabled by default. The public code goes in the
-- self-registration link of the clinic and is unique across clinics.
create table patients_registration_settings (
    tenant_id                  uuid        primary key references tenant (id),
    self_registration_enabled  boolean     not null default false,
    public_code                varchar(60) not null,
    updated_at                 timestamptz not null default now(),
    updated_by                 uuid,
    version                    bigint      not null default 0,
    constraint patients_registration_settings_public_code_unique unique (public_code),
    constraint patients_registration_settings_public_code_check check (public_code ~ '^[a-z0-9-]{3,60}$')
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
    'PATIENT_REGISTRATION_SETTINGS_UPDATED', 'EMAIL_VERIFICATION_SENT', 'EMAIL_VERIFIED'));
