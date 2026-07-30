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
INSERT INTO organizations (id,name,ruc,founded_date,status,created_at) VALUES ('99999999-9999-9999-9999-999999999999','Iglesia Central','99999999999',CURRENT_DATE,'ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO organizations (id,name,ruc,founded_date,status,created_at) VALUES ('99999999-9999-9999-9999-999999999998','Iglesia Filial','88888888888',CURRENT_DATE,'ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- BRANCHES
-- =========================================================

--BRANCH ORG 1
INSERT INTO branches (id,name,code,opening_date,is_main,status,organization_id,created_at) VALUES ('88888888-8888-8888-8888-888888888888','Sede Norte','NORTE',CURRENT_DATE,true,'ACTIVE','99999999-9999-9999-9999-999999999999',CURRENT_TIMESTAMP);
INSERT INTO branches (id,name,code,opening_date,is_main,status,organization_id,created_at) VALUES ('88888888-8888-8888-8888-888888888889','Sede Sur','SUR',CURRENT_DATE,false,'ACTIVE','99999999-9999-9999-9999-999999999999',CURRENT_TIMESTAMP);
INSERT INTO branches (id,name,code,opening_date,is_main,status,organization_id,created_at) VALUES ('88888888-8888-8888-8888-888888888890','Sede Este','ESTE',CURRENT_DATE,false,'ACTIVE','99999999-9999-9999-9999-999999999999',CURRENT_TIMESTAMP);
INSERT INTO branches (id,name,code,opening_date,is_main,status,organization_id,created_at) VALUES ('88888888-8888-8888-8888-888888888891','Sede Oeste','OESTE',CURRENT_DATE,false,'ACTIVE','99999999-9999-9999-9999-999999999999',CURRENT_TIMESTAMP);
--BRANCH ORG 2
INSERT INTO branches (id,name,code,opening_date,is_main,status,organization_id,created_at) VALUES ('99988888-8888-8888-8888-888888888890','Sede Este Filial','ESTE_FILIAL',CURRENT_DATE,true,'ACTIVE','99999999-9999-9999-9999-999999999998',CURRENT_TIMESTAMP);
INSERT INTO branches (id,name,code,opening_date,is_main,status,organization_id,created_at) VALUES ('99988888-8888-8888-8888-888888888891','Sede Oeste Filial','OESTE_FILIAL',CURRENT_DATE,false,'ACTIVE','99999999-9999-9999-9999-999999999998',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULES
-- =========================================================
-- Columnas de visibilidad (visible_system, visible_org_admin,
-- visible_branch_admin, visible_user) seteadas desde la
-- creación, según quién puede llegar a usar cada módulo:
--
-- ADMIN/PERSONS            : grupos, visibles para todos.
-- USERS (Personas)         : ORG_ADMIN/ORG_BRANCH_ADMIN (no SYSTEM).
-- MEMBERSHIP / MINIST. SVC : SYSTEM + ORG_ADMIN/ORG_BRANCH_ADMIN.
-- CONTRACTS/MINISTRY/      : exclusivos de SYSTEM.
--   MODULES/ORGANIZATIONS/
--   SYSTEM_USERS
-- ACCESS_USERS             : ORG_ADMIN/ORG_BRANCH_ADMIN (no SYSTEM,
--                            no delegable a ORG_USER).
-- ORG_ADMIN_BRANCH         : SYSTEM + ORG_ADMIN/ORG_BRANCH_ADMIN,
--                            no delegable a ORG_USER.
-- DASHBOARD                : sin restricción particular, visible
--                            para todos.
-- EVENTS                   : ORG_ADMIN/ORG_BRANCH_ADMIN (no SYSTEM,
--                            no delegable a ORG_USER).
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000010','Administración','ADMIN','setting',NULL,1,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000010','Personas','PERSONS','usergroup-add',NULL,2,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000011','Usuarios','USERS','user','/users',1,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000012','Membresías','MEMBERSHIP','idcard','/membership',2,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000013','Servicios Ministeriales','MINISTERIAL_SERVICE','medicine-box','/ministerial_service',3,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000014','Contratos','CONTRACTS','file-text','/contracts',4,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000015','Ministerios','MINISTRY','team','/ministry',5,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000016','Módulos','MODULES','team','/modules',5,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000001','Dashboard','DASHBOARD','dashboard','/dashboard',1,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000002','Eventos','EVENTS','calendar','/events',6,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000004','Usuarios de Acceso','ACCESS_USERS','user-switch','/access_users',7,'ACTIVE',false,true,true,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000005','Organizaciones','ORGANIZATIONS','bank','/organizations',8,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000006','Usuarios del Sistema','SYSTEM_USERS','safety','/system-users',9,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000007','Accesos Administrativos Organizacionales','ORG_ADMIN_BRANCH','crown','/org-admin-branch',10,'ACTIVE',true,true,true,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);

-- =========================================================
-- PERMISSIONS
-- =========================================================
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555551','VIEW','Ver','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555552','CREATE','Crear','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555553','EDIT','Editar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555554','DELETE','Eliminar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555555','ENABLE','Habilitar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555556','DISABLE','Inhabilitar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555557','ACTIVATE','Activar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555558','DEACTIVATE','Desactivar','ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- PERSONS ADMIN
-- =========================================================
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('99999999-aaaa-bbbb-cccc-999999999999','Administrador','Sistema Global','00000000','MALE','SINGLE',CURRENT_TIMESTAMP);
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('99999999-aaaa-bbbb-cccc-999999999998','Soporte','Sistema','00000001','MALE','SINGLE',CURRENT_TIMESTAMP);

-- =========================================================
-- PERSONS ORGANIZACION 1
-- =========================================================
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('11111111-aaaa-bbbb-cccc-111111111111','Carlos','Administrador Central','00000002','MALE','SINGLE',CURRENT_TIMESTAMP);
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('33333333-aaaa-bbbb-cccc-111111111111','Juan','Usuario Norte','00000003','MALE','SINGLE',CURRENT_TIMESTAMP);
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('44444444-aaaa-bbbb-cccc-111111111111','Maria','Usuario Sur','00000004','MALE','SINGLE',CURRENT_TIMESTAMP);

-- =========================================================
-- PERSONS ORGANIZACION 2
-- =========================================================
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('55555555-aaaa-bbbb-cccc-111111111111','Pedro','Administrador Filial','00000005','MALE','SINGLE',CURRENT_TIMESTAMP);
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('66666666-aaaa-bbbb-cccc-111111111111','Luis','Usuario Este','00000006','MALE','SINGLE',CURRENT_TIMESTAMP);
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('77777777-aaaa-bbbb-cccc-111111111111','Ana','Usuario Oeste','00000007','MALE','SINGLE',CURRENT_TIMESTAMP);
INSERT INTO persons (id,name,lastname,dni,sex,marital_status,created_at) VALUES ('88888888-aaaa-bbbb-cccc-111111111111','Sofia','Usuario Oeste 2','00000008','MALE','SINGLE',CURRENT_TIMESTAMP);

-- =========================================================
-- PERSON BRANCH
-- =========================================================
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000001-0000-0000-0000-000000000001','11111111-aaaa-bbbb-cccc-111111111111','88888888-8888-8888-8888-888888888888','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000002-0000-0000-0000-000000000002','33333333-aaaa-bbbb-cccc-111111111111','88888888-8888-8888-8888-888888888888','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000003-0000-0000-0000-000000000003','44444444-aaaa-bbbb-cccc-111111111111','88888888-8888-8888-8888-888888888889','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000004-0000-0000-0000-000000000004','55555555-aaaa-bbbb-cccc-111111111111','99988888-8888-8888-8888-888888888890','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000005-0000-0000-0000-000000000005','66666666-aaaa-bbbb-cccc-111111111111','99988888-8888-8888-8888-888888888890','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000006-0000-0000-0000-000000000006','77777777-aaaa-bbbb-cccc-111111111111','99988888-8888-8888-8888-888888888891','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);
INSERT INTO person_branches (id,person_id,branch_id,status,start_date,created_at) VALUES ('b0000007-0000-0000-0000-000000000007','88888888-aaaa-bbbb-cccc-111111111111','99988888-8888-8888-8888-888888888891','ACTIVE',CURRENT_DATE,CURRENT_TIMESTAMP);

