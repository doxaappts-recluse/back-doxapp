-- V39 · RLS acotado (auditoría de seguridad #4, 2026-10-09). Alcance decidido con el usuario: SOLO estas 4
-- tablas (person, credential, user_access, fin_movement) — el resto del esquema queda igual. Defensa en
-- profundidad: aunque una consulta del back olvidara su filtro por organización, Postgres no devolvería filas
-- de otra organización.
--
-- Cómo funciona: cada conexión que sale del pool (RlsAwareDataSource, Java) fija dos variables de sesión como
-- primera instrucción de su transacción, con SET LOCAL (se borran solas al terminar la transacción — una
-- conexión reciclada por el pool nunca puede heredar el contexto de la petición anterior):
--   app.current_org  → el organizationId del actor autenticado (vacío si no aplica).
--   app.rls_bypass    → 'true' solo cuando la transacción corre SIN una petición HTTP detrás (jobs programados,
--                        arranque de la app, el propio Flyway). Código del back, nunca entrada de un usuario.
--
-- Importante para quien inspeccione la base a mano (psql, un cliente directo): con FORCE ROW LEVEL SECURITY,
-- hasta el propio rol dueño de las tablas queda sujeto a la política. Una sesión manual que no fije
-- app.rls_bypass verá CERO filas en estas 4 tablas — no es un error, hay que ejecutar primero
-- `SET app.rls_bypass = 'true';` en esa sesión (o conectarse con un rol superusuario/BYPASSRLS, como ya hace
-- el editor SQL de Supabase).

-- person, user_access, fin_movement: organization_id siempre NOT NULL → política simple.
alter table person        enable row level security;
alter table person        force  row level security;
create policy person_org_isolation on person
    using (
        current_setting('app.rls_bypass', true) = 'true'
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    )
    with check (
        current_setting('app.rls_bypass', true) = 'true'
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    );

alter table user_access    enable row level security;
alter table user_access    force  row level security;
create policy user_access_org_isolation on user_access
    using (
        current_setting('app.rls_bypass', true) = 'true'
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    )
    with check (
        current_setting('app.rls_bypass', true) = 'true'
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    );

alter table fin_movement   enable row level security;
alter table fin_movement   force  row level security;
create policy fin_movement_org_isolation on fin_movement
    using (
        current_setting('app.rls_bypass', true) = 'true'
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    )
    with check (
        current_setting('app.rls_bypass', true) = 'true'
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    );

-- credential: organization_id es NULL para el personal de plataforma (actor_type STAFF, D2 — ver V2). Sin
-- contexto de organización (personal de plataforma sin acceso asistido), debe poder ver su PROPIA credencial de
-- plataforma (organization_id IS NULL) — nunca credenciales de personas de ninguna organización.
alter table credential     enable row level security;
alter table credential     force  row level security;
create policy credential_org_isolation on credential
    using (
        current_setting('app.rls_bypass', true) = 'true'
        or (organization_id is null and nullif(current_setting('app.current_org', true), '') is null)
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    )
    with check (
        current_setting('app.rls_bypass', true) = 'true'
        or (organization_id is null and nullif(current_setting('app.current_org', true), '') is null)
        or organization_id = nullif(current_setting('app.current_org', true), '')::uuid
    );
