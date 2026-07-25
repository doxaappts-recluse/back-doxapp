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
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000010','Administración','ADMIN','setting',NULL,1,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000010','Personas','PERSONS','usergroup-add',NULL,2,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000011','Usuarios','USERS','user','/users',1,'ACTIVE','20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000012','Membresías','MEMBERSHIP','idcard','/membership',2,'ACTIVE','20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000013','Servicios Ministeriales','MINISTERIAL_SERVICE','medicine-box','/ministerial_service',3,'ACTIVE','20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000014','Contratos','CONTRACTS','file-text','/contracts',4,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000015','Ministerios','MINISTRY','team','/ministry',5,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000016','Módulos','MODULES','team','/modules',5,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('30000000-0000-0000-0000-000000000001','Tesorería','TREASURY','wallet','/treasury',1,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('30000000-0000-0000-0000-000000000002','Finanzas','FINANCE','dollar','/finance',2,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000001','Dashboard','DASHBOARD','dashboard','/dashboard',1,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000002','Eventos','EVENTS','calendar','/events',6,'ACTIVE',NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000004','Usuarios de Acceso','ACCESS_USERS','user-switch','/access_users',7,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000005','Organizaciones','ORGANIZATIONS','bank','/organizations',8,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000006','Usuarios del Sistema','SYSTEM_USERS','safety','/system-users',9,'ACTIVE','10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);

-- =========================================================
-- PERMISSIONS
-- =========================================================
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555551','VIEW','Ver','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555552','CREATE','Crear','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555553','EDIT','Editar','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO permissions (id,code,name,status,created_at) VALUES ('55555555-5555-5555-5555-555555555554','DELETE','Eliminar','ACTIVE',CURRENT_TIMESTAMP);

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
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,created_at) VALUES ('70000000-0000-0000-0000-000000000001','99999999-9999-9999-9999-999999999999',NULL,'PLAN ENTERPRISE SHARED',499.90,'PEN','2026-01-01','2026-12-31',2,'ACTIVE','ORGANIZATION','SHARED','NEW',CURRENT_TIMESTAMP);

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
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000004','70000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- =========================================================
-- CASO 4
-- ORGANIZACION 1
-- CONTRATO ESPECIFICO PARA SEDE NORTE
-- =========================================================
INSERT INTO contracts (id,organization_id,branch_id,plan_name,price,currency,start_date,end_date,max_licenses,status,scope,distribution_mode,renewal_type,created_at) VALUES ('70000000-0000-0000-0000-000000000003','99999999-9999-9999-9999-999999999999','88888888-8888-8888-8888-888888888888','PLAN NORTE PREMIUM',199.90,'PEN','2026-01-01','2026-12-31',3,'ACTIVE','BRANCH','SHARED','NEW',CURRENT_TIMESTAMP);

-- =========================================================
-- MODULOS EXCLUSIVOS NORTE
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('81000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000001','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('81000000-0000-0000-0000-000000000002','70000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000002','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);


