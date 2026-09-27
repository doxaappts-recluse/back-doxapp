-- V2 · Identidad y acceso (M05, spec 00 §5/§6).
-- platform_staff NO es Person y no pertenece a una organización (D2).

-- Integridad de tenant: un acceso solo puede unir personas/sedes de la MISMA organización.
ALTER TABLE person ADD CONSTRAINT ux_person_id_org UNIQUE (id, organization_id);
ALTER TABLE branch ADD CONSTRAINT ux_branch_id_org UNIQUE (id, organization_id);

CREATE TABLE platform_staff (
    id             UUID PRIMARY KEY,
    first_name     VARCHAR(80)  NOT NULL,
    last_name      VARCHAR(80)  NOT NULL,
    doc_type       VARCHAR(10)  NOT NULL,
    doc_number     VARCHAR(12)  NOT NULL,
    email          VARCHAR(160) NOT NULL,
    phone          VARCHAR(20),
    position       VARCHAR(80),
    staff_role     VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'INVITED',
    status_reason  VARCHAR(255),
    hire_date      DATE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMPTZ,
    updated_by     UUID,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_doc_type CHECK (doc_type IN ('DNI','CE','PASSPORT')),
    CONSTRAINT ck_staff_role     CHECK (staff_role IN ('SYSTEM_ADMIN','SYSTEM_SUPPORT')),
    CONSTRAINT ck_staff_status   CHECK (status IN ('INVITED','ACTIVE','INACTIVE','LOCKED'))
);
CREATE UNIQUE INDEX ux_staff_doc   ON platform_staff (doc_type, doc_number);
CREATE UNIQUE INDEX ux_staff_email ON platform_staff (lower(email));

CREATE TABLE credential (
    id                    UUID PRIMARY KEY,
    actor_type            VARCHAR(10)  NOT NULL,
    staff_id              UUID REFERENCES platform_staff (id),
    person_id             UUID,
    organization_id       UUID REFERENCES organization (id),
    username              VARCHAR(160) NOT NULL,
    password_hash         VARCHAR(100),
    status                VARCHAR(20)  NOT NULL DEFAULT 'INVITED',
    failed_attempts       INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret_enc        VARCHAR(255),
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_cred_actor  CHECK (actor_type IN ('STAFF','PERSON')),
    CONSTRAINT ck_cred_status CHECK (status IN ('INVITED','ACTIVE','INACTIVE','LOCKED')),
    -- exactamente un titular: staff (sin organización) o persona (con organización)
    CONSTRAINT ck_cred_owner  CHECK (
        (actor_type = 'STAFF'  AND staff_id IS NOT NULL AND person_id IS NULL     AND organization_id IS NULL) OR
        (actor_type = 'PERSON' AND person_id IS NOT NULL AND staff_id IS NULL     AND organization_id IS NOT NULL)),
    CONSTRAINT fk_cred_person FOREIGN KEY (person_id, organization_id) REFERENCES person (id, organization_id)
);
CREATE UNIQUE INDEX ux_cred_staff_username  ON credential (lower(username)) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX ux_cred_person_username ON credential (organization_id, lower(username)) WHERE organization_id IS NOT NULL;
CREATE UNIQUE INDEX ux_cred_staff_id        ON credential (staff_id)  WHERE staff_id  IS NOT NULL;
CREATE UNIQUE INDEX ux_cred_person_id       ON credential (person_id) WHERE person_id IS NOT NULL;