-- =========================================================
-- CREDENTIALS
-- =========================================================
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c9999999-0000-0000-0000-000000000999','admin','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','99999999-aaaa-bbbb-cccc-999999999999',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c9999999-0000-0000-0000-000000000998','support','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','99999999-aaaa-bbbb-cccc-999999999998',CURRENT_TIMESTAMP);

INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000001-0000-0000-0000-000000000001','carlos','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','11111111-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000002-0000-0000-0000-000000000002','juan.norte','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','33333333-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000003-0000-0000-0000-000000000003','maria.sur','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','44444444-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);

INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000004-0000-0000-0000-000000000004','pedro.filial','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','55555555-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000005-0000-0000-0000-000000000005','luis.este','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','66666666-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000006-0000-0000-0000-000000000006','ana.oeste','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','77777777-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c0000007-0000-0000-0000-000000000007','sofia.oeste','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','88888888-aaaa-bbbb-cccc-111111111111',CURRENT_TIMESTAMP);

-- =========================================================
-- USER ACCESS
-- =========================================================

-- SYSTEM ADMIN
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a9999999-0000-0000-0000-000000000999','99999999-aaaa-bbbb-cccc-999999999999',NULL,NULL,'11111111-1111-1111-1111-111111111111','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a9999999-0000-0000-0000-000000000998','99999999-aaaa-bbbb-cccc-999999999998',NULL,NULL,'22222222-2222-2222-2222-222222222222','ACTIVE',CURRENT_TIMESTAMP);

