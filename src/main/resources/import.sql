-- =========================================================
-- ROLES
-- =========================================================
INSERT INTO roles (id, name, value, status, created_at) VALUES ('11111111-1111-1111-1111-111111111111','System Admin','SYSTEM_ADMIN','ACTIVE',NOW());
INSERT INTO roles (id, name, value, status, created_at) VALUES ('22222222-2222-2222-2222-222222222222','Support','SYSTEM_SUPPORT','ACTIVE',NOW());
INSERT INTO roles (id, name, value, status, created_at) VALUES ('33333333-3333-3333-3333-333333333333','Organization Admin','ORG_ADMIN','ACTIVE',NOW());
INSERT INTO roles (id, name, value, status, created_at) VALUES ('44444444-4444-4444-4444-444444444444','Organization User','ORG_USER','ACTIVE',NOW());

-- =========================================================
-- USERS
-- =========================================================
INSERT INTO users (id, name, lastname, created_at, organization_id, role_id) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Master','Admin',NOW(),NULL,'11111111-1111-1111-1111-111111111111');

-- =========================================================
-- CREDENTIALS
-- =========================================================
INSERT INTO credentials (id, username, password, status, user_id, created_at) VALUES ('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1','admin','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',NOW());

-- =========================================================
-- PERMISSIONS
-- =========================================================
INSERT INTO permissions (id, code, name, created_at) VALUES ('55555555-5555-5555-5555-555555555551','VIEW','Ver',NOW());
INSERT INTO permissions (id, code, name, created_at) VALUES ('55555555-5555-5555-5555-555555555552','CREATE','Crear',NOW());
INSERT INTO permissions (id, code, name, created_at) VALUES ('55555555-5555-5555-5555-555555555553','EDIT','Editar',NOW());
INSERT INTO permissions (id, code, name, created_at) VALUES ('55555555-5555-5555-5555-555555555554','DELETE','Eliminar',NOW());
INSERT INTO permissions (id, code, name, created_at) VALUES ('55555555-5555-5555-5555-555555555555','DOWNLOAD','Descargar',NOW());

-- =========================================================
-- MODULES
-- =========================================================
-- =========================
-- ADMINISTRACIÓN (ROOT)
-- =========================
-- =========================
-- ADMINISTRACIÓN (ROOT)
-- =========================
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000010','Administración','ADMIN','setting',NULL,2,'ACTIVE',NULL,NOW());

-- CHILDREN ADMIN
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000011','Módulos','MODULES','appstore','/modules',1,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000012','Organizaciones','ORGANIZATIONS','bank','/organizations',2,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000013','Usuarios del Sistema','SYSTEM_USERS','user','/system-users',3,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000014','Contratos','CONTRACTS','file-text','/contracts',4,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000015','Ministerios','MINISTRY','team','/ministry',5,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000016','Roles Ministeriales','MINISTRY_ROLES','idcard','/ministry_roles',6,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000017','Usuarios de Acceso','ACCESS_USERS','lock','/access_users',7,'ACTIVE','10000000-0000-0000-0000-000000000010',NOW());

-- =========================
-- PERSONAS (ROOT)
-- =========================
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('20000000-0000-0000-0000-000000000010','Personas','PERSONS','usergroup-add',NULL,3,'ACTIVE',NULL,NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('20000000-0000-0000-0000-000000000011','Usuarios','USERS','user','/users',1,'ACTIVE','20000000-0000-0000-0000-000000000010',NOW());

INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('20000000-0000-0000-0000-000000000012','Membresías','MEMBERSHIP','idcard','/membership',2,'ACTIVE','20000000-0000-0000-0000-000000000010',NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('20000000-0000-0000-0000-000000000013','Servicios Ministeriales','MINISTERIAL_SERVICE','medicine-box','/ministerial_service',3,'ACTIVE','20000000-0000-0000-0000-000000000010',NOW());

-- =========================
-- EVENTOS (ROOT)
-- =========================
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('30000000-0000-0000-0000-000000000010','Eventos','EVENTS','calendar',NULL,4,'ACTIVE',NULL,NOW());
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('30000000-0000-0000-0000-000000000011','Gestión de Eventos','EVENT_MANAGEMENT','calendar','/events',1,'ACTIVE','30000000-0000-0000-0000-000000000010',NOW());

-- =========================
-- DASHBOARD (ROOT)
-- =========================
INSERT INTO modules (id, name, code, icon, route, order_num, status, parent_id, created_at) VALUES ('10000000-0000-0000-0000-000000000001','Dashboard','DASHBOARD','home','/dashboard',1,'ACTIVE',NULL,NOW());