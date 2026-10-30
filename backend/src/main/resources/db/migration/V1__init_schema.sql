-- Enterprise Jira Lite: baseline schema. All timestamps are timestamptz (UTC).

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid(), crypt() for seed hashes
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- fast ILIKE title search

-- ---------------------------------------------------------------------------
-- Immutability guards
-- ---------------------------------------------------------------------------
CREATE FUNCTION bts_prevent_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% on table % is forbidden: table is append-only', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE FUNCTION bts_prevent_delete() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Hard DELETE on table % is forbidden: use soft archival', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users
(
    id            uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(120) NOT NULL,
    system_role   varchar(20)  NOT NULL DEFAULT 'USER',
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_email_shape CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT ck_users_system_role CHECK (system_role IN ('ADMINISTRATOR', 'USER')),
    CONSTRAINT ck_users_name_not_blank CHECK (char_length(btrim(full_name)) > 0)
);

-- ---------------------------------------------------------------------------
-- projects
-- ---------------------------------------------------------------------------
CREATE TABLE projects
(
    id              uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    project_key     varchar(10)  NOT NULL,
    name            varchar(120) NOT NULL,
    description     varchar(2000),
    next_bug_number integer      NOT NULL DEFAULT 1,
    archived        boolean      NOT NULL DEFAULT false,
    archived_at     timestamptz,
    created_by      uuid         NOT NULL REFERENCES users (id),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_key UNIQUE (project_key),
    CONSTRAINT ck_projects_key_shape CHECK (project_key ~ '^[A-Z][A-Z0-9]{1,9}$'),
    CONSTRAINT ck_projects_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_projects_counter CHECK (next_bug_number >= 1),
    CONSTRAINT ck_projects_archive_consistent CHECK (archived = (archived_at IS NOT NULL))
);

-- ---------------------------------------------------------------------------
-- project_members (project-scoped RBAC)
-- ---------------------------------------------------------------------------
CREATE TABLE project_members
(
    project_id   uuid        NOT NULL REFERENCES projects (id),
    user_id      uuid        NOT NULL REFERENCES users (id),
    project_role varchar(20) NOT NULL,
    added_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT ck_members_role CHECK (project_role IN ('PROJECT_LEAD', 'DEVELOPER', 'REPORTER'))
);
CREATE INDEX ix_project_members_user ON project_members (user_id);

-- ---------------------------------------------------------------------------
-- bugs
-- ---------------------------------------------------------------------------
CREATE TABLE bugs
(
    id          uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    project_id  uuid         NOT NULL REFERENCES projects (id),
    bug_number  integer      NOT NULL,
    title       varchar(200) NOT NULL,
    description text         NOT NULL DEFAULT '',
    status      varchar(20)  NOT NULL DEFAULT 'OPEN',
    priority    varchar(20)  NOT NULL DEFAULT 'MEDIUM',
    resolution  varchar(30),
    reporter_id uuid         NOT NULL REFERENCES users (id),
    assignee_id uuid REFERENCES users (id),
    version     bigint       NOT NULL DEFAULT 0, -- JPA @Version (optimistic locking)
    archived    boolean      NOT NULL DEFAULT false,
    archived_at timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_bugs_project_number UNIQUE (project_id, bug_number),
    CONSTRAINT ck_bugs_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REOPENED')),
    CONSTRAINT ck_bugs_priority CHECK (priority IN ('BLOCKER', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_bugs_resolution CHECK (resolution IS NULL OR
                                         resolution IN ('FIXED', 'WONT_FIX', 'DUPLICATE', 'CANNOT_REPRODUCE',
                                                        'WORKS_AS_DESIGNED')),
    -- Closing REQUIRES a resolution; any non-closed state MUST NOT carry one.
    CONSTRAINT ck_bugs_resolution_iff_closed CHECK ((status = 'CLOSED') = (resolution IS NOT NULL)),
    CONSTRAINT ck_bugs_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_bugs_description_len CHECK (char_length(description) <= 20000),
    CONSTRAINT ck_bugs_archive_consistent CHECK (archived = (archived_at IS NOT NULL))
);
CREATE INDEX ix_bugs_project_status ON bugs (project_id, status) WHERE NOT archived;
CREATE INDEX ix_bugs_project_priority ON bugs (project_id, priority) WHERE NOT archived;
CREATE INDEX ix_bugs_project_created ON bugs (project_id, created_at DESC) WHERE NOT archived;
CREATE INDEX ix_bugs_assignee ON bugs (assignee_id) WHERE assignee_id IS NOT NULL AND NOT archived;
CREATE INDEX ix_bugs_reporter ON bugs (reporter_id);
CREATE INDEX ix_bugs_title_trgm ON bugs USING gin (title gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- bug_comments (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE bug_comments
(
    id         uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    bug_id     uuid        NOT NULL REFERENCES bugs (id),
    author_id  uuid        NOT NULL REFERENCES users (id),
    body       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_comments_body_len CHECK (char_length(btrim(body)) BETWEEN 1 AND 5000)
);
CREATE INDEX ix_bug_comments_bug ON bug_comments (bug_id, created_at);

-- ---------------------------------------------------------------------------
-- bug_history (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE bug_history
(
    id         uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    bug_id     uuid        NOT NULL REFERENCES bugs (id),
    actor_id   uuid        NOT NULL REFERENCES users (id),
    event_type varchar(30) NOT NULL,
    field_name varchar(60),
    old_value  varchar(500),
    new_value  varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_history_event CHECK (event_type IN
                                       ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'UNASSIGNED', 'UPDATED',
                                        'COMMENTED', 'ATTACHMENT_ADDED', 'ARCHIVED'))
);
CREATE INDEX ix_bug_history_bug ON bug_history (bug_id, created_at);

-- ---------------------------------------------------------------------------
-- attachments (soft archival only)
-- ---------------------------------------------------------------------------
CREATE TABLE attachments
(
    id                uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    bug_id            uuid         NOT NULL REFERENCES bugs (id),
    uploader_id       uuid         NOT NULL REFERENCES users (id),
    original_filename varchar(255) NOT NULL,
    storage_name      uuid         NOT NULL, -- opaque on-disk name; never derived from user input
    content_type      varchar(100) NOT NULL,
    size_bytes        bigint       NOT NULL,
    sha256            char(64)     NOT NULL,
    archived          boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_attachments_storage UNIQUE (storage_name),
    CONSTRAINT ck_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_attachments_sha CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_attachments_bug ON attachments (bug_id) WHERE NOT archived;

-- ---------------------------------------------------------------------------
-- audit_log (append-only, no FKs so records survive any future data lifecycle policy)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log
(
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id varchar(64) NOT NULL,
    actor_id       uuid,
    action         varchar(60) NOT NULL,
    entity_type    varchar(40) NOT NULL,
    entity_id      uuid,
    details        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    remote_ip      varchar(45),
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, created_at);
CREATE INDEX ix_audit_actor ON audit_log (actor_id, created_at);
CREATE INDEX ix_audit_correlation ON audit_log (correlation_id);

-- ---------------------------------------------------------------------------
-- Enforce append-only tables (UPDATE / DELETE / TRUNCATE all rejected)
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_bug_comments_append_only
    BEFORE UPDATE OR DELETE
    ON bug_comments
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_mutation();
CREATE TRIGGER trg_bug_comments_no_truncate
    BEFORE TRUNCATE
    ON bug_comments
    FOR EACH STATEMENT
EXECUTE FUNCTION bts_prevent_mutation();

CREATE TRIGGER trg_bug_history_append_only
    BEFORE UPDATE OR DELETE
    ON bug_history
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_mutation();
CREATE TRIGGER trg_bug_history_no_truncate
    BEFORE TRUNCATE
    ON bug_history
    FOR EACH STATEMENT
EXECUTE FUNCTION bts_prevent_mutation();

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE
    ON audit_log
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_mutation();
CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE
    ON audit_log
    FOR EACH STATEMENT
EXECUTE FUNCTION bts_prevent_mutation();

-- ---------------------------------------------------------------------------
-- Soft-delete only: block hard DELETE on core business tables
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_users_no_delete
    BEFORE DELETE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_delete();
CREATE TRIGGER trg_projects_no_delete
    BEFORE DELETE
    ON projects
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_delete();
CREATE TRIGGER trg_bugs_no_delete
    BEFORE DELETE
    ON bugs
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_delete();
CREATE TRIGGER trg_attachments_no_delete
    BEFORE DELETE
    ON attachments
    FOR EACH ROW
EXECUTE FUNCTION bts_prevent_delete();