-- ORG
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000001-0000-0000-0000-000000000001','11111111-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999999',NULL,'33333333-3333-3333-3333-333333333333','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000002-0000-0000-0000-000000000002','33333333-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888','44444444-4444-4444-4444-444444444444','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000003-0000-0000-0000-000000000003','44444444-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888889','44444444-4444-4444-4444-444444444444','ACTIVE',CURRENT_TIMESTAMP);

INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000004-0000-0000-0000-000000000004','55555555-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999998',NULL,'33333333-3333-3333-3333-333333333333','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000005-0000-0000-0000-000000000005','66666666-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999998','99988888-8888-8888-8888-888888888890','44444444-4444-4444-4444-444444444444','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000006-0000-0000-0000-000000000006','77777777-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999998','99988888-8888-8888-8888-888888888891','44444444-4444-4444-4444-444444444444','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a0000007-0000-0000-0000-000000000007','88888888-aaaa-bbbb-cccc-111111111111','99999999-9999-9999-9999-999999999998','99988888-8888-8888-8888-888888888891','44444444-4444-4444-4444-444444444444','ACTIVE',CURRENT_TIMESTAMP);

-- =========================================================
-- CONTRACTS
-- =========================================================
-- =========================================================
-- CASO 1
-- ORGANIZACION 1
-- CONTRATO ORGANIZATION SHARED
-- REPLACED por el CASO 5 (mismo org+scope no puede tener 2
-- contratos ACTIVE solapados a la vez, ver validateNoOverlap en
-- ContractServiceImpl): este vivió todo 2025 y en 2026 lo
-- reemplazó PLAN CENTRAL ADMIN (CASO 5, previous_contract_id
-- apunta acá).
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,created_at) VALUES ('70000000-0000-0000-0000-000000000001','99999999-9999-9999-9999-999999999999',NULL,'PLAN ENTERPRISE SHARED',499.90,'PEN','2025-01-01','2025-12-31',2,'REPLACED','ORGANIZATION','SHARED','NEW',CURRENT_TIMESTAMP);

-- =========================================================
-- CASO 2
-- ORGANIZACION 2
-- CONTRATO ORGANIZATION ALLOCATED
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,created_at) VALUES ('70000000-0000-0000-0000-000000000002','99999999-9999-9999-9999-999999999998',NULL,'PLAN ENTERPRISE DISTRIBUTED',599.90,'PEN','2026-01-01','2026-12-31',3,'ACTIVE','ORGANIZATION','ALLOCATED','NEW',CURRENT_TIMESTAMP);

-- =========================================================
-- DISTRIBUCION CASO 2
-- ORGANIZACION 2
-- =========================================================
INSERT INTO contract_branch_licenses (id,contract_id,branch_id,allocated_licenses,created_at) VALUES ('71000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000002','99988888-8888-8888-8888-888888888890',1,CURRENT_TIMESTAMP);
INSERT INTO contract_branch_licenses (id,contract_id,branch_id,allocated_licenses,created_at) VALUES ('71000000-0000-0000-0000-000000000002','70000000-0000-0000-0000-000000000002','99988888-8888-8888-8888-888888888891',2,CURRENT_TIMESTAMP);

