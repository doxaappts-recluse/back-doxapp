-- =========================================================
-- ROLES
-- =========================================================
INSERT INTO roles (id,name,value,status,created_at) VALUES ('11111111-1111-1111-1111-111111111111','System Admin','SYSTEM_ADMIN','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('22222222-2222-2222-2222-222222222222','Support','SYSTEM_SUPPORT','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('33333333-3333-3333-3333-333333333333','Organization Admin','ORG_ADMIN','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('44444444-4444-4444-4444-444444444444','Organization User','ORG_USER','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO roles (id,name,value,status,created_at) VALUES ('55555555-5555-5555-5555-555555555555','Organization Branch Admin','ORG_BRANCH_ADMIN','ACTIVE',CURRENT_TIMESTAMP);


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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000010','Administración','Administration','ADMIN','setting',NULL,1,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000010','Personas','People','PERSONS','usergroup-add',NULL,2,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000011','Usuarios','Users','USERS','user','/users',1,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000012','Membresías','Memberships','MEMBERSHIP','idcard','/membership',2,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000013','Servicios Ministeriales','Ministerial Services','MINISTERIAL_SERVICE','medicine-box','/ministerial_service',3,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000014','Bautizo','Baptism','BAPTISM','solution','/baptism',4,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000018','Matrimonios','Marriages','MARRIAGE','heart','/marriage',5,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000019','Grupos Pequeños','Small Groups','SMALL_GROUP','cluster','/small-group',6,'ACTIVE',true,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000020','Seguimiento Pastoral','Pastoral Follow-up','PASTORAL_FOLLOWUP','phone','/pastoral-followup/inactive-members',7,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000021','Visitantes','Visitors','VISITOR','smile','/visitor',8,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000022','Academia Bíblica','Bible Academy','BIBLE_ACADEMY','read','/bible-academy/class',9,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000023','Grupo Familiar','Family Group','FAMILY_GROUP','apartment','/family-group',10,'ACTIVE',false,true,true,true,'20000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('50000000-0000-0000-0000-000000000003','Operaciones','Operations','OPERATIONS','appstore',NULL,13,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000014','Reservas de Espacios','Space Reservations','SPACE_RESERVATION','home','/space-reservation/reservations',1,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000003',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000015','Inventario','Inventory','INVENTORY','database','/inventory/items',2,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000003',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000016','Recursos Humanos','Human Resources','HR','idcard',NULL,15,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000018','Ficha de Empleado','Employee Record','STAFF_MEMBER','idcard','/hr/staff',1,'ACTIVE',false,true,true,true,'40000000-0000-0000-0000-000000000016',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000019','Vacaciones y Permisos','Vacations and Leave','LEAVE_REQUEST','calendar','/hr/leave-requests',2,'ACTIVE',false,true,true,true,'40000000-0000-0000-0000-000000000016',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000020','Planilla','Payroll','PAYROLL','pay-circle','/hr/payroll',3,'ACTIVE',false,true,true,true,'40000000-0000-0000-0000-000000000016',CURRENT_TIMESTAMP);
-- Reportes Avanzados: Dashboard Ejecutivo. Sin delegación a
-- ORG_USER (visible_user=false) y sin bypass SYSTEM — ver
-- AdvancedReportsAccessGuard. Org admin ve toda la organización,
-- branch admin solo su sede.
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000017','Reportes Avanzados','Advanced Reports','ADVANCED_REPORTS','fund','/advanced-reports',16,'ACTIVE',false,true,true,false,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('50000000-0000-0000-0000-000000000001','Gestión de Traslados','Transfer Management','TRANSFER_MANAGEMENT','swap',NULL,7,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000015','Traslados de Sede','Branch Transfers','BRANCH_TRANSFER','swap','/branch-transfer',1,'ACTIVE',true,true,true,true,'50000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000016','Solicitudes de Visibilidad','Visibility Requests','VISIBILITY_REQUEST','eye','/visibility-requests',2,'ACTIVE',true,true,true,true,'50000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('20000000-0000-0000-0000-000000000017','Mis Solicitudes','My Requests','MY_VISIBILITY_REQUEST','solution','/my-visibility-requests',3,'ACTIVE',true,true,true,true,'50000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000014','Contratos','Contracts','CONTRACTS','file-text','/contracts',4,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000015','Ministerios','Ministries','MINISTRY','team','/ministry',5,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('10000000-0000-0000-0000-000000000016','Módulos','Modules','MODULES','team','/modules',5,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000001','Dashboard','Dashboard','DASHBOARD','dashboard','/dashboard',1,'ACTIVE',true,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000002','Eventos','Events','EVENTS','calendar','/events',6,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000004','Usuarios de Acceso','Access Users','ACCESS_USERS','user-switch','/access_users',7,'ACTIVE',false,true,true,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000005','Organizaciones','Organizations','ORGANIZATIONS','bank','/organizations',8,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000006','Usuarios del Sistema','System Users','SYSTEM_USERS','safety','/system-users',9,'ACTIVE',true,false,false,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000007','Accesos Administrativos Organizacionales','Organizational Admin Access','ORG_ADMIN_BRANCH','crown','/org-admin-branch',10,'ACTIVE',true,true,true,false,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('50000000-0000-0000-0000-000000000002','Finanzas Institucionales','Institutional Finance','FINANCE_MANAGEMENT','dollar',NULL,8,'ACTIVE',false,true,true,true,NULL,CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000008','Movimientos','Movements','FINANCIAL_MOVEMENT','transaction','/financial-movements',1,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000009','Fondos','Funds','FINANCIAL_FUND','gold','/financial-funds',2,'ACTIVE',false,true,false,false,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000010','Donantes','Donors','FINANCIAL_DONOR','contacts','/financial-donors',3,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000011','Plantillas de Documentos','Document Templates','DOCUMENT_TEMPLATES','file-image','/document-templates',11,'ACTIVE',false,true,true,true,'10000000-0000-0000-0000-000000000010',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000012','Presupuestos','Budgets','FINANCIAL_BUDGET','pie-chart','/financial-budgets',4,'ACTIVE',false,true,false,false,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);
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
INSERT INTO modules (id,name_es,name_en,code,icon,route,order_num,status,visible_system,visible_org_admin,visible_branch_admin,visible_user,parent_id,created_at) VALUES ('40000000-0000-0000-0000-000000000013','Caja Diaria','Daily Cash Register','FINANCIAL_CASH_REGISTER','wallet','/financial-cash-registers',5,'ACTIVE',false,true,true,true,'50000000-0000-0000-0000-000000000002',CURRENT_TIMESTAMP);


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
-- CREDENTIALS (bootstrap SYSTEM_ADMIN / SYSTEM_SUPPORT)
-- =========================================================
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c9999999-0000-0000-0000-000000000999','admin','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','99999999-aaaa-bbbb-cccc-999999999999',CURRENT_TIMESTAMP);
INSERT INTO credentials (id,username,password,status,person_id,created_at) VALUES ('c9999999-0000-0000-0000-000000000998','support','$2a$12$xSdY4w8oOjT2ppSSgPMsfeHIo0Dm3wo8FQCWX936zsZzoB1133k16','ACTIVE','99999999-aaaa-bbbb-cccc-999999999998',CURRENT_TIMESTAMP);

-- =========================================================
-- USER ACCESS (bootstrap SYSTEM_ADMIN / SYSTEM_SUPPORT)
-- =========================================================
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a9999999-0000-0000-0000-000000000999','99999999-aaaa-bbbb-cccc-999999999999',NULL,NULL,'11111111-1111-1111-1111-111111111111','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO user_accesses (id,person_id,organization_id,branch_id,role_id,active,created_at) VALUES ('a9999999-0000-0000-0000-000000000998','99999999-aaaa-bbbb-cccc-999999999998',NULL,NULL,'22222222-2222-2222-2222-222222222222','ACTIVE',CURRENT_TIMESTAMP);

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
