-- ============================================================================
-- SOLO DESARROLLO · datos de prueba. Lo ejecuta DevDatabaseReset después de las migraciones Flyway
-- cuando app.dev.reset-on-start=true (config/application.yml). NUNCA se ejecuta en producción.
--
-- Marcadores que el arranque reemplaza (así no hay hashes ni claves pegados en el archivo):
--   @@PWD_HASH@@    hash BCrypt (costo 12) de la contraseña de desarrollo  Demo12345xy
--   @@MFA_SECRET@@  secreto TOTP de desarrollo cifrado con tu app.security.mfa-key
--                   (secreto en texto para tu app autenticadora: DEVDOXAPPTESTSECRET234567ABCDEFG)
--
-- Todos los usuarios usan la contraseña  Demo12345xy .  Detalle completo en documentacion/F0_acceso_y_contexto.
-- ============================================================================

-- ---------------------------------------------------------------- PERSONAL DE PLATAFORMA (N1)
INSERT INTO platform_staff (id, first_name, last_name, doc_type, doc_number, email, phone, position, staff_role, status, status_reason, hire_date, created_at) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'Admin',  'Sistema',  'DNI', '70000001', 'doxa.app.ts@gmail.com', '+51987000001', 'Administrador general', 'SYSTEM_ADMIN',   'ACTIVE',   NULL,               current_date - 200, now()),
 ('a0000000-0000-0000-0000-000000000002', 'Sofía',  'Soporte',  'DNI', '70000002', 'soporte@doxapp.test',   '+51987000002', 'Soporte nivel 1',        'SYSTEM_SUPPORT', 'ACTIVE',   NULL,               current_date - 90,  now()),
 ('a0000000-0000-0000-0000-000000000003', 'Nuevo',  'Ingreso',  'DNI', '70000003', 'nuevo@doxapp.test',     NULL,           'Soporte nivel 1',        'SYSTEM_SUPPORT', 'INVITED',  NULL,               NULL,               now()),
 ('a0000000-0000-0000-0000-000000000004', 'Bruno',  'Baja',     'CE',  '70000004', 'baja@doxapp.test',      NULL,           'Ex soporte',             'SYSTEM_SUPPORT', 'INACTIVE', 'Fin de contrato', current_date - 400, now());

-- Admin y soporte llevan MFA ya activado (obligatorio para el personal): ingresan con Demo12345xy + código TOTP.
INSERT INTO credential (id, actor_type, staff_id, username, password_hash, status, must_change_password, mfa_enabled, mfa_secret_enc, password_changed_at, created_at) VALUES
 ('b0000000-0000-0000-0000-000000000001', 'STAFF', 'a0000000-0000-0000-0000-000000000001', 'doxa.app.ts@gmail.com', '@@PWD_HASH@@', 'ACTIVE',   false, true,  '@@MFA_SECRET@@', now(), now()),
 ('b0000000-0000-0000-0000-000000000002', 'STAFF', 'a0000000-0000-0000-0000-000000000002', 'soporte@doxapp.test',   '@@PWD_HASH@@', 'ACTIVE',   false, true,  '@@MFA_SECRET@@', now(), now()),
 ('b0000000-0000-0000-0000-000000000003', 'STAFF', 'a0000000-0000-0000-0000-000000000003', 'nuevo@doxapp.test',     NULL,           'INVITED',  false, false, NULL,             NULL,  now()),
 ('b0000000-0000-0000-0000-000000000004', 'STAFF', 'a0000000-0000-0000-0000-000000000004', 'baja@doxapp.test',      '@@PWD_HASH@@', 'INACTIVE', false, true,  '@@MFA_SECRET@@', now(), now());

-- ---------------------------------------------------------------- ORGANIZACIONES (N2)
INSERT INTO organization (id, name, legal_name, slug, email, status, created_at) VALUES
 ('11111111-1111-1111-1111-111111111111', 'Iglesia Demo',        'Asociación Iglesia Demo',        'demo',        'contacto@demo.test',        'ACTIVE',    now()),
 ('12222222-2222-2222-2222-222222222222', 'Iglesia Esperanza',   'Asociación Iglesia Esperanza',   'esperanza',   'contacto@esperanza.test',   'ACTIVE',    now()),
 ('13333333-3333-3333-3333-333333333333', 'Iglesia Sin Contrato','Asociación Sin Contrato',        'sincontrato', 'contacto@sincontrato.test', 'ACTIVE',    now()),
 ('14444444-4444-4444-4444-444444444444', 'Iglesia Suspendida',  'Asociación Suspendida',          'suspendida',  'contacto@suspendida.test',  'SUSPENDED', now()),
 ('15555555-5555-5555-5555-555555555555', 'Iglesia Borrador',    'Asociación Borrador',            'borrador',    'contacto@borrador.test',    'DRAFT',     now());

-- M02: datos legales / regionales / dirección de la organización demo y una marca de ejemplo (color naranja claro: sirve para
-- probar el ajuste automático de contraste). Sin logos: se suben desde Mi organización → Marca.
UPDATE organization SET tax_id = '20123456786', phone = '+51987654321', founded_date = '1998-05-17',
       address_line = 'Av. Principal 123', address_district = 'Miraflores', address_city = 'Lima', address_region = 'Lima',
       address_country = 'PE', address_reference = 'Frente al parque', activated_at = now() - interval '10 days'
 WHERE id = '11111111-1111-1111-1111-111111111111';
UPDATE organization SET tax_id = '20987654326', activated_at = now() - interval '10 days' WHERE id = '12222222-2222-2222-2222-222222222222';
UPDATE organization SET status_reason = 'Falta de pago' WHERE id = '14444444-4444-4444-4444-444444444444';

INSERT INTO organization_branding (organization_id, display_name, primary_color, secondary_color, welcome_text_es, welcome_text_en, contact_email, contact_phone, socials, revision, created_at) VALUES
 ('11111111-1111-1111-1111-111111111111', 'Iglesia Demo', '#F5A623', '#1F2937', 'Bienvenido a casa. Ingresa con tu usuario.', 'Welcome home. Sign in with your username.',
  'contacto@demo.test', '+51987654321', '{"facebook": "https://facebook.com/iglesiademo", "youtube": "https://youtube.com/@iglesiademo"}'::jsonb, 1, now());

-- ---------------------------------------------------------------- SEDES (N3)
INSERT INTO branch (id, organization_id, name, code, is_main, created_at) VALUES
 ('21111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Sede Central',   'CEN', true,  now()),
 ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Sede Norte',     'NOR', false, now()),
 ('23333333-3333-3333-3333-333333333333', '12222222-2222-2222-2222-222222222222', 'Sede Principal', 'PRI', true,  now()),
 ('24444444-4444-4444-4444-444444444444', '13333333-3333-3333-3333-333333333333', 'Sede Única',     'UNI', true,  now()),
 ('25555555-5555-5555-5555-555555555555', '14444444-4444-4444-4444-444444444444', 'Sede Central',   'CEN', true,  now());

-- Datos de presentación de las sedes (M04)
UPDATE branch SET address_line = 'Av. Larco 123', address_district = 'Miraflores', address_city = 'Lima', address_region = 'Lima', address_country = 'PE',
       address_reference = 'Frente al parque Kennedy', phone = '+51987654001', email = 'central@demo.test', opening_date = '1998-05-17',
       display_name = 'Iglesia Demo Central',
       public_schedule = '[{"day":"WED","from":"19:30","to":"21:00","label":"Reunión de oración"},{"day":"SUN","from":"09:00","to":"11:00","label":"Culto dominical"},{"day":"SUN","from":"18:00","to":"20:00","label":"Culto de la tarde"}]'::jsonb
 WHERE id = '21111111-1111-1111-1111-111111111111';
UPDATE branch SET address_line = 'Av. Túpac Amaru 4500', address_district = 'Independencia', address_city = 'Lima', address_region = 'Lima', address_country = 'PE',
       phone = '+51987654002', email = 'norte@demo.test', opening_date = '2012-03-04',
       public_schedule = '[{"day":"SUN","from":"10:00","to":"12:00","label":"Culto dominical"}]'::jsonb
 WHERE id = '22222222-2222-2222-2222-222222222222';
UPDATE branch SET address_line = 'Jr. Esperanza 45', address_district = 'Cercado', address_city = 'Arequipa', address_region = 'Arequipa', address_country = 'PE', opening_date = '2015-01-10'
 WHERE id = '23333333-3333-3333-3333-333333333333';

-- ---------------------------------------------------------------- CONTRATOS (sin contrato: "Iglesia Sin Contrato")
INSERT INTO contract (id, organization_id, status, start_date, end_date, max_licenses, plan_name, price, currency, activated_at, created_at) VALUES
 ('31111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'ACTIVE', current_date - 10, current_date + 300, 10, 'Contrato de prueba', 0, 'PEN', now(), now()),
 ('32222222-2222-2222-2222-222222222222', '12222222-2222-2222-2222-222222222222', 'ACTIVE', current_date - 10, current_date + 300, 5,  'Contrato de prueba', 0, 'PEN', now(), now()),
 ('34444444-4444-4444-4444-444444444444', '14444444-4444-4444-4444-444444444444', 'ACTIVE', current_date - 10, current_date + 300, 5,  'Contrato de prueba', 0, 'PEN', now(), now());

-- M23: Iglesia Demo contrata Integraciones (módulo contratable) para poder probarlas.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'INTEGRATIONS');

