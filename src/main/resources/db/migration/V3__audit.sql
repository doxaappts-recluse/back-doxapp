-- V3 · Auditoría append-only (M22). Sin UPDATE/DELETE ni siquiera para el dueño de la BD:
-- solo la purga por política de retención (que registra la purga) podrá saltarse el trigger vía función dedicada.

CREATE TABLE audit_event (
    id                  BIGSERIAL PRIMARY KEY,
    at                  TIMESTAMPTZ  NOT NULL,
    actor_type          VARCHAR(10)  NOT NULL,
    actor_id            UUID,
    actor_role          VARCHAR(30),
    organization_id     UUID,
    branch_id           UUID,
    module_code         VARCHAR(40)  NOT NULL,
    action              VARCHAR(40)  NOT NULL,
    entity_type         VARCHAR(60),
    entity_id           VARCHAR(64),
    diff                JSONB,
    ip                  VARCHAR(45),
    user_agent          VARCHAR(255),
    assisted_grant_id   UUID,
    CONSTRAINT ck_audit_actor CHECK (actor_type IN ('STAFF','PERSON','SYSTEM'))
);
CREATE INDEX ix_audit_org_at    ON audit_event (organization_id, at DESC);
CREATE INDEX ix_audit_entity    ON audit_event (entity_type, entity_id);
CREATE INDEX ix_audit_module_at ON audit_event (module_code, at DESC);

CREATE FUNCTION audit_event_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event es de solo anexar (append-only)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();