CREATE TABLE user_access (
    id               UUID PRIMARY KEY,
    person_id        UUID        NOT NULL,
    organization_id  UUID        NOT NULL REFERENCES organization (id),
    branch_id        UUID,
    role             VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    status_reason    VARCHAR(255),
    valid_from       DATE        NOT NULL,
    valid_to         DATE,
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_ua_role   CHECK (role IN ('ORG_ADMIN','ORG_BRANCH_ADMIN','ORG_USER','MEMBER')),
    CONSTRAINT ck_ua_status CHECK (status IN ('INVITED','ACTIVE','INACTIVE','LOCKED')),
    CONSTRAINT ck_ua_dates  CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- [V10] sede obligatoria salvo ORG_ADMIN, que va sin sede
    CONSTRAINT ck_ua_branch CHECK ((role = 'ORG_ADMIN' AND branch_id IS NULL) OR (role <> 'ORG_ADMIN' AND branch_id IS NOT NULL)),
    CONSTRAINT fk_ua_person FOREIGN KEY (person_id, organization_id) REFERENCES person (id, organization_id),
    CONSTRAINT fk_ua_branch FOREIGN KEY (branch_id, organization_id) REFERENCES branch (id, organization_id)
);
-- [V9] unique(person, org, branch, role) tratando la sede nula como un valor
CREATE UNIQUE INDEX ux_ua_person_org_branch_role
    ON user_access (person_id, organization_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), role);
CREATE INDEX ix_ua_org_status ON user_access (organization_id, status);
CREATE INDEX ix_ua_person     ON user_access (person_id);

CREATE TABLE user_access_permission (
    id           UUID PRIMARY KEY,
    access_id    UUID        NOT NULL REFERENCES user_access (id),
    module_code  VARCHAR(40) NOT NULL REFERENCES module (code),
    actions      TEXT[]      NOT NULL,
    CONSTRAINT ux_uap UNIQUE (access_id, module_code)
);

CREATE TABLE permission_profile (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    name             VARCHAR(80)  NOT NULL,
    description      VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pp_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE UNIQUE INDEX ux_pp_org_name ON permission_profile (organization_id, lower(name));

CREATE TABLE permission_profile_item (
    profile_id   UUID        NOT NULL REFERENCES permission_profile (id),
    module_code  VARCHAR(40) NOT NULL REFERENCES module (code),
    actions      TEXT[]      NOT NULL,
    PRIMARY KEY (profile_id, module_code)
);

-- Tokens de un solo uso (invitación 72 h, recuperación 60 min). Solo se guarda el hash SHA-256.
CREATE TABLE one_time_token (
    id             UUID PRIMARY KEY,
    purpose        VARCHAR(10) NOT NULL,
    credential_id  UUID        NOT NULL REFERENCES credential (id),
    token_hash     VARCHAR(64)    NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ott_purpose CHECK (purpose IN ('INVITE','RESET')),
    CONSTRAINT ux_ott_hash UNIQUE (token_hash)
);
CREATE INDEX ix_ott_credential ON one_time_token (credential_id, purpose);

-- Refresh rotativo con detección de reúso: una "familia" = una sesión de dispositivo.
CREATE TABLE refresh_token (
    id             UUID PRIMARY KEY,
    family_id      UUID        NOT NULL,
    credential_id  UUID        NOT NULL REFERENCES credential (id),
    context_id     UUID,
    token_hash     VARCHAR(64)    NOT NULL,
    issued_at      TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoke_reason  VARCHAR(30),
    ip             VARCHAR(45),
    user_agent     VARCHAR(255),
    CONSTRAINT ux_rt_hash UNIQUE (token_hash)
);
CREATE INDEX ix_rt_family     ON refresh_token (family_id);
CREATE INDEX ix_rt_credential ON refresh_token (credential_id, revoked_at);

CREATE TABLE login_event (
    id               UUID PRIMARY KEY,
    at               TIMESTAMPTZ  NOT NULL,
    credential_id    UUID,
    organization_id  UUID,
    actor_type       VARCHAR(10),
    username         VARCHAR(160),
    result           VARCHAR(30)  NOT NULL,
    ip               VARCHAR(45),
    user_agent       VARCHAR(255),
    detail           VARCHAR(255)
);
CREATE INDEX ix_login_event_cred ON login_event (credential_id, at DESC);
CREATE INDEX ix_login_event_org  ON login_event (organization_id, at DESC);