-- ---------------------------------------------------------------- PERSONAS
INSERT INTO person (id, organization_id, doc_type, doc_number, first_name, last_name, email, created_at) VALUES
 ('41111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'DNI', '12345678', 'Ana',     'Demo',      'ana@demo.test',            now()),
 ('41111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', 'DNI', '12345679', 'Luis',    'Demo',      'luis@demo.test',           now()),
 ('41111111-1111-1111-1111-111111111113', '11111111-1111-1111-1111-111111111111', 'DNI', '12345680', 'Rosa',    'Retirada',  'rosa@demo.test',           now()),
 ('41111111-1111-1111-1111-111111111114', '11111111-1111-1111-1111-111111111111', 'DNI', '12345681', 'Ivo',     'Invitado',  'invitado@demo.test',       now()),
 ('42222222-2222-2222-2222-222222222221', '12222222-2222-2222-2222-222222222222', 'DNI', '12345678', 'Ana',     'Esperanza', 'ana@demo.test',            now()),
 ('42222222-2222-2222-2222-222222222222', '12222222-2222-2222-2222-222222222222', 'DNI', '22345678', 'Carlos',  'Quispe',    'carlos@esperanza.test',    now()),
 ('43333333-3333-3333-3333-333333333331', '13333333-3333-3333-3333-333333333333', 'DNI', '32345678', 'Marta',   'Sincontrato','marta@sincontrato.test',  now()),
 ('44444444-4444-4444-4444-444444444441', '14444444-4444-4444-4444-444444444444', 'DNI', '42345678', 'Sara',    'Suspendida','sara@suspendida.test',     now());

-- Usuario = correo. "ana@demo.test" existe en DOS organizaciones con la misma contraseña => el login pide elegir una.
INSERT INTO credential (id, actor_type, person_id, organization_id, username, password_hash, status, must_change_password, password_changed_at, created_at) VALUES
 ('c1111111-1111-1111-1111-111111111111', 'PERSON', '41111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'ana@demo.test',            '@@PWD_HASH@@', 'ACTIVE',   false, now(),  now()),
 ('c1111111-1111-1111-1111-111111111112', 'PERSON', '41111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', 'luis@demo.test',           '@@PWD_HASH@@', 'ACTIVE',   false, now(),  now()),
 ('c1111111-1111-1111-1111-111111111113', 'PERSON', '41111111-1111-1111-1111-111111111113', '11111111-1111-1111-1111-111111111111', 'rosa@demo.test',           '@@PWD_HASH@@', 'INACTIVE', false, now(),  now()),
 ('c1111111-1111-1111-1111-111111111114', 'PERSON', '41111111-1111-1111-1111-111111111114', '11111111-1111-1111-1111-111111111111', 'invitado@demo.test',       NULL,           'INVITED',  false, NULL,   now()),
 ('c2222222-2222-2222-2222-222222222221', 'PERSON', '42222222-2222-2222-2222-222222222221', '12222222-2222-2222-2222-222222222222', 'ana@demo.test',            '@@PWD_HASH@@', 'ACTIVE',   false, now(),  now()),
 ('c2222222-2222-2222-2222-222222222222', 'PERSON', '42222222-2222-2222-2222-222222222222', '12222222-2222-2222-2222-222222222222', 'carlos@esperanza.test',    '@@PWD_HASH@@', 'ACTIVE',   false, now(),  now()),
 ('c3333333-3333-3333-3333-333333333331', 'PERSON', '43333333-3333-3333-3333-333333333331', '13333333-3333-3333-3333-333333333333', 'marta@sincontrato.test',   '@@PWD_HASH@@', 'ACTIVE',   false, now(),  now()),
 ('c4444444-4444-4444-4444-444444444441', 'PERSON', '44444444-4444-4444-4444-444444444441', '14444444-4444-4444-4444-444444444444', 'sara@suspendida.test',     '@@PWD_HASH@@', 'ACTIVE',   false, now(),  now());

-- ---------------------------------------------------------------- ACCESOS (rol + sede)
-- Ana (Demo): 2 accesos => tras el login aparece "Elige tu acceso". Luis: 1 acceso => entra directo.
INSERT INTO user_access (id, person_id, organization_id, branch_id, role, status, valid_from, created_at) VALUES
 ('d1111111-1111-1111-1111-111111111111', '41111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', NULL,                                   'ORG_ADMIN',        'ACTIVE',   current_date - 5, now()),
 ('d1111111-1111-1111-1111-111111111112', '41111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'ORG_BRANCH_ADMIN', 'ACTIVE',   current_date - 5, now()),
 ('d1111111-1111-1111-1111-111111111113', '41111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'ORG_BRANCH_ADMIN', 'ACTIVE',   current_date - 5, now()),
 ('d1111111-1111-1111-1111-111111111114', '41111111-1111-1111-1111-111111111113', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'ORG_BRANCH_ADMIN', 'INACTIVE', current_date - 50, now()),
 ('d1111111-1111-1111-1111-111111111115', '41111111-1111-1111-1111-111111111114', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'ORG_BRANCH_ADMIN', 'INVITED',  current_date,     now()),
 ('d2222222-2222-2222-2222-222222222221', '42222222-2222-2222-2222-222222222221', '12222222-2222-2222-2222-222222222222', NULL,                                   'ORG_ADMIN',        'ACTIVE',   current_date - 5, now()),
 ('d2222222-2222-2222-2222-222222222222', '42222222-2222-2222-2222-222222222222', '12222222-2222-2222-2222-222222222222', '23333333-3333-3333-3333-333333333333', 'ORG_BRANCH_ADMIN', 'ACTIVE',   current_date - 5, now()),
 ('d3333333-3333-3333-3333-333333333331', '43333333-3333-3333-3333-333333333331', '13333333-3333-3333-3333-333333333333', NULL,                                   'ORG_ADMIN',        'ACTIVE',   current_date - 5, now()),
 ('d4444444-4444-4444-4444-444444444441', '44444444-4444-4444-4444-444444444441', '14444444-4444-4444-4444-444444444444', NULL,                                   'ORG_ADMIN',        'ACTIVE',   current_date - 5, now());

-- ---------------------------------------------------------------- PERFILES DE PERMISOS (M05)
INSERT INTO permission_profile (id, organization_id, name, description, status, created_at, version) VALUES
 ('e1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Solo consulta', 'Ve el panel y las sedes; no modifica nada.', 'ACTIVE', now(), 0);
INSERT INTO permission_profile_item (profile_id, module_code, actions) VALUES
 ('e1111111-1111-1111-1111-111111111111', 'DASHBOARD', ARRAY['V']),
 ('e1111111-1111-1111-1111-111111111111', 'BRANCH',    ARRAY['V']);

-- ---------------------------------------------------------------- PERSONAS, ETIQUETAS Y HOGARES (M06)
-- Las cuatro personas de acceso llevan sede principal y datos básicos.
UPDATE person SET primary_branch_id = '21111111-1111-1111-1111-111111111111', sex = 'FEMALE', birth_date = '1985-04-12', phone = '+51987000001' WHERE id = '41111111-1111-1111-1111-111111111111';
UPDATE person SET primary_branch_id = '21111111-1111-1111-1111-111111111111', sex = 'MALE',   birth_date = '1988-09-30', phone = '+51987000002' WHERE id = '41111111-1111-1111-1111-111111111112';
UPDATE person SET primary_branch_id = '21111111-1111-1111-1111-111111111111', sex = 'FEMALE', birth_date = '1979-01-22', phone = '+51987000003' WHERE id = '41111111-1111-1111-1111-111111111113';
UPDATE person SET primary_branch_id = '21111111-1111-1111-1111-111111111111', sex = 'MALE',   birth_date = '1992-06-18', phone = '+51987000004' WHERE id = '41111111-1111-1111-1111-111111111114';

INSERT INTO person (id, organization_id, doc_type, doc_number, first_name, last_name, sex, birth_date, marital_status, email, phone, occupation, primary_branch_id, joined_at, status, status_reason, deceased_at, created_at) VALUES
 ('45111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'DNI', '40000001', 'Pedro',  'Ramírez Soto',  'MALE',   '1980-05-10', 'MARRIED', 'pedro@demo.test',  '+51987111001', 'Ingeniero',   '21111111-1111-1111-1111-111111111111', '2015-03-01', 'ACTIVE',   NULL, NULL, now()),
 ('45111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'DNI', '40000002', 'María',  'Torres Vega',   'FEMALE', '1983-08-21', 'MARRIED', 'maria@demo.test',  '+51987111002', 'Docente',     '21111111-1111-1111-1111-111111111111', '2015-03-01', 'ACTIVE',   NULL, NULL, now()),
 ('45111111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', 'DNI', '40000003', 'Lucas',  'Ramírez Torres','MALE',   '2012-03-05', 'SINGLE',  NULL,                NULL,           NULL,          '21111111-1111-1111-1111-111111111111', '2015-03-01', 'ACTIVE',   NULL, NULL, now()),
 ('45111111-1111-1111-1111-000000000004', '11111111-1111-1111-1111-111111111111', 'DNI', '40000004', 'Sofía',  'Ramírez Torres','FEMALE', '2015-11-30', 'SINGLE',  NULL,                NULL,           NULL,          '21111111-1111-1111-1111-111111111111', '2018-01-15', 'ACTIVE',   NULL, NULL, now()),
 ('45111111-1111-1111-1111-000000000005', '11111111-1111-1111-1111-111111111111', 'DNI', '40000005', 'Juan',   'Pérez Gómez',   'MALE',   '1975-01-15', 'MARRIED', 'juan@demo.test',   '+51987111005', 'Comerciante', '22222222-2222-2222-2222-222222222222', '2019-06-09', 'ACTIVE',   NULL, NULL, now()),
 ('45111111-1111-1111-1111-000000000006', '11111111-1111-1111-1111-111111111111', 'DNI', '40000006', 'Carmen', 'Gómez Rojas',   'FEMALE', '1990-12-02', 'SINGLE',  'carmen@demo.test',  '+51987111006', 'Enfermera',   '22222222-2222-2222-2222-222222222222', '2021-02-14', 'ACTIVE',   NULL, NULL, now()),
 ('45111111-1111-1111-1111-000000000007', '11111111-1111-1111-1111-111111111111', 'DNI', '40000007', 'José',   'Quispe Mamani', 'MALE',   '1965-07-07', 'WIDOWED', NULL,                '+51987111007', 'Jubilado',    '21111111-1111-1111-1111-111111111111', '2010-05-20', 'INACTIVE', 'Se mudó de ciudad', NULL, now()),
 ('45111111-1111-1111-1111-000000000008', '11111111-1111-1111-1111-111111111111', 'DNI', '40000008', 'Elena',  'Vargas Lino',   'FEMALE', '1950-10-19', 'WIDOWED', NULL,                NULL,           NULL,          '22222222-2222-2222-2222-222222222222', '2012-08-12', 'DECEASED', NULL, current_date - 40, now()),
 ('45222222-2222-2222-2222-000000000001', '12222222-2222-2222-2222-222222222222', 'DNI', '40000001', 'Pedro',  'Ramírez Esperanza','MALE','1981-02-11', 'SINGLE',  'pedro@esperanza.test','+51987222001','Chofer',     '23333333-3333-3333-3333-333333333333', '2020-01-05', 'ACTIVE',   NULL, NULL, now());

INSERT INTO person_branch (id, person_id, branch_id, from_date, is_current, created_at)
SELECT gen_random_uuid(), id, primary_branch_id, current_date - 30, true, now() FROM person WHERE primary_branch_id IS NOT NULL;

INSERT INTO tag (id, organization_id, name, color, created_at) VALUES
 ('47111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'Líder',      '#1677FF', now()),
 ('47111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'Nuevo',      '#52C41A', now()),
 ('47111111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', 'Voluntario', '#FA8C16', now());
INSERT INTO person_tag (person_id, tag_id, created_at) VALUES
 ('45111111-1111-1111-1111-000000000001', '47111111-1111-1111-1111-000000000001', now()),
 ('45111111-1111-1111-1111-000000000001', '47111111-1111-1111-1111-000000000003', now()),
 ('45111111-1111-1111-1111-000000000006', '47111111-1111-1111-1111-000000000002', now());

INSERT INTO household (id, organization_id, name, address_line, address_district, address_city, address_region, address_country, status, created_at) VALUES
 ('46111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'Familia Ramírez Torres', 'Av. Arequipa 1234', 'Lince', 'Lima', 'Lima', 'PE', 'ACTIVE', now());
INSERT INTO household_member (id, household_id, person_id, role, guardian, joined_at, created_at) VALUES
 (gen_random_uuid(), '46111111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000001', 'HEAD',   true,  current_date - 30, now()),
 (gen_random_uuid(), '46111111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000002', 'SPOUSE', true,  current_date - 30, now()),
 (gen_random_uuid(), '46111111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000003', 'CHILD',  false, current_date - 30, now()),
 (gen_random_uuid(), '46111111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000004', 'CHILD',  false, current_date - 30, now());

-- ---------------------------------------------------------------- VISITANTES (M07)
-- Iglesia Demo contrata el módulo Visitantes (Esperanza no: sirve para probar el 403 y el 404 del formulario público).
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'VISITOR');

-- ---------------------------------------------------------------- TRASLADOS Y VISIBILIDAD ENTRE SEDES (M21)
-- Iglesia Demo contrata las reglas multi-sede; Esperanza no (sirve para probar el 403 con la bandeja base funcionando).
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'BRANCH_TRANSFER');
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'VISIBILITY_RULES');

INSERT INTO person (id, organization_id, first_name, last_name, phone, email, primary_branch_id, status, created_at) VALUES
 ('45111111-1111-1111-1111-000000000011', '11111111-1111-1111-1111-111111111111', 'Diego',  'Flores Cruz',  '+51987333001', NULL,                '21111111-1111-1111-1111-111111111111', 'ACTIVE', now()),
 ('45111111-1111-1111-1111-000000000012', '11111111-1111-1111-1111-111111111111', 'Lucía',  'Rojas Paz',    '+51987333002', 'lucia@visita.test', '21111111-1111-1111-1111-111111111111', 'ACTIVE', now()),
 ('45111111-1111-1111-1111-000000000013', '11111111-1111-1111-1111-111111111111', 'Mario',  'Vega Luna',    '+51987333003', NULL,                '22222222-2222-2222-2222-222222222222', 'ACTIVE', now()),
 ('45111111-1111-1111-1111-000000000014', '11111111-1111-1111-1111-111111111111', 'Rita',   'Campos Ríos',  NULL,           'rita@visita.test',  '21111111-1111-1111-1111-111111111111', 'ACTIVE', now()),
 ('45111111-1111-1111-1111-000000000015', '11111111-1111-1111-1111-111111111111', 'Óscar',  'Peña Díaz',    '+51987333005', NULL,                '21111111-1111-1111-1111-111111111111', 'ACTIVE', now());
INSERT INTO person_branch (id, person_id, branch_id, from_date, is_current, created_at)
SELECT gen_random_uuid(), id, primary_branch_id, current_date - 5, true, now() FROM person WHERE id::text ~ '^45111111-1111-1111-1111-0000000000(1[1-5])$';

INSERT INTO visitor_case (id, organization_id, branch_id, person_id, first_visit_date, how_arrived, invited_by, stage, consolidator_id, assigned_at,
                          first_contact_at, last_contact_at, next_action_date, integrated_at, closed_at, archive_reason, notes, source, consent_status, consent_at, created_at) VALUES
 -- NEW sin responsable y con el plazo vencido (creado hace 3 días)
 ('48111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000011',
  current_date - 3, 'WALK_IN', NULL, 'NEW', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Llegó al culto dominical con su familia.', 'STAFF', 'PENDING', NULL, now() - interval '3 days'),
 -- En seguimiento con Luis, próxima llamada mañana
 ('48111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000012',
  current_date - 4, 'MEMBER_INVITATION', '45111111-1111-1111-1111-000000000001', 'IN_FOLLOWUP', '41111111-1111-1111-1111-111111111112', now() - interval '3 days',
  now() - interval '2 days', now() - interval '1 day', current_date + 1, NULL, NULL, NULL, NULL, 'STAFF', 'GRANTED', now() - interval '4 days', now() - interval '4 days'),
 -- Nuevo desde el formulario público (Norte)
 ('48111111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '45111111-1111-1111-1111-000000000013',
  current_date, 'WEBSITE', NULL, 'NEW', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'PUBLIC_FORM', 'GRANTED', now(), now()),
 -- Integrada (a la espera de membresía, M08)
 ('48111111-1111-1111-1111-000000000004', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000014',
  current_date - 30, 'SOCIAL_MEDIA', NULL, 'INTEGRATED', '41111111-1111-1111-1111-111111111112', now() - interval '29 days',
  now() - interval '28 days', now() - interval '5 days', NULL, now() - interval '3 days', NULL, NULL, NULL, 'STAFF', 'GRANTED', now() - interval '30 days', now() - interval '30 days'),
 -- Archivado
 ('48111111-1111-1111-1111-000000000005', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000015',
  current_date - 40, 'FLYER', NULL, 'ARCHIVED', '41111111-1111-1111-1111-111111111112', now() - interval '39 days',
  now() - interval '38 days', now() - interval '20 days', NULL, NULL, now() - interval '20 days', 'MOVED', NULL, 'STAFF', 'GRANTED', now() - interval '40 days', now() - interval '40 days');

INSERT INTO follow_up_contact (id, organization_id, subject_type, subject_id, at, method, result, notes, next_action_date, by_person_id, created_at) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'VISITOR_CASE', '48111111-1111-1111-1111-000000000002', now() - interval '2 days', 'CALL', 'CONTACTED',
  'Le gustó la reunión de jóvenes; quiere volver.', current_date + 1, '41111111-1111-1111-1111-111111111112', now() - interval '2 days'),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'VISITOR_CASE', '48111111-1111-1111-1111-000000000002', now() - interval '1 day', 'WHATSAPP', 'NO_ANSWER',
  NULL, current_date + 1, '41111111-1111-1111-1111-111111111112', now() - interval '1 day'),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'VISITOR_CASE', '48111111-1111-1111-1111-000000000004', now() - interval '28 days', 'VISIT', 'CONTACTED',
  'Se le visitó en su casa.', NULL, '41111111-1111-1111-1111-111111111112', now() - interval '28 days'),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'VISITOR_CASE', '48111111-1111-1111-1111-000000000005', now() - interval '38 days', 'CALL', 'CONTACTED',
  NULL, NULL, '41111111-1111-1111-1111-111111111112', now() - interval '38 days');

-- ---------------------------------------------------------------- CONSENTIMIENTOS (M06 parte 2A)
-- Los visitantes con autorización tienen tratamiento de datos y comunicaciones; Pedro además el directorio; los demás no tienen consentimiento.
INSERT INTO consent_record (id, organization_id, person_id, purpose_code, version, granted_at, source, granted_by)
SELECT gen_random_uuid(), c.organization_id, c.person_id, p.code, 'v1', coalesce(c.consent_at, c.created_at), 'VISITOR', NULL
  FROM visitor_case c CROSS JOIN (VALUES ('DATA_PROCESSING'), ('COMMUNICATIONS')) AS p(code) WHERE c.consent_status = 'GRANTED';
INSERT INTO consent_record (id, organization_id, person_id, purpose_code, version, granted_at, source, granted_by) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'DATA_PROCESSING', 'v1', now() - interval '30 days', 'STAFF', NULL),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'COMMUNICATIONS',  'v1', now() - interval '30 days', 'STAFF', NULL),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'DIRECTORY',       'v1', now() - interval '30 days', 'STAFF', NULL);

