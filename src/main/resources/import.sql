-- =========================================================
-- ROLES
-- =========================================================
INSERT INTO roles (id,name,value,status,created_at) VALUES ('11111111-1111-1111-1111-111111111111','System Admin','SYSTEM_ADMIN','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('22222222-2222-2222-2222-222222222222','Support','SYSTEM_SUPPORT','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('33333333-3333-3333-3333-333333333333','Organization Admin','ORG_ADMIN','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('44444444-4444-4444-4444-444444444444','Organization User','ORG_USER','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('55555555-5555-5555-5555-555555555555','Organization Branch Admin','ORG_BRANCH_ADMIN','ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- ORGANIZATION
-- =========================================================
INSERT INTO organizations (id,name,ruc,status,created_at) VALUES ('99999999-9999-9999-9999-999999999999','Iglesia Central','99999999999','ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- BRANCH
-- =========================================================
INSERT INTO branches (id,name,code,is_main,status,organization_id,created_at) VALUES ('88888888-8888-8888-8888-888888888888','Sede Norte','NORTE',true,'ACTIVE','99999999-9999-9999-9999-999999999999',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULES
-- =========================================================
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000010','Administración','ADMIN','setting',NULL,1,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000010','Personas','PERSONS','usergroup-add',NULL,2,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000011','Usuarios','USERS','user','/users',1,'ACTIVE','20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000012','Membresías','MEMBERSHIP','idcard','/membership',2,'ACTIVE','20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000013','Servicios Ministeriales','MINISTERIAL_SERVICE','medicine-box','/ministerial_service',3,'ACTIVE','20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000011','Módulos','MODULES','appstore','/modules',1,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000012','Organizaciones','ORGANIZATIONS','bank','/organizations',2,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000013','Usuarios del Sistema','SYSTEM_USERS','user','/system-users',3,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000014','Contratos','CONTRACTS','file-text','/contracts',4,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000015','Ministerios','MINISTRY','team','/ministry',5,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000016','Roles Ministeriales','MINISTRY_ROLES','idcard','/ministry_roles',6,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000017','Usuarios de Acceso','ACCESS_USERS','lock','/access_users',7,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);

-- =========================================================
-- CONTRACT (POR SEDE)
-- =========================================================
INSERT INTO contracts (id,branch_id,plan_name,price,currency,start_date,end_date,number_users,status,created_at) VALUES ('77777777-aaaa-aaaa-7777-777777777777','88888888-8888-8888-8888-888888888888','PLAN PREMIUM',199.90,'PEN','2026-01-01','2026-12-31',100,'ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- USERS
-- =========================================================
INSERT INTO users (id,name,lastname,created_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Master','Admin',CURRENT_TIMESTAMP);
INSERT INTO users (id,name,lastname,organization_id,branch_id,created_at) VALUES ('11111111-aaaa-bbbb-cccc-111111111111','Carlos','Administrador','99999999-9999-9999-9999-999999999999',NULL,CURRENT_TIMESTAMP);
INSERT INTO users (id,name,lastname,organization_id,branch_id,created_at) VALUES ('22222222-aaaa-bbbb-cccc-111111111111','Pedro','Encargado Norte','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888',CURRENT_TIMESTAMP);
INSERT INTO users (id,name,lastname,organization_id,branch_id,created_at) VALUES ('33333333-aaaa-bbbb-cccc-111111111111','Juan','Usuario','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888',CURRENT_TIMESTAMP);
INSERT INTO users (id,name,lastname,organization_id,branch_id,created_at) VALUES ('77777777-7777-7777-7777-777777777777','Pedro','Gomez','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888',CURRENT_TIMESTAMP);

-- =========================================================
-- USER ACCESS (SEDE)
-- =========================================================
INSERT INTO user_accesses (id,user_id,organization_id,branch_id,role_id,active,created_at) VALUES ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',NULL,NULL,'11111111-1111-1111-1111-111111111111',true,CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,user_id,organization_id,branch_id,role_id,active,created_at) VALUES ('11111111-aaaa-bbbb-cccc-333333333333','11111111-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999999',NULL,'33333333-3333-3333-3333-333333333333',true,CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,user_id,organization_id,branch_id,role_id,active,created_at) VALUES ('22222222-aaaa-bbbb-cccc-333333333333','22222222-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888','55555555-5555-5555-5555-555555555555',true,CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,user_id,organization_id,branch_id,role_id,active,created_at) VALUES ('33333333-aaaa-bbbb-cccc-333333333333','33333333-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888','44444444-4444-4444-4444-444444444444',true,CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,user_id,organization_id,branch_id,role_id,active,created_at) VALUES ('66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888','44444444-4444-4444-4444-444444444444',true,CURRENT_TIMESTAMP);

-- =========================================================
-- CREDENTIALS
-- =========================================================
INSERT INTO credentials (id,username,password,status,user_id,created_at) VALUES ('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1','admin','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,user_id,created_at) VALUES ('11111111-aaaa-bbbb-cccc-222222222222','carlos','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','11111111-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,user_id,created_at) VALUES ('22222222-aaaa-bbbb-cccc-222222222222','pedro.norte','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','22222222-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,user_id,created_at) VALUES ('33333333-aaaa-bbbb-cccc-222222222222','juan.usuario','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','33333333-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);

-- =========================================================
-- PERMISSIONS
-- =========================================================
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555551','VIEW','Ver','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555552','CREATE','Crear','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555553','EDIT','Editar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555554','DELETE','Eliminar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555555','DOWNLOAD','Descargar','ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- CONTRACT MODULES
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('90000000-0000-0000-0000-000000000001','77777777-aaaa-aaaa-7777-777777777777','20000000-0000-0000-0000-000000000010','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('90000000-0000-0000-0000-000000000002','77777777-aaaa-aaaa-7777-777777777777','20000000-0000-0000-0000-000000000011','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('90000000-0000-0000-0000-000000000003','77777777-aaaa-aaaa-7777-777777777777','20000000-0000-0000-0000-000000000012','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('90000000-0000-0000-0000-000000000004','77777777-aaaa-aaaa-7777-777777777777','20000000-0000-0000-0000-000000000013','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('90000000-0000-0000-0000-000000000005','77777777-aaaa-aaaa-7777-777777777777','10000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('90000000-0000-0000-0000-000000000006','77777777-aaaa-aaaa-7777-777777777777','10000000-0000-0000-0000-000000000016','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- =========================================================
-- CONTRACT MODULES PERMISSIONS
-- =========================================================
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('91000000-0000-0000-0000-000000000001','90000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('91000000-0000-0000-0000-000000000002','90000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('91000000-0000-0000-0000-000000000003','90000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('91000000-0000-0000-0000-000000000004','90000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555554',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULES BY USER
-- =========================================================
INSERT INTO user_modules (id,user_id,module_id,status,enabled,created_at) VALUES ('80000000-0000-0000-0000-000000000002','33333333-aaaa-bbbb-cccc-111111111111','20000000-0000-0000-0000-000000000012','ACTIVE',true,CURRENT_TIMESTAMP);
INSERT INTO user_modules (id,user_id,module_id,status,enabled,created_at) VALUES ('80000000-0000-0000-0000-000000000003','33333333-aaaa-bbbb-cccc-111111111111','20000000-0000-0000-0000-000000000013','ACTIVE',true,CURRENT_TIMESTAMP);