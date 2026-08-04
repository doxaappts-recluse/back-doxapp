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
-- ADMIN/PERSONS/           : grupos, visibles para todos.
--   TRANSFER_MANAGEMENT
-- USERS (Personas)         : ORG_ADMIN/ORG_BRANCH_ADMIN (no SYSTEM).
-- MEMBERSHIP / MINIST. SVC /
--   BAPTISM / MARRIAGE     : SYSTEM + ORG_ADMIN/ORG_BRANCH_ADMIN.
-- SMALL_GROUP               : SYSTEM + ORG_ADMIN/ORG_BRANCH_ADMIN,
--                              SI delegable a ORG_USER (con permiso
--                              CREATE puede crear su propio grupo; con
--                              EDIT solo gestiona el que él mismo creó
--                              — ver SmallGroupAccessGuard). Los
--                              participantes de un grupo NO tienen
--                              por qué ser miembros ni tener registro
--                              alguno (SmallGroupMember admite
--                              guestName/guestPhone en vez de Person).
-- BRANCH_TRANSFER /
--   VISIBILITY_REQUEST /
--   MY_VISIBILITY_REQUEST  : SYSTEM + ORG_ADMIN/ORG_BRANCH_ADMIN,
--                            hijos de TRANSFER_MANAGEMENT (no de
--                            PERSONS). VISIBILITY_REQUEST = revisar/
--                            aprobar solicitudes ENTRANTES sobre la
--                            data de mi sede; MY_VISIBILITY_REQUEST =
--                            crear y hacer seguimiento a MIS
--                            solicitudes SALIENTES.
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
-- FINANCE_MANAGEMENT       : grupo padre "Finanzas Institucionales"
--   (route=NULL, mismo patrón que TRANSFER_MANAGEMENT). No se
--   otorga como contract_module propio — igual que
--   TRANSFER_MANAGEMENT, el padre no requiere grant explícito,
--   solo sus hijos.
-- FINANCIAL_MOVEMENT       : hijo de FINANCE_MANAGEMENT ("Movimientos").
--                            ORG_ADMIN/ORG_BRANCH_ADMIN (no SYSTEM),
--                            SI delegable a ORG_USER (con permiso
--                            CREATE puede registrar movimientos de
--                            su propia sede, quedan PENDING hasta
--                            que un admin los aprueba — ver
--                            FinancialAccessGuard).
-- FINANCIAL_FUND            : hijo de FINANCE_MANAGEMENT ("Fondos"),
--                            catálogo de fondos (Fondo General,
--                            Construcción, Misiones, etc.). Solo
--                            ORG_ADMIN gestiona el catálogo (crear/
--                            editar/habilitar/inhabilitar), por eso
--                            visible_branch_admin=visible_user=false
--                            — branch admin/org user solo lo
--                            consumen como dropdown dentro del
--                            formulario de Movimientos
--                            (FinancialFundService.listActive() no
--                            exige el permiso de este módulo).
-- FINANCIAL_DONOR           : hijo de FINANCE_MANAGEMENT ("Donantes"),
--                            solo lectura — agrupa los movimientos
--                            INCOME/APROBADOS por persona (más un
--                            bucket "anónimo" para los registrados
--                            sin personId). Mismo alcance que
--                            FINANCIAL_MOVEMENT (ORG_ADMIN/
--                            ORG_BRANCH_ADMIN, delegable a ORG_USER),
--                            solo permiso VIEW — no hay crear/editar/
--                            eliminar acá, un donante no es una
--                            entidad propia (ver
--                            FinancialMovementServiceImpl.donors()).
-- DOCUMENT_TEMPLATES        : hijo de ADMIN ("Plantillas de
--                            Documentos"), catálogo de plantillas
--                            RAW (imagen) de certificados (bautizo,
--                            donación, otros) — es 100% un asunto
--                            de cada organización/sede: SYSTEM NO
--                            gestiona ni ve este módulo
--                            (visible_system=false), a diferencia
--                            del resto de módulos donde SYSTEM
--                            siempre tiene bypass (ver
--                            DocumentTemplateAccessGuard). Org admin
--                            gestiona toda plantilla de su
--                            organización (org-wide o de cualquier
--                            sede, elige libremente). Branch admin y
--                            un org user delegado (módulo asignado +
--                            permiso CREATE/EDIT) solo gestionan la
--                            plantilla de SU propia sede — la sede
--                            sale siempre de su contexto, nunca la
--                            eligen ni pueden dejarla org-wide (mismo
--                            criterio de delegación que EVENTS /
--                            SMALL_GROUP). El backend NUNCA compone
--                            el documento final: solo sube/sirve la
--                            plantilla cruda vía URL firmada (ver
--                            DocumentTemplateService.download()),
--                            que sí es consumible por cualquier
--                            usuario con acceso al módulo consumidor
--                            (p.ej. Donantes) sin requerir permiso de
--                            este módulo, igual que
--                            FinancialFundService.listActive().
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000010','Administración','ADMIN','setting',NULL,1,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000010','Personas','PERSONS','usergroup-add',NULL,2,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000011','Usuarios','USERS','user','/users',1,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000012','Membresías','MEMBERSHIP','idcard','/membership',2,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000013','Servicios Ministeriales','MINISTERIAL_SERVICE','medicine-box','/ministerial_service',3,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000014','Bautizo','BAPTISM','solution','/baptism',4,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000018','Matrimonios','MARRIAGE','heart','/marriage',5,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000019','Grupos Pequeños','SMALL_GROUP','cluster','/small-group',6,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
-- PASTORAL_FOLLOWUP         : visible_system=FALSE — CORREGIDO: este
--                              módulo (junto a VISITOR y Asistencia a
--                              Cultos) es el paquete comercial "CRM
--                              Pastoral" del documento de precios, NO
--                              un módulo base/gratuito; antes se
--                              seedeaba con visible_system=true igual
--                              que Membresía/Bautizo/Matrimonio, lo
--                              cual le daba bypass a SYSTEM sobre datos
--                              pastorales de la organización — mismo
--                              criterio ahora que Academia Bíblica en
--                              adelante (ver DocumentTemplateAccessGuard,
--                              PastoralFollowUpAccessGuard.isAdmin()).
--                              ORG_ADMIN/ORG_BRANCH_ADMIN siempre, SI
--                              delegable a ORG_USER (con CREATE
--                              registra contactos/peticiones de
--                              personas de su propia sede; con EDIT
--                              gestiona cualquiera de su sede, no
--                              solo las que él mismo creó). Genérico
--                              a CUALQUIER Person (miembro, visitante
--                              o cualquier otra): historial de
--                              contactos (FollowUpContact), peticiones
--                              de oración (PrayerRequest) y el líder
--                              asignado (Person.assignedLeader) — no
--                              es exclusivo del módulo Visitantes, que
--                              lo usa como base. Asignar/reasignar
--                              líder SIEMPRE requiere org admin/branch
--                              admin, nunca delegable. El detalle por
--                              persona (/pastoral-followup/:personId)
--                              se consume desde otras pantallas
--                              (Usuarios, Visitantes); la ruta del
--                              módulo en el sidebar apunta al listado
--                              de Miembros Inactivos, ver
--                              InactiveMemberSpecification. Asistencia
--                              a Cultos (ver CHURCH_SERVICE/
--                              ChurchServiceAttendance) reutiliza este
--                              mismo módulo/permisos, sin línea de
--                              precio propia.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000020','Seguimiento Pastoral','PASTORAL_FOLLOWUP','phone','/pastoral-followup/inactive-members',7,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
-- VISITOR                   : visible_system=FALSE — CORREGIDO, mismo
--                              motivo que PASTORAL_FOLLOWUP (paquete
--                              comercial "CRM Pastoral", no base/
--                              gratuito). ORG_ADMIN/ORG_BRANCH_ADMIN
--                              siempre, SI delegable a ORG_USER con
--                              CREATE/EDIT. Datos específicos de "ser
--                              visitante" (cómo llegó, etapa de
--                              consolidación, conversión a miembro)
--                              sobre una Person que ya existe (creada
--                              vía Usuarios, igual patrón que Bautizo/
--                              Matrimonio). El seguimiento en sí
--                              (contactos, líder, peticiones de
--                              oración) vive en PASTORAL_FOLLOWUP, no
--                              acá. Al convertir a miembro se abre una
--                              Membership nueva con reason=MEMBERSHIP
--                              (ver VisitorServiceImpl.convertToMember)
--                              — MembershipReason.VISITOR ya no existe.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000021','Visitantes','VISITOR','smile','/visitor',8,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
-- BIBLE_ACADEMY              : visible_system=FALSE a propósito —
--                              SYSTEM no debe interferir en la
--                              Academia Bíblica de una organización
--                              (pedido explícito del usuario, ver
--                              BibleAcademyAccessGuard, mismo
--                              criterio que DOCUMENT_TEMPLATES). La
--                              malla curricular (BibleCurriculum,
--                              org-wide, una sola ACTIVE a la vez) es
--                              EXCLUSIVA de org admin — ni branch
--                              admin ni delegado pueden tocarla.
--                              Cursos extra, dictados (BibleClass) y
--                              matrículas SÍ son delegables a
--                              ORG_USER con CREATE/EDIT, acotados a
--                              su propia sede. Un solo módulo/permiso
--                              cubre las 3 pantallas (malla, cursos
--                              extra, dictados) — la ruta apunta al
--                              listado de Dictados (uso diario más
--                              frecuente); las otras 2 pantallas se
--                              alcanzan con la navegación interna que
--                              agrega bible-*-list.component.html.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000022','Academia Bíblica','BIBLE_ACADEMY','read','/bible-academy/class',9,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
-- FAMILY_GROUP                : visible_system=FALSE — mismo criterio
--                              que Academia Bíblica en adelante,
--                              nuevos módulos operativos no dan
--                              bypass a SYSTEM. Realce GRATUITO de
--                              Gestión de Miembros (agrupa Person con
--                              rol: jefe de hogar/cónyuge/hijo/otro),
--                              no es un módulo comercial propio — no
--                              está en el documento de precios, a
--                              diferencia de Matrimonios/CRM Pastoral.
--                              SIEMPRE opera sobre Person que ya
--                              existe (sin invitados de solo nombre,
--                              a diferencia de Grupos Pequeños) y cada
--                              Person pertenece a un solo grupo a la
--                              vez. VER/CREAR/EDITAR delegables a
--                              ORG_USER (ver FamilyGroupAccessGuard).
--                              Al registrar un Matrimonio con al
--                              menos un cónyuge vinculado a Person, se
--                              crea/actualiza el grupo automáticamente
--                              (cónyuge1=jefe de hogar,
--                              cónyuge2=cónyuge) — hijos/otros
--                              parientes siempre se agregan a mano.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000023','Grupo Familiar','FAMILY_GROUP','apartment','/family-group',10,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
-- SPACE_RESERVATION           : visible_system=FALSE — mismo criterio
--                              que Academia Bíblica/Plantillas de
--                              Documentos, nuevos módulos operativos
--                              no dan bypass a SYSTEM. Catálogo de
--                              espacios (ReservableSpace) exclusivo
--                              de org admin/branch admin de su sede,
--                              NO delegable. Reservas (SpaceReservation)
--                              SÍ delegables a ORG_USER con CREATE/EDIT,
--                              acotadas a la sede del espacio — ver
--                              SpaceReservationAccessGuard. La ruta
--                              apunta al listado de reservas (uso
--                              diario); el catálogo de espacios se
--                              alcanza con la navegación interna de
--                              esa pantalla.
--
-- REAGRUPACIÓN (módulos padre/hijo): Reservas de Espacios e
-- Inventario pasan a colgar del nuevo padre "Operaciones"
-- (OPERATIONS) — cambio puramente visual/organizativo, sus permisos y
-- AccessGuards NO cambian (siguen siendo hojas independientes con su
-- propio code). Ver también el padre "Recursos Humanos" más abajo,
-- que SÍ splitea permisos (caso distinto).
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('50000000-0000-0000-0000-000000000003','Operaciones','OPERATIONS','appstore',NULL,13,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000014','Reservas de Espacios','SPACE_RESERVATION','home','/space-reservation/reservations',1,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000003',CURRENT_TIMESTAMP);
-- INVENTORY                   : visible_system=FALSE — mismo criterio
--                              que Reservas de Espacios/Academia
--                              Bíblica, nuevos módulos operativos no
--                              dan bypass a SYSTEM. Catálogo de ítems
--                              (InventoryItem) exclusivo de org admin/
--                              branch admin de su sede, NO delegable.
--                              Movimientos (InventoryMovement) y
--                              asignaciones de custodia
--                              (InventoryAssignment) SÍ delegables a
--                              ORG_USER con CREATE/EDIT, acotados a la
--                              sede del ítem — ver InventoryAccessGuard.
--                              La ruta apunta al listado de ítems (uso
--                              diario); movimientos/custodia se
--                              alcanzan con la navegación interna de
--                              esa pantalla.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000015','Inventario','INVENTORY','database','/inventory/items',2,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000003',CURRENT_TIMESTAMP);
-- HR                          : visible_system=FALSE. REESTRUCTURADO:
--                              este módulo (id ...016) ahora es
--                              únicamente el PADRE "Recursos Humanos"
--                              (route=NULL, ya no se le asignan
--                              permisos directamente — un módulo con
--                              hijos no es asignable a contratos, ver
--                              ContractModuleServiceImpl.getLeafModuleOrThrow).
--                              Se parte en 3 módulos hijo, cada uno
--                              con su propio code/permiso delegable
--                              independiente — ver HrAccessGuard
--                              (permissions(String moduleCode)) y los 3
--                              MODULE_CODE nuevos:
--                              - STAFF_MEMBER (Ficha de Empleado):
--                                exclusiva de org admin/branch admin de
--                                su sede, NO delegable a ORG_USER para
--                                crear/editar (sí para solo ver, con
--                                permiso VIEW).
--                              - LEAVE_REQUEST (Vacaciones y Permisos,
--                                incluye aprobar/rechazar): delegable a
--                                ORG_USER con CREATE/EDIT, acotado a la
--                                sede del empleado.
--                              - PAYROLL (Planilla): delegable a
--                                ORG_USER con CREATE/EDIT, acotado a la
--                                sede del empleado.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000016','Recursos Humanos','HR','idcard',NULL,15,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000018','Ficha de Empleado','STAFF_MEMBER','idcard','/hr/staff',1,'ACTIVE',false,true,true,true,'40000000-0000-0000-0000-000000000016',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000019','Vacaciones y Permisos','LEAVE_REQUEST','calendar','/hr/leave-requests',2,'ACTIVE',false,true,true,true,'40000000-0000-0000-0000-000000000016',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000020','Planilla','PAYROLL','pay-circle','/hr/payroll',3,'ACTIVE',false,true,true,true,'40000000-0000-0000-0000-000000000016',CURRENT_TIMESTAMP);
-- Reportes Avanzados: Dashboard Ejecutivo. Sin delegación a
-- ORG_USER (visible_user=false) y sin bypass SYSTEM — ver
-- AdvancedReportsAccessGuard. Org admin ve toda la organización,
-- branch admin solo su sede.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000017','Reportes Avanzados','ADVANCED_REPORTS','fund','/advanced-reports',16,'ACTIVE',false,true,true,false,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('50000000-0000-0000-0000-000000000001','Gestión de Traslados','TRANSFER_MANAGEMENT','swap',NULL,7,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000015','Traslados de Sede','BRANCH_TRANSFER','swap','/branch-transfer',1,'ACTIVE',true,true,true,true,'50000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000016','Solicitudes de Visibilidad','VISIBILITY_REQUEST','eye','/visibility-requests',2,'ACTIVE',true,true,true,true,'50000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000017','Mis Solicitudes','MY_VISIBILITY_REQUEST','solution','/my-visibility-requests',3,'ACTIVE',true,true,true,true,'50000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000014','Contratos','CONTRACTS','file-text','/contracts',4,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000015','Ministerios','MINISTRY','team','/ministry',5,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000016','Módulos','MODULES','team','/modules',5,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000001','Dashboard','DASHBOARD','dashboard','/dashboard',1,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000002','Eventos','EVENTS','calendar','/events',6,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000004','Usuarios de Acceso','ACCESS_USERS','user-switch','/access_users',7,'ACTIVE',false,true,true,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000005','Organizaciones','ORGANIZATIONS','bank','/organizations',8,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000006','Usuarios del Sistema','SYSTEM_USERS','safety','/system-users',9,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000007','Accesos Administrativos Organizacionales','ORG_ADMIN_BRANCH','crown','/org-admin-branch',10,'ACTIVE',true,true,true,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('50000000-0000-0000-0000-000000000002','Finanzas Institucionales','FINANCE_MANAGEMENT','dollar',NULL,8,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000008','Movimientos','FINANCIAL_MOVEMENT','transaction','/financial-movements',1,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000009','Fondos','FINANCIAL_FUND','gold','/financial-funds',2,'ACTIVE',false,true,false,false,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000010','Donantes','FINANCIAL_DONOR','contacts','/financial-donors',3,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000011','Plantillas de Documentos','DOCUMENT_TEMPLATES','file-image','/document-templates',11,'ACTIVE',false,true,true,true,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
-- FINANCIAL_BUDGET           : hijo de FINANCE_MANAGEMENT
--                            ("Presupuestos"), monto meta por
--                            sede/fondo/categoría/mes — mismo
--                            criterio que FINANCIAL_FUND: solo
--                            ORG_ADMIN gestiona el catálogo (crear/
--                            editar/habilitar/inhabilitar),
--                            visible_branch_admin=visible_user=false.
--                            El avance real (gastado/recaudado) se
--                            calcula on-demand contra
--                            FinancialMovement, nunca se persiste
--                            (ver FinancialBudgetServiceImpl.progress).
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000012','Presupuestos','FINANCIAL_BUDGET','pie-chart','/financial-budgets',4,'ACTIVE',false,true,false,false,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
-- FINANCIAL_CASH_REGISTER   : hijo de FINANCE_MANAGEMENT ("Caja
--                            Diaria"), apertura/cierre con arqueo
--                            por sede y día — mismo alcance que
--                            FINANCIAL_MOVEMENT (ORG_ADMIN/
--                            ORG_BRANCH_ADMIN, SI delegable a
--                            ORG_USER con permiso CREATE, quien solo
--                            puede cerrar la caja que él mismo abrió).
--                            A diferencia de Movimientos no hay paso
--                            de aprobación separado: abrir y cerrar
--                            comparten la misma condición de acceso
--                            (ver FinancialCashRegisterAccessGuard).
--                            El saldo esperado/diferencia se
--                            persisten como snapshot al cerrar (no se
--                            recalculan después), a diferencia de
--                            Fondos/Presupuestos que siempre calculan
--                            on-demand.
INSERT INTO modules (id,name,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000013','Caja Diaria','FINANCIAL_CASH_REGISTER','wallet','/financial-cash-registers',5,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);

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
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000024','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000008','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000026','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000009','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000027','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000010','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000029','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000011','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000030','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000012','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000031','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000013','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

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
-- Finanzas Institucionales: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000098','80000000-0000-0000-0000-000000000024','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000099','80000000-0000-0000-0000-000000000024','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000100','80000000-0000-0000-0000-000000000024','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Fondos: ver, crear, editar, habilitar, inhabilitar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000104','80000000-0000-0000-0000-000000000026','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000105','80000000-0000-0000-0000-000000000026','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000106','80000000-0000-0000-0000-000000000026','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000107','80000000-0000-0000-0000-000000000026','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000108','80000000-0000-0000-0000-000000000026','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
-- Donantes: solo lectura
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000109','80000000-0000-0000-0000-000000000027','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
-- Plantillas de Documentos: ver, crear, editar, habilitar, inhabilitar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000111','80000000-0000-0000-0000-000000000029','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000112','80000000-0000-0000-0000-000000000029','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000113','80000000-0000-0000-0000-000000000029','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000114','80000000-0000-0000-0000-000000000029','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000115','80000000-0000-0000-0000-000000000029','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
-- Presupuestos: ver, crear, editar, habilitar, inhabilitar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000116','80000000-0000-0000-0000-000000000030','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000117','80000000-0000-0000-0000-000000000030','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000118','80000000-0000-0000-0000-000000000030','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000119','80000000-0000-0000-0000-000000000030','55555555-5555-5555-5555-555555555555',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000120','80000000-0000-0000-0000-000000000030','55555555-5555-5555-5555-555555555556',CURRENT_TIMESTAMP);
-- Caja Diaria (CASO 5): ver, crear (abrir/cerrar), editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000121','80000000-0000-0000-0000-000000000031','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000122','80000000-0000-0000-0000-000000000031','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000123','80000000-0000-0000-0000-000000000031','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

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
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000025','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000008','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000032','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000013','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000028','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000010','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

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
-- Finanzas Institucionales: ver, crear, editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000101','80000000-0000-0000-0000-000000000025','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000102','80000000-0000-0000-0000-000000000025','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000103','80000000-0000-0000-0000-000000000025','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Caja Diaria (CASO 6, Sede Sur): ver, crear (abrir/cerrar), editar
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000124','80000000-0000-0000-0000-000000000032','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000125','80000000-0000-0000-0000-000000000032','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000126','80000000-0000-0000-0000-000000000032','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
-- Donantes: solo lectura
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000110','80000000-0000-0000-0000-000000000028','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);



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

-- =========================================================
-- BAUTIZO / TRASLADOS DE SEDE
-- Se habilitan en los mismos contratos que ya tenían
-- Membresías (CASO 3, CASO 5, CASO 6), con los mismos
-- permisos base (ver, crear, editar).
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000012','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000014','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000013','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000014','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000014','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000015','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000016','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000014','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000017','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- Bautizo / Traslados: ver, crear, editar (por cada contract_module de arriba)
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000065','80000000-0000-0000-0000-000000000012','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000066','80000000-0000-0000-0000-000000000012','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000067','80000000-0000-0000-0000-000000000012','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000068','80000000-0000-0000-0000-000000000013','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000069','80000000-0000-0000-0000-000000000013','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000070','80000000-0000-0000-0000-000000000013','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000071','80000000-0000-0000-0000-000000000014','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000072','80000000-0000-0000-0000-000000000014','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000073','80000000-0000-0000-0000-000000000014','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000074','80000000-0000-0000-0000-000000000015','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000075','80000000-0000-0000-0000-000000000015','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000076','80000000-0000-0000-0000-000000000015','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000077','80000000-0000-0000-0000-000000000016','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000078','80000000-0000-0000-0000-000000000016','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000079','80000000-0000-0000-0000-000000000016','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000080','80000000-0000-0000-0000-000000000017','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000081','80000000-0000-0000-0000-000000000017','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000082','80000000-0000-0000-0000-000000000017','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- MATRIMONIOS
-- Mismos contratos que Bautizo (CASO 3, 5, 6), mismos
-- permisos base (ver, crear, editar).
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000033','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000018','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000036','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000019','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000034','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000018','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000037','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000019','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000035','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000018','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000038','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000019','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000127','80000000-0000-0000-0000-000000000033','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000128','80000000-0000-0000-0000-000000000033','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000129','80000000-0000-0000-0000-000000000033','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000130','80000000-0000-0000-0000-000000000034','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000131','80000000-0000-0000-0000-000000000034','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000132','80000000-0000-0000-0000-000000000034','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000133','80000000-0000-0000-0000-000000000035','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000134','80000000-0000-0000-0000-000000000035','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000135','80000000-0000-0000-0000-000000000035','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000136','80000000-0000-0000-0000-000000000036','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000137','80000000-0000-0000-0000-000000000036','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000138','80000000-0000-0000-0000-000000000036','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000139','80000000-0000-0000-0000-000000000037','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000140','80000000-0000-0000-0000-000000000037','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000141','80000000-0000-0000-0000-000000000037','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000142','80000000-0000-0000-0000-000000000038','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000143','80000000-0000-0000-0000-000000000038','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000144','80000000-0000-0000-0000-000000000038','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- SEGUIMIENTO PASTORAL Y VISITANTES
-- Mismos contratos que Matrimonios/Grupos Pequeños (CASO 3, 5, 6),
-- mismos permisos base (ver, crear, editar) — ambos delegables a
-- ORG_USER (ver PastoralFollowUpAccessGuard/VisitorAccessGuard).
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000039','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000020','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000040','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000021','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000041','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000020','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000042','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000021','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000043','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000020','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000044','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000021','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000145','80000000-0000-0000-0000-000000000039','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000146','80000000-0000-0000-0000-000000000039','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000147','80000000-0000-0000-0000-000000000039','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000148','80000000-0000-0000-0000-000000000040','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000149','80000000-0000-0000-0000-000000000040','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000150','80000000-0000-0000-0000-000000000040','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000151','80000000-0000-0000-0000-000000000041','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000152','80000000-0000-0000-0000-000000000041','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000153','80000000-0000-0000-0000-000000000041','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000154','80000000-0000-0000-0000-000000000042','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000155','80000000-0000-0000-0000-000000000042','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000156','80000000-0000-0000-0000-000000000042','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000157','80000000-0000-0000-0000-000000000043','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000158','80000000-0000-0000-0000-000000000043','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000159','80000000-0000-0000-0000-000000000043','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000160','80000000-0000-0000-0000-000000000044','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000161','80000000-0000-0000-0000-000000000044','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000162','80000000-0000-0000-0000-000000000044','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- ACADEMIA BÍBLICA
-- Mismos contratos que Seguimiento Pastoral/Visitantes (CASO 3, 5,
-- 6). VER/CREAR/EDITAR delegables a ORG_USER en cursos extra,
-- dictados y matrículas (ver BibleAcademyAccessGuard) — la malla
-- curricular en sí queda exclusiva de org admin sin importar estos
-- permisos delegados.
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000045','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000022','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000046','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000022','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000047','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000022','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000163','80000000-0000-0000-0000-000000000045','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000164','80000000-0000-0000-0000-000000000045','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000165','80000000-0000-0000-0000-000000000045','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000166','80000000-0000-0000-0000-000000000046','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000167','80000000-0000-0000-0000-000000000046','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000168','80000000-0000-0000-0000-000000000046','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000169','80000000-0000-0000-0000-000000000047','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000170','80000000-0000-0000-0000-000000000047','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000171','80000000-0000-0000-0000-000000000047','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- RESERVAS DE ESPACIOS
-- Mismos contratos que Seguimiento Pastoral/Visitantes/Academia
-- Bíblica (CASO 3, 5, 6). VER/CREAR/EDITAR delegables a ORG_USER en
-- reservas (ver SpaceReservationAccessGuard) — el catálogo de
-- espacios en sí queda exclusivo de org admin/branch admin sin
-- importar estos permisos delegados.
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000048','70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000014','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000049','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000014','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000050','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000014','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000172','80000000-0000-0000-0000-000000000048','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000173','80000000-0000-0000-0000-000000000048','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000174','80000000-0000-0000-0000-000000000048','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000175','80000000-0000-0000-0000-000000000049','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000176','80000000-0000-0000-0000-000000000049','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000177','80000000-0000-0000-0000-000000000049','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000178','80000000-0000-0000-0000-000000000050','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000179','80000000-0000-0000-0000-000000000050','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000180','80000000-0000-0000-0000-000000000050','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- INVENTARIO
-- Mismos contratos que Reservas de Espacios/Academia Bíblica
-- (CASO 3, 5, 6). VER/CREAR/EDITAR delegables a ORG_USER en
-- movimientos y asignaciones de custodia (ver InventoryAccessGuard)
-- — el catálogo de ítems en sí queda exclusivo de org admin/branch
-- admin sin importar estos permisos delegados.
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000051','70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000052','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000053','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000015','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000181','80000000-0000-0000-0000-000000000051','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000182','80000000-0000-0000-0000-000000000051','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000183','80000000-0000-0000-0000-000000000051','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000184','80000000-0000-0000-0000-000000000052','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000185','80000000-0000-0000-0000-000000000052','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000186','80000000-0000-0000-0000-000000000052','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000187','80000000-0000-0000-0000-000000000053','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000188','80000000-0000-0000-0000-000000000053','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000189','80000000-0000-0000-0000-000000000053','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- RRHH
-- Mismos contratos que Inventario/Reservas de Espacios/Academia
-- Bíblica (CASO 3, 5, 6). VER/CREAR/EDITAR delegables a ORG_USER
-- en vacaciones/permisos (incluye aprobar/rechazar) y planilla
-- (ver HrAccessGuard) — la ficha de empleado en sí queda exclusiva
-- de org admin/branch admin sin importar estos permisos delegados.
-- =========================================================
-- REESTRUCTURADO: HR (id ...016) ya no es un módulo hoja asignable
-- (ahora es el padre "Recursos Humanos") — se reemplaza el único
-- contract_module por 3, uno por cada hijo (STAFF_MEMBER/
-- LEAVE_REQUEST/PAYROLL), con el mismo VER/CREAR/EDITAR que tenía HR,
-- para los mismos 3 contratos (CASO 3, 5, 6).
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000063','70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000018','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000064','70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000019','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000065','70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000020','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000066','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000018','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000067','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000019','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000068','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000020','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000069','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000018','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000070','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000019','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000071','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000020','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000208','80000000-0000-0000-0000-000000000063','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000209','80000000-0000-0000-0000-000000000063','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000210','80000000-0000-0000-0000-000000000063','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000211','80000000-0000-0000-0000-000000000064','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000212','80000000-0000-0000-0000-000000000064','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000213','80000000-0000-0000-0000-000000000064','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000214','80000000-0000-0000-0000-000000000065','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000215','80000000-0000-0000-0000-000000000065','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000216','80000000-0000-0000-0000-000000000065','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000217','80000000-0000-0000-0000-000000000066','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000218','80000000-0000-0000-0000-000000000066','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000219','80000000-0000-0000-0000-000000000066','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000220','80000000-0000-0000-0000-000000000067','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000221','80000000-0000-0000-0000-000000000067','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000222','80000000-0000-0000-0000-000000000067','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000223','80000000-0000-0000-0000-000000000068','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000224','80000000-0000-0000-0000-000000000068','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000225','80000000-0000-0000-0000-000000000068','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000226','80000000-0000-0000-0000-000000000069','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000227','80000000-0000-0000-0000-000000000069','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000228','80000000-0000-0000-0000-000000000069','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000229','80000000-0000-0000-0000-000000000070','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000230','80000000-0000-0000-0000-000000000070','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000231','80000000-0000-0000-0000-000000000070','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000232','80000000-0000-0000-0000-000000000071','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000233','80000000-0000-0000-0000-000000000071','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000234','80000000-0000-0000-0000-000000000071','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- REPORTES AVANZADOS (Dashboard Ejecutivo)
-- Mismos contratos que RRHH/Inventario (CASO 3, 5, 6). Sin
-- delegación (no aparece en visible_user) y sin catálogo de
-- permisos: el acceso es puramente por rol (org admin/branch
-- admin), igual criterio que Presupuestos — ver
-- AdvancedReportsAccessGuard.
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000057','70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000017','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000058','70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000017','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000059','70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000017','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- =========================================================
-- GRUPO FAMILIAR
-- Mismos contratos que RRHH/Inventario/Reportes Avanzados (CASO 3, 5,
-- 6). VER/CREAR/EDITAR delegables a ORG_USER (ver
-- FamilyGroupAccessGuard) — a diferencia de Reportes Avanzados, este
-- SÍ tiene catálogo de permisos porque es delegable.
-- =========================================================
-- CASO 3 (ORGANIZACION 2, contrato 002)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000060','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000023','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 5 (ORGANIZACION 1, contrato 004)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000061','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000023','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- CASO 6 (SEDE SUR, contrato 005)
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000062','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000023','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000199','80000000-0000-0000-0000-000000000060','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000200','80000000-0000-0000-0000-000000000060','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000201','80000000-0000-0000-0000-000000000060','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000202','80000000-0000-0000-0000-000000000061','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000203','80000000-0000-0000-0000-000000000061','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000204','80000000-0000-0000-0000-000000000061','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000205','80000000-0000-0000-0000-000000000062','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000206','80000000-0000-0000-0000-000000000062','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000207','80000000-0000-0000-0000-000000000062','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- SOLICITUDES DE VISIBILIDAD (ENTRANTES: revisar/aprobar)
-- Mismos contratos que Bautizo/Traslados (CASO 3, 5, 6).
-- Solo ver + editar (editar = aprobar/rechazar); no se crea
-- nada desde acá (eso vive en MY_VISIBILITY_REQUEST).
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000018','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000016','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000019','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000016','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000020','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000016','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000083','80000000-0000-0000-0000-000000000018','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000085','80000000-0000-0000-0000-000000000018','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000086','80000000-0000-0000-0000-000000000019','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000088','80000000-0000-0000-0000-000000000019','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000089','80000000-0000-0000-0000-000000000020','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000091','80000000-0000-0000-0000-000000000020','55555555-5555-5555-5555-555555555553',CURRENT_TIMESTAMP);

-- =========================================================
-- MIS SOLICITUDES (SALIENTES: crear + hacer seguimiento)
-- Mismos contratos. Solo ver + crear; aprobar/rechazar no
-- aplica acá (eso es de la sede dueña, en VISIBILITY_REQUEST).
-- =========================================================
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000021','70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000017','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000022','70000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000017','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO contract_modules (id,contract_id,module_id,status,enabled_at,created_at) VALUES ('80000000-0000-0000-0000-000000000023','70000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000017','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000092','80000000-0000-0000-0000-000000000021','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000093','80000000-0000-0000-0000-000000000021','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000094','80000000-0000-0000-0000-000000000022','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000095','80000000-0000-0000-0000-000000000022','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000096','80000000-0000-0000-0000-000000000023','55555555-5555-5555-5555-555555555551',CURRENT_TIMESTAMP);
INSERT INTO contract_module_permissions (id,contract_module_id,permission_id,created_at) VALUES ('82000000-0000-0000-0000-000000000097','80000000-0000-0000-0000-000000000023','55555555-5555-5555-5555-555555555552',CURRENT_TIMESTAMP);

-- =========================================================
-- DATA ACCESS RULES
-- Cuando una persona se traslada de sede, su data histórica de
-- estos 3 módulos queda "dueña" de la sede donde se creó (ver
-- Membership.branch / MinistryAssignment.branch / Baptism.branch).
-- scope=APPROVAL_REQUIRED: la nueva sede debe pedir visibilidad
-- y la sede dueña (o el admin de organización) debe aprobarla —
-- ver VisibilityRequest/VisibilityGrant. Antes de esa aprobación,
-- el registro se muestra como "restringido" (dato existe, pero
-- no se ve el detalle) en vez de ocultarse por completo.
-- =========================================================
INSERT INTO data_access_rules (id,module_id,scope,requires_approval,enabled,created_at) VALUES ('83000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000012','APPROVAL_REQUIRED',true,true,CURRENT_TIMESTAMP);
INSERT INTO data_access_rules (id,module_id,scope,requires_approval,enabled,created_at) VALUES ('83000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000013','APPROVAL_REQUIRED',true,true,CURRENT_TIMESTAMP);
INSERT INTO data_access_rules (id,module_id,scope,requires_approval,enabled,created_at) VALUES ('83000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000014','APPROVAL_REQUIRED',true,true,CURRENT_TIMESTAMP);