-- ---------------------------------------------------------------- ASISTENCIA (M09)
-- Iglesia Demo contrata Check-in de niños (Esperanza no: sirve para probar el 403). Asistencia es un módulo base.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'CHILD_CHECKIN');
INSERT INTO church_service (id, organization_id, branch_id, name, service_type, day_of_week, start_time, duration_min, space_note, recurrence, self_checkin_enabled, status, created_at) VALUES
 ('a1111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'Culto dominical', 'SUNDAY',  7, '09:00', 120, 'Templo principal', 'WEEKLY', true,  'ACTIVE', now()),
 ('a1111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'Reunión de oración', 'PRAYER', 3, '19:30', 90, NULL, 'WEEKLY', false, 'ACTIVE', now()),
 ('a1111111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Culto dominical', 'SUNDAY',  7, '10:00', 120, NULL, 'WEEKLY', false, 'ACTIVE', now());

-- ---------------------------------------------------------------- MEMBRESÍA Y RITOS (M08)
-- Iglesia Demo contrata los cuatro módulos (Esperanza no: sirve para probar el 403).
INSERT INTO contract_module (contract_id, module_code) VALUES
 ('31111111-1111-1111-1111-111111111111', 'MEMBERSHIP'),
 ('31111111-1111-1111-1111-111111111111', 'BAPTISM'),
 ('31111111-1111-1111-1111-111111111111', 'MARRIAGE'),
 ('31111111-1111-1111-1111-111111111111', 'CHILD_DEDICATION');
INSERT INTO membership (id, organization_id, branch_id, person_id, kind, status, current, start_date, origin, created_at) VALUES
 ('b8111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'MEMBER',   'ACTIVE', true, current_date - 1000, 'RECORD', now()),
 ('b8111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000002', 'MEMBER',   'ACTIVE', true, current_date - 900,  'RECORD', now()),
 ('b8111111-1111-1111-1111-000000000005', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '45111111-1111-1111-1111-000000000005', 'ATTENDEE', 'ACTIVE', true, current_date - 200,  'RECORD', now());
INSERT INTO rite (id, organization_id, branch_id, rite_type, person_id, event_date, place, officiant_text, status, origin, completed_at, created_at) VALUES
 ('b9111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'BAPTISM', '45111111-1111-1111-1111-000000000001',
  current_date - 800, 'Templo principal', 'Pastor Demo', 'COMPLETED', 'RECORD', now() - interval '800 days', now());
INSERT INTO rite_requirement (id, organization_id, rite_type, code, label, required, source, min_age, active, sort_order, created_at) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'MEMBERSHIP', 'CLASS',       'Clase de membresía',        true,  'MANUAL', NULL, true, 1, now()),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'BAPTISM',    'PREP_COURSE', 'Curso pre-bautismal',       true,  'MANUAL', NULL, true, 1, now()),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'MARRIAGE',   'COUNSELING',  'Consejería prematrimonial', true,  'MANUAL', NULL, true, 1, now()),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'MARRIAGE',   'CIVIL',       'Acta civil presentada',     false, 'MANUAL', NULL, true, 2, now());

-- ---------------------------------------------------------------- GRUPOS Y CÉLULAS (M10)
-- Demo contrata el módulo (Esperanza no: sirve para probar el 403). Grupo activo en Central con Pedro de líder y María de integrante, y un borrador en Norte.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'SMALL_GROUP');
INSERT INTO small_group (id, organization_id, branch_id, name, category, audience, description, meeting_day, meeting_time, location, zone, capacity, open_to_join, start_date, status, created_at, version) VALUES
 ('b1011111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'Célula Esperanza Centro', 'CELL', 'ADULT', 'Célula de familias del centro.', 3, '19:30', 'Av. Central 123', 'Centro', 10, true, current_date - 120, 'ACTIVE', now(), 0),
 ('b1011111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Célula Norte Jóvenes', 'YOUTH', 'YOUTH', NULL, 5, '20:00', NULL, 'Norte', 12, false, NULL, 'DRAFT', now(), 0);
INSERT INTO group_member (id, organization_id, group_id, person_id, role, status, joined_at, created_at) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'b1011111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000001', 'LEADER', 'ACTIVE', current_date - 120, now()),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'b1011111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000002', 'MEMBER', 'ACTIVE', current_date - 100, now());
INSERT INTO group_meeting (id, organization_id, branch_id, group_id, meeting_date, meeting_time, topic, status, created_at, version) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'b1011111-1111-1111-1111-000000000001', current_date + 3, '19:30', 'Estudio de Juan 3', 'PLANNED', now(), 0);

-- ---------------------------------------------------------------- MINISTERIOS (M11a)
-- Módulo base (sin contrato). Alabanza (sin verificación) y Niños (solo adultos y con verificación); en Central, Pedro dirige Alabanza y María canta.
INSERT INTO ministry (id, organization_id, name, description, color, requires_screening, screening_type, adult_only, status, created_at, version) VALUES
 ('b1a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'Alabanza', 'Música y adoración del servicio.', '#4F46E5', false, NULL, false, 'ACTIVE', now(), 0),
 ('b1a11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'Niños', 'Escuela dominical y cuidado de niños.', '#16A34A', true, 'BACKGROUND', true, 'ACTIVE', now(), 0);
INSERT INTO ministry_position (id, ministry_id, name, is_leader, sort_order, created_at) VALUES
 ('b1b11111-1111-1111-1111-000000000001', 'b1a11111-1111-1111-1111-000000000001', 'Director', true, 10, now()),
 ('b1b11111-1111-1111-1111-000000000002', 'b1a11111-1111-1111-1111-000000000001', 'Músico', false, 20, now()),
 ('b1b11111-1111-1111-1111-000000000003', 'b1a11111-1111-1111-1111-000000000001', 'Vocalista', false, 30, now()),
 ('b1b11111-1111-1111-1111-000000000004', 'b1a11111-1111-1111-1111-000000000002', 'Coordinador', true, 10, now()),
 ('b1b11111-1111-1111-1111-000000000005', 'b1a11111-1111-1111-1111-000000000002', 'Maestro', false, 20, now());
INSERT INTO branch_ministry (id, organization_id, ministry_id, branch_id, leader_person_id, status, created_at, version) VALUES
 ('b1c11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'b1a11111-1111-1111-1111-000000000001', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'ACTIVE', now(), 0),
 ('b1c11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'b1a11111-1111-1111-1111-000000000002', '21111111-1111-1111-1111-111111111111', NULL, 'ACTIVE', now(), 0);
INSERT INTO ministry_assignment (id, organization_id, branch_ministry_id, person_id, position_id, from_date, status, created_at) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'b1c11111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000001', 'b1b11111-1111-1111-1111-000000000001', current_date - 90, 'ACTIVE', now()),
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'b1c11111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000002', 'b1b11111-1111-1111-1111-000000000003', current_date - 60, 'ACTIVE', now());
INSERT INTO person_screening (id, organization_id, person_id, type, status, issued_at, expires_at, created_at, version) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'BACKGROUND', 'CLEARED', current_date - 30, current_date + 335, now(), 0);

-- ---------------------------------------------------------------- TURNOS Y PROGRAMACIÓN DE VOLUNTARIOS (M11b)
-- Demo contrata el módulo (Esperanza no: sirve para probar el 403). Plan del culto dominical de Central en DRAFT, con un turno de Alabanza (2 vocalistas) y María ya propuesta.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'VOLUNTEER_SCHEDULING');
INSERT INTO service_plan (id, organization_id, branch_id, plan_date, context_type, context_id, title, status, created_at, version) VALUES
 ('b1d11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', current_date + 3, 'SERVICE', 'a1111111-1111-1111-1111-000000000001', 'Culto dominical', 'DRAFT', now(), 0);
INSERT INTO shift_slot (id, plan_id, branch_ministry_id, position_id, needed, start_time, end_time, notes, created_at, version) VALUES
 ('b1e11111-1111-1111-1111-000000000001', 'b1d11111-1111-1111-1111-000000000001', 'b1c11111-1111-1111-1111-000000000001', 'b1b11111-1111-1111-1111-000000000003', 2, '08:30', '10:30', NULL, now(), 0);
INSERT INTO shift_assignment (id, slot_id, person_id, status, created_at, version) VALUES
 (gen_random_uuid(), 'b1e11111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000002', 'PROPOSED', now(), 0);

-- ---------------------------------------------------------------- CUIDADO PASTORAL Y ORACIÓN (M12)
-- Demo contrata ambos módulos. Un caso manual (consejería) asignado a Pedro sobre Lucas, y una petición de oración pública ya aprobada.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'PASTORAL_CARE');
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'PRAYER');
INSERT INTO pastoral_case (id, organization_id, branch_id, person_id, type, priority, status, assigned_to, source, confidentiality, due_at, created_at, updated_at, version) VALUES
 ('c1a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000003',
  'COUNSELING', 'NORMAL', 'OPEN', '45111111-1111-1111-1111-000000000001', 'MANUAL', 'STANDARD', now() + interval '72 hours', now(), now(), 0);