-- =========================================================
-- CASO 3
-- ORGANIZACION 2
-- MODULOS PARA TODAS LAS SEDES
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000010','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000002','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000012','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000003','70000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- =========================================================
-- PERMISOS POR MODULO - CASO 3 (ORGANIZACION 2)
-- VIEW=...551 CREATE=...552 EDIT=...553 DELETE=...554
-- =========================================================
-- Personas (agrupador, no se puede asignar directo, pero el contrato igual la habilita)
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000001','80000000-0000-0000-0000-000000000001','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
-- Membresías: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000002','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000003','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000004','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Ministerios: ver, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000005','80000000-0000-0000-0000-000000000003','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000006','80000000-0000-0000-0000-000000000003','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- CASO 4
-- ORGANIZACION 1
-- CONTRATO ESPECIFICO PARA SEDE NORTE
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,created_at) VALUES ('70000000-0000-0000-0000-000000000003','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888','PLAN NORTE PREMIUM',199.90,'PEN','2026-01-01','2026-12-31',3,'ACTIVE','BRANCH','SHARED','NEW',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULOS EXCLUSIVOS NORTE
-- Nota: TREASURY/FINANCE se eliminaron del catálogo (no
-- tienen ruta en app.routes.ts), por lo que este contrato
-- queda sin módulos exclusivos asignados.
-- =========================================================

-- =========================================================
-- CASO 5
-- ORGANIZACION 1 (Iglesia Central) - CARLOS
-- CONTRATO ORGANIZATION, con módulos reales (a diferencia
-- del CASO 1, que no tiene ningún contract_modules asignado).
-- Sucesor de CASO 1 (previous_contract_id + renewal_type=UPGRADE):
-- mismo org+scope, así que no puede coexistir ACTIVE con CASO 1,
-- que por eso quedó REPLACED en 2025.
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,previous_contract_id,created_at) VALUES ('70000000-0000-0000-0000-000000000004','99999999-9999-9999-9999-999999999999',NULL,'PLAN CENTRAL ADMIN',349.90,'PEN','2026-01-01','2026-12-31',5,'ACTIVE','ORGANIZATION','SHARED','UPGRADE','70000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULOS - CASO 5 (ORGANIZACION 1)
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000004','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000010','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000005','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000012','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000006','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000013','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000007','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000002','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000008','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000004','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000009','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000007','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- =========================================================
-- PERMISOS POR MODULO - CASO 5 (ORGANIZACION 1)
-- VIEW=...551 CREATE=...552 EDIT=...553 DELETE=...554
-- =========================================================
-- Personas (agrupador, no se puede asignar directo, pero el contrato igual la habilita)
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000007','80000000-0000-0000-0000-000000000004','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
-- Membresías: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000008','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000009','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000010','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Servicios Ministeriales: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000011','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000012','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000013','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Eventos: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000014','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000015','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000016','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Usuarios de Acceso: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000017','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000018','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000019','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Accesos Administrativos Organizacionales: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000020','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000021','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000022','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- CASO 6
-- ORGANIZACION 1 - SEDE SUR (otra sede de la misma org de Carlos)
-- CONTRATO BRANCH, con módulos reales
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,created_at) VALUES ('70000000-0000-0000-0000-000000000005','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888889','PLAN SEDE SUR',149.90,'PEN','2026-01-01','2026-12-31',2,'ACTIVE','BRANCH','SHARED','NEW',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULOS - CASO 6 (SEDE SUR)
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000010','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000002','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000011','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000012','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- =========================================================
-- PERMISOS POR MODULO - CASO 6 (SEDE SUR)
-- =========================================================
-- Eventos: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000023','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000024','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000025','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Membresías: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000026','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000027','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000028','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);



-- =========================================================
-- MIRROR: ENABLE/DISABLE/ACTIVATE/DEACTIVATE donde ya hay EDIT
-- Los contract_modules que ya conceden EDIT (contrato ya
-- funcional/probado) reciben tambien los 4 permisos nuevos,
-- para que los botones de estado no desaparezcan de golpe
-- en pantallas/usuarios que ya funcionaban. Quedan como filas
-- independientes: un admin puede revocar ENABLE sin tocar EDIT.
-- =========================================================
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000029','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000030','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000031','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000032','80000000-0000-0000-0000-000000000002','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000033','80000000-0000-0000-0000-000000000003','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000034','80000000-0000-0000-0000-000000000003','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000035','80000000-0000-0000-0000-000000000003','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000036','80000000-0000-0000-0000-000000000003','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000037','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000038','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000039','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000040','80000000-0000-0000-0000-000000000005','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000041','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000042','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000043','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000044','80000000-0000-0000-0000-000000000006','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000045','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000046','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000047','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000048','80000000-0000-0000-0000-000000000007','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000049','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000050','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000051','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000052','80000000-0000-0000-0000-000000000008','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000053','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000054','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000055','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000056','80000000-0000-0000-0000-000000000009','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000057','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000058','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000059','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000060','80000000-0000-0000-0000-000000000010','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000061','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000062','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000063','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555557',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000064','80000000-0000-0000-0000-000000000011','55555555-5555-5555-5555-555555555558',CURRENT_TIMESTAMP);