INSERT INTO prayer_request (id, organization_id, branch_id, requested_by, text, category, visibility, anonymous, moderation, status, created_at, updated_at, version) VALUES
 (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000002',
  'Por favor oren por la salud de mi familia en este tiempo.', 'HEALTH', 'CONGREGATION', FALSE, 'APPROVED', 'OPEN', now(), now(), 0);

-- ---------------------------------------------------------------- FORMACIÓN / ACADEMIA BÍBLICA (M13)
-- Malla activa con 2 cursos (fundamentos -> doctrina). Un dictado del primero, en curso, con María como docente y Pedro matriculado.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'TRAINING');
INSERT INTO curriculum (id, organization_id, name, description, status, created_at, version) VALUES
 ('d1a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'Academia Bíblica Básica', 'Formación doctrinal de dos niveles.', 'ACTIVE', now(), 0);
INSERT INTO course (id, organization_id, curriculum_id, order_num, name, description, hours, min_attendance_pct, pass_grade, status, created_at, version) VALUES
 ('d1b11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'd1a11111-1111-1111-1111-000000000001', 1, 'Fundamentos de la Fe', 'Nivel 1 de la academia.', 20, 70, 11, 'ACTIVE', now(), 0),
 ('d1b11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'd1a11111-1111-1111-1111-000000000001', 2, 'Doctrina Cristiana', 'Nivel 2 de la academia.', 24, 70, 11, 'ACTIVE', now(), 0);
INSERT INTO course_class (id, organization_id, branch_id, course_id, teacher_person_id, day_of_week, start_time, end_time, location, start_date, end_date, capacity, status, started_at, created_at, version) VALUES
 ('d1c11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'd1b11111-1111-1111-1111-000000000001',
  '45111111-1111-1111-1111-000000000002', 3, '18:00', '20:00', 'Aula 2', current_date - 14, current_date + 46, 20, 'IN_PROGRESS', now() - interval '14 days', now(), 0);
INSERT INTO enrollment (id, organization_id, branch_id, person_id, class_id, status, enrolled_at, created_by, version) VALUES
 ('d1d11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001',
  'd1c11111-1111-1111-1111-000000000001', 'ENROLLED', now() - interval '14 days', '45111111-1111-1111-1111-000000000001', 0);

-- ---------------------------------------------------------------- EVENTOS (M14)
-- Evento de organización, publicado, cupo 2 ya cubierto por Pedro y María (MEMBER, gratis): sirve para probar la lista de espera
-- matriculando a una tercera persona. Retiro de sede con pago por transferencia pendiente de confirmar (probar auto-aprobación → 403).
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'EVENTS');
INSERT INTO org_event (id, organization_id, scope, branch_id, name, type_code, description, start_at, end_at, location, capacity, waitlist_enabled,
    reg_opens_at, reg_closes_at, cancel_deadline, is_public, guests_max, status, created_at, version) VALUES
 ('e1a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'ORGANIZATION', NULL, 'Conferencia de Liderazgo 2026', NULL,
  'Un día de talleres para líderes de ministerio.', now() + interval '30 days' + time '09:00', now() + interval '30 days' + time '18:00',
  'Auditorio principal', 2, true, now() - interval '5 days', now() + interval '29 days', now() + interval '25 days', true, 1, 'PUBLISHED', now(), 0);
INSERT INTO event_price_tier (id, event_id, category, amount, currency) VALUES
 (gen_random_uuid(), 'e1a11111-1111-1111-1111-000000000001', 'MEMBER', 0,     'PEN'),
 (gen_random_uuid(), 'e1a11111-1111-1111-1111-000000000001', 'VISITOR', 30.00,'PEN'),
 (gen_random_uuid(), 'e1a11111-1111-1111-1111-000000000001', 'GUEST',  50.00, 'PEN');
INSERT INTO event_question (id, event_id, label, type, required, sort_order) VALUES
 (gen_random_uuid(), 'e1a11111-1111-1111-1111-000000000001', '¿Alguna restricción alimentaria?', 'TEXT', false, 0);
INSERT INTO event_registration (id, event_id, person_id, category, status, payment_status, amount, currency, guests, source, registered_by, ticket_code, created_at, created_by, version) VALUES
 ('e1c11111-1111-1111-1111-000000000001', 'e1a11111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000001', 'MEMBER', 'REGISTERED', 'WAIVED', 0, 'PEN', 0, 'STAFF', '41111111-1111-1111-1111-111111111111', 'DEMOEVT001', now() - interval '4 days', '41111111-1111-1111-1111-111111111111', 0),
 ('e1c11111-1111-1111-1111-000000000002', 'e1a11111-1111-1111-1111-000000000001', '45111111-1111-1111-1111-000000000002', 'MEMBER', 'REGISTERED', 'WAIVED', 0, 'PEN', 0, 'STAFF', '41111111-1111-1111-1111-111111111111', 'DEMOEVT002', now() - interval '3 days', '41111111-1111-1111-1111-111111111111', 0);

INSERT INTO org_event (id, organization_id, scope, branch_id, name, type_code, description, start_at, end_at, location, capacity, waitlist_enabled,
    cancel_deadline, is_public, guests_max, require_payment_for_checkin, status, created_at, version) VALUES
 ('e1a11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'BRANCH', '21111111-1111-1111-1111-111111111111', 'Retiro de Jóvenes',
  NULL, 'Fin de semana de retiro en la sede central.', now() + interval '20 days', now() + interval '22 days', 'Casa de Retiros Emaús', 15, false,
  now() + interval '15 days', true, 0, true, 'PUBLISHED', now(), 0);
INSERT INTO event_price_tier (id, event_id, category, amount, currency) VALUES
 (gen_random_uuid(), 'e1a11111-1111-1111-1111-000000000002', 'GUEST', 80.00, 'PEN');
INSERT INTO event_registration (id, event_id, person_id, category, status, payment_status, amount, currency, guests, payment_method, payment_reference,
    submitted_by, source, registered_by, ticket_code, created_at, created_by, version) VALUES
 ('e1c11111-1111-1111-1111-000000000003', 'e1a11111-1111-1111-1111-000000000002', '45111111-1111-1111-1111-000000000002', 'GUEST', 'REGISTERED', 'PENDING',
  80.00, 'PEN', 0, 'TRANSFER', 'OP-2026-001', '41111111-1111-1111-1111-111111111112', 'STAFF', '41111111-1111-1111-1111-111111111112', 'DEMOEVT003',
  now() - interval '2 days', '41111111-1111-1111-1111-111111111112', 0);

-- ---------------------------------------------------------------- FINANZAS Y DONACIONES (M15)
-- Demo contrata los cuatro módulos (Esperanza no: sirve para probar el 403). Dos fondos (uno GENERAL, uno RESTRICTED que solo
-- admite DONATION/OTHER_INCOME) y dos cuentas (caja de la sede central y una cuenta bancaria de toda la organización).
-- Reglas explícitas con los valores por defecto de la migración. Un donante (Pedro, ya persona de la organización) con una
-- promesa activa. Movimientos ya APPROVED de los dos meses anteriores (para probar consolidado/saldo/presupuesto) y un
-- movimiento PENDING de hoy, de Luis (Sede Central) por 800 — por encima del umbral de sede (500): sirve para probar que
-- Luis NO puede aprobarlo (403 thresholdExceeded) y Ana (ORG_ADMIN) sí. El periodo de hace 2 meses queda CLOSED (para
-- probar reabrir con Z) y un presupuesto de MAINTENANCE del mes en curso en 1000: como el chequeo de sobregiro corre AL
-- REGISTRAR (no al aprobar), sirve para probar el aviso del 80 % creando por API un nuevo egreso MAINTENANCE de ~800 en
-- ese mismo fondo/categoría/mes (el PENDING de abajo se sembró directo por SQL, sin pasar por esa validación).
INSERT INTO contract_module (contract_id, module_code) VALUES
 ('31111111-1111-1111-1111-111111111111', 'FIN_MOVEMENTS'),
 ('31111111-1111-1111-1111-111111111111', 'FIN_FUNDS'),
 ('31111111-1111-1111-1111-111111111111', 'FIN_BUDGETS'),
 ('31111111-1111-1111-1111-111111111111', 'FIN_DONORS');

INSERT INTO fin_rules (organization_id, approval_threshold_branch, attachment_threshold, overspend_block, self_approval, updated_at) VALUES
 ('11111111-1111-1111-1111-111111111111', 500.00, 200.00, false, false, now());

INSERT INTO fin_fund (id, organization_id, code, name, type, allowed_categories, currency, status, created_at, created_by) VALUES
 ('f1a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'GEN', 'Fondo General', 'GENERAL', NULL, 'PEN', 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111'),
 ('f1a11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'MIS', 'Misiones', 'RESTRICTED', ARRAY['DONATION','OTHER_INCOME'], 'PEN', 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO fin_account (id, organization_id, branch_id, name, type, currency, opening_balance, status, created_at, created_by) VALUES
 ('f1b11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'Caja Sede Central', 'CASH', 'PEN', 200.00, 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111'),
 ('f1b11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', NULL, 'Cuenta Banco Principal', 'BANK', 'PEN', 5000.00, 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO fin_donor (id, organization_id, person_id, email, created_at, created_by) VALUES
 ('f1d11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'pedro@demo.test', now(), '41111111-1111-1111-1111-111111111111');
INSERT INTO fin_pledge (id, organization_id, donor_id, fund_id, amount, frequency, start_date, status, created_at, created_by) VALUES
 ('f1f11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'f1d11111-1111-1111-1111-000000000001', 'f1a11111-1111-1111-1111-000000000001',
  50.00, 'MONTHLY', current_date - 180, 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111');

-- Periodo de hace 2 meses ya cerrado (para probar reabrir con Z y motivo).
INSERT INTO fin_fiscal_period (id, organization_id, year, month, status, closed_at, closed_by) VALUES
 ('f1e11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111',
  extract(year from current_date - interval '2 months')::int, extract(month from current_date - interval '2 months')::int,
  'CLOSED', now() - interval '20 days', '41111111-1111-1111-1111-111111111111');

INSERT INTO fin_receipt_counter (organization_id, year, last_number) VALUES ('11111111-1111-1111-1111-111111111111', extract(year from current_date)::int, 1);

-- Movimientos ya conciliados de los dos meses anteriores (para saldo/consolidado/presupuesto).
INSERT INTO fin_movement (id, organization_id, branch_id, movement_date, type, category, fund_id, account_id, amount, currency, method, donor_id,
    anonymous, description, receipt_no, status, submitted_by, decided_by, decided_at, created_at, created_by) VALUES
 ('f1c11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  (current_date - interval '2 months')::date, 'INCOME', 'TITHE', 'f1a11111-1111-1111-1111-000000000001', 'f1b11111-1111-1111-1111-000000000001',
  150.00, 'PEN', 'CASH', 'f1d11111-1111-1111-1111-000000000001', false, 'Diezmo de Pedro', 'REC-' || extract(year from current_date)::text || '-000001',
  'APPROVED', '41111111-1111-1111-1111-111111111112', '41111111-1111-1111-1111-111111111111', now() - interval '55 days', now() - interval '55 days', '41111111-1111-1111-1111-111111111112'),
 ('f1c11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  (current_date - interval '2 months')::date, 'EXPENSE', 'MAINTENANCE', 'f1a11111-1111-1111-1111-000000000001', 'f1b11111-1111-1111-1111-000000000002',
  100.00, 'PEN', 'TRANSFER', NULL, false, 'Mantenimiento de local', NULL, 'APPROVED', '41111111-1111-1111-1111-111111111112',
  '41111111-1111-1111-1111-111111111111', now() - interval '54 days', now() - interval '54 days', '41111111-1111-1111-1111-111111111112'),
 ('f1c11111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  (current_date - interval '1 month')::date, 'INCOME', 'OFFERING', 'f1a11111-1111-1111-1111-000000000001', 'f1b11111-1111-1111-1111-000000000001',
  300.00, 'PEN', 'CASH', NULL, true, 'Ofrenda del culto dominical', NULL, 'APPROVED', '41111111-1111-1111-1111-111111111112',
  '41111111-1111-1111-1111-111111111111', now() - interval '25 days', now() - interval '25 days', '41111111-1111-1111-1111-111111111112'),
 ('f1c11111-1111-1111-1111-000000000004', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  (current_date - interval '1 month')::date, 'EXPENSE', 'UTILITIES', 'f1a11111-1111-1111-1111-000000000001', 'f1b11111-1111-1111-1111-000000000002',
  120.00, 'PEN', 'TRANSFER', NULL, false, 'Recibo de luz y agua', NULL, 'APPROVED', '41111111-1111-1111-1111-111111111112',
  '41111111-1111-1111-1111-111111111111', now() - interval '24 days', now() - interval '24 days', '41111111-1111-1111-1111-111111111112');

-- Presupuesto de mantenimiento del mes en curso: 1000; el PENDING de abajo (800) lo deja en 80% al aprobarse.
INSERT INTO fin_budget (id, organization_id, scope, fund_id, category, period, amount, status, created_at, created_by) VALUES
 ('f1e11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'ORG', 'f1a11111-1111-1111-1111-000000000001', 'MAINTENANCE',
  date_trunc('month', current_date)::date, 1000.00, 'APPROVED', now(), '41111111-1111-1111-1111-111111111111');

-- Movimiento PENDING de hoy, sobre el umbral de sede (500): Luis (ORG_BRANCH_ADMIN de Sede Central) no debería poder
-- aprobarlo; Ana (ORG_ADMIN) sí. Trae un comprobante porque 800 >= attachment_threshold (200) [V9].
INSERT INTO fin_movement (id, organization_id, branch_id, movement_date, type, category, fund_id, account_id, amount, currency, method, anonymous,
    description, status, submitted_by, created_at, created_by) VALUES
 ('f1c11111-1111-1111-1111-000000000005', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', current_date, 'EXPENSE',
  'MAINTENANCE', 'f1a11111-1111-1111-1111-000000000001', 'f1b11111-1111-1111-1111-000000000002', 800.00, 'PEN', 'TRANSFER', false,
  'Reparación del techo del templo', 'PENDING', '41111111-1111-1111-1111-111111111112', now(), '41111111-1111-1111-1111-111111111112');
INSERT INTO fin_movement_attachment (id, movement_id, storage_key, filename, uploaded_at, uploaded_by) VALUES
 (gen_random_uuid(), 'f1c11111-1111-1111-1111-000000000005', 'org/11111111-1111-1111-1111-111111111111/finance/f1c11111-1111-1111-1111-000000000005/cotizacion.pdf',
  'cotizacion.pdf', now(), '41111111-1111-1111-1111-111111111112');

-- ---------------------------------------------------------------- INSTALACIONES Y RECURSOS (M16)
-- Demo contrata los dos módulos (Esperanza no: sirve para probar el 403 [M16-T16]). Dos espacios en Sede Central: el
-- "Salón Principal" no exige aprobación (reserva nace CONFIRMED de una vez) y la "Sala de Reuniones" sí (nace PENDING y
-- abre una solicitud en el motor de aprobaciones). Dos ítems de inventario: un proyector (ASSET, con una unidad
-- disponible, listo para asignarse) y paquetes de papel higiénico (CONSUMABLE, ya por debajo de su stock mínimo, para
-- probar la alerta [V15] sin necesidad de registrar un movimiento primero).
INSERT INTO contract_module (contract_id, module_code) VALUES
 ('31111111-1111-1111-1111-111111111111', 'SPACES'),
 ('31111111-1111-1111-1111-111111111111', 'INVENTORY');

INSERT INTO space (id, organization_id, branch_id, name, type_code, capacity, equipment, requires_approval, buffer_minutes, status, created_at, created_by) VALUES
 ('f2a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  'Salón Principal', NULL, 150, '["Proyector","Sonido"]'::jsonb, false, 0, 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111'),
 ('f2a11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  'Sala de Reuniones', NULL, 20, '[]'::jsonb, true, 15, 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO inventory_item (id, organization_id, branch_id, code, name, category_code, kind, unit, quantity, min_stock, location, condition, status,
    created_at, created_by) VALUES
 ('f2b11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  'PROY-01', 'Proyector Epson', NULL, 'ASSET', 'UNIDAD', 1, NULL, 'Almacén Sede Central', 'GOOD', 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111'),
 ('f2b11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  'PAPEL-01', 'Papel higiénico (paquete)', NULL, 'CONSUMABLE', 'PAQUETE', 3, 10, 'Almacén Sede Central', NULL, 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111');

-- ---------------------------------------------------------------- RRHH Y PLANILLA (M17)
-- Demo contrata los tres módulos. Dos fichas de personal ACTIVE en Sede Central (Pedro y María, ya adultos en el seed base) y
-- tres conceptos de planilla típicos de Perú: sueldo básico (100% del sueldo pactado, así el importe de StaffMember.baseSalary
-- se vuelve una línea de boleta en vez de un campo aparte), ONP (deducción del trabajador) y EsSalud (aporte del empleador).
INSERT INTO contract_module (contract_id, module_code) VALUES
 ('31111111-1111-1111-1111-111111111111', 'HR_STAFF'),
 ('31111111-1111-1111-1111-111111111111', 'HR_LEAVE'),
 ('31111111-1111-1111-1111-111111111111', 'HR_PAYROLL');

INSERT INTO payroll_concept (id, organization_id, code, name_es, name_en, kind, calc, value, mandatory, active, sort_order, created_at, created_by) VALUES
 ('f3c11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'SUELDO_BASICO', 'Sueldo básico', 'Base salary', 'EARNING', 'PERCENT_OF_BASE', 100, true, true, 10, now(), '41111111-1111-1111-1111-111111111111'),
 ('f3c11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'ONP', 'ONP (13%)', 'ONP (13%)', 'DEDUCTION', 'PERCENT_OF_BASE', 13, false, true, 20, now(), '41111111-1111-1111-1111-111111111111'),
 ('f3c11111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', 'ESSALUD', 'EsSalud (9%)', 'EsSalud (9%)', 'EMPLOYER_CONTRIBUTION', 'PERCENT_OF_BASE', 9, true, true, 30, now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO staff_member (id, organization_id, branch_id, person_id, position, contract_type, hire_date, base_salary, currency, pay_frequency, status, created_at, created_by) VALUES
 ('f3a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  '45111111-1111-1111-1111-000000000001', 'Pastor principal', 'PLANILLA_INDEFINIDO', '2015-03-01', 3500.00, 'PEN', 'MONTHLY', 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111'),
 ('f3a11111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111',
  '45111111-1111-1111-1111-000000000002', 'Administradora', 'PLANILLA_INDEFINIDO', '2016-06-15', 2200.00, 'PEN', 'MONTHLY', 'ACTIVE', now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO salary_history (id, staff_id, amount, from_date, reason, created_at, created_by) VALUES
 ('f3d11111-1111-1111-1111-000000000001', 'f3a11111-1111-1111-1111-000000000001', 3500.00, '2015-03-01', 'Alta', now(), '41111111-1111-1111-1111-111111111111'),
 ('f3d11111-1111-1111-1111-000000000002', 'f3a11111-1111-1111-1111-000000000002', 2200.00, '2016-06-15', 'Alta', now(), '41111111-1111-1111-1111-111111111111');

-- ---------------------------------------------------------------- PLANTILLAS Y CERTIFICADOS (M18)
-- Una plantilla base (N1, plataforma) de certificado de donativo y una plantilla propia de Demo (N2) para recibos,
-- publicada y marcada por defecto para toda la organización, con un diseño mínimo (texto + QR) que ya pasa [V1]/[V4]/[V6].
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'DOC_TEMPLATES');

INSERT INTO template_base (id, type, name, design, locale, version, status, created_at, created_by) VALUES
 ('f4b11111-1111-1111-1111-000000000001', 'DONATION_CERTIFICATE', 'Certificado de donativo (base)',
  '{"pageSize":"A4","orientation":"landscape","elements":[
     {"type":"TEXT","x":20,"y":30,"width":257,"height":15,"fontSize":22,"align":"center","bold":true,"content":"{{org.displayName}}"},
     {"type":"TEXT","x":20,"y":90,"width":257,"height":12,"fontSize":16,"align":"center","content":"Certificado de donativo N° {{number}}"},
     {"type":"TEXT","x":20,"y":115,"width":257,"height":10,"fontSize":13,"align":"center","content":"Otorgado a {{person.fullName}} por un donativo de {{amount}} durante {{year}}."},
     {"type":"TEXT","x":20,"y":150,"width":257,"height":8,"fontSize":10,"align":"center","content":"Emitido el {{date}}"},
     {"type":"QR","x":240,"y":160,"width":25,"height":25}
   ]}'::jsonb, 'es', 1, 'PUBLISHED', now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO document_template (id, organization_id, branch_id, base_template_id, type, name, design, locale, version, status, is_default, created_at, created_by) VALUES
 ('f4a11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', NULL, NULL, 'RECEIPT', 'Recibo de Demo',
  '{"pageSize":"A4","orientation":"landscape","elements":[
     {"type":"TEXT","x":20,"y":25,"width":257,"height":12,"fontSize":18,"align":"center","bold":true,"content":"{{org.displayName}}"},
     {"type":"TEXT","x":20,"y":45,"width":257,"height":10,"fontSize":14,"align":"center","content":"Recibo N° {{number}} — {{branch.name}}"},
     {"type":"TEXT","x":20,"y":80,"width":257,"height":10,"fontSize":12,"align":"left","content":"Recibí de: {{person.fullName}}"},
     {"type":"TEXT","x":20,"y":95,"width":257,"height":10,"fontSize":12,"align":"left","content":"La suma de: {{amount}}"},
     {"type":"TEXT","x":20,"y":110,"width":257,"height":10,"fontSize":12,"align":"left","content":"Por concepto de: {{concept}}"},
     {"type":"TEXT","x":20,"y":150,"width":100,"height":8,"fontSize":10,"align":"left","content":"Fecha: {{date}}"},
     {"type":"QR","x":240,"y":140,"width":25,"height":25}
   ]}'::jsonb, 'es', 1, 'PUBLISHED', true, now(), '41111111-1111-1111-1111-111111111111');

INSERT INTO signatory (id, organization_id, person_id, title, active, created_at, created_by) VALUES
 ('f4c11111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', '45111111-1111-1111-1111-000000000001', 'Pastor principal', true, now(), '41111111-1111-1111-1111-111111111111');

-- ---------------------------------------------------------------- REPORTES (M20)
-- Demo contrata REPORTS; los reportes de PERSON/ATTENDANCE/FIN_MOVEMENTS/HR_PAYROLL ya están contratados por sus
-- propios módulos (sembrados en secciones anteriores), así que aparecen solos en la galería.
INSERT INTO contract_module (contract_id, module_code) VALUES ('31111111-1111-1111-1111-111111111111', 'REPORTS');
