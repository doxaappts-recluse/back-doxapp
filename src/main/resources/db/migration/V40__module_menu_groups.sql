-- Agrupa el catálogo de módulos N2/N3 bajo "módulos padre" puramente de navegación (kind BASE,
-- route NULL, sin acción real más que V). No reemplazan ningún módulo existente: solo organizan
-- el menú lateral, que hoy es una lista plana de ~38 ítems para ORG_ADMIN/BRANCH_ADMIN.
--
-- Importante: a diferencia de ORG_BRANDING (route NULL porque es una pestaña dentro de "Mi
-- organización"), estos GRP_* se usan como encabezados colapsables en el sidebar (ver
-- MenuService/SidebarService): un módulo con route NULL y al menos un hijo visible SÍ aparece en
-- el menú, solo que no es un enlace propio, es un acordeón.
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('GRP_ORG',            'Organización',         'Organization',        ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'apartment',    90),
 ('GRP_PEOPLE',          'Personas y ritos',     'People & rites',      ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'team',         115),
 ('GRP_ATTENDANCE',      'Asistencia y grupos',  'Attendance & groups', ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'check-circle', 185),
 ('GRP_PASTORAL',        'Pastoral y oración',   'Pastoral & prayer',   ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'heart',        267),
 ('GRP_TRAINING_EVENTS', 'Formación y eventos',  'Training & events',   ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'calendar',     278),
 ('GRP_FINANCE',         'Finanzas',             'Finance',             ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'dollar',       298),
 ('GRP_OPERATIONS',      'Operaciones',          'Operations',          ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'tool',         305),
 ('GRP_HR',              'Recursos humanos',     'Human resources',     ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'idcard',       325),
 ('GRP_DOCS_REPORTS',    'Documentos y reportes','Documents & reports', ARRAY['N2','N3'], 'BASE', ARRAY['V'], FALSE, NULL, 'file-text',    333);

UPDATE module SET parent_code = 'GRP_ORG'
 WHERE code IN ('ORGANIZATION','BRANCH','ORG_CONTRACT_VIEW','ORG_SETTINGS','APPROVAL_INBOX','BRANCH_TRANSFER','VISIBILITY_RULES');

UPDATE module SET parent_code = 'GRP_PEOPLE'
 WHERE code IN ('PERSON','FAMILY','PERSON_TAGS','VISITOR','MEMBERSHIP','BAPTISM','MARRIAGE','CHILD_DEDICATION');

UPDATE module SET parent_code = 'GRP_ATTENDANCE'
 WHERE code IN ('ATTENDANCE','CHILD_CHECKIN','SMALL_GROUP','MINISTRY','VOLUNTEER_SCHEDULING');

UPDATE module SET parent_code = 'GRP_PASTORAL'
 WHERE code IN ('PASTORAL_CARE','PRAYER');

UPDATE module SET parent_code = 'GRP_TRAINING_EVENTS'
 WHERE code IN ('TRAINING','EVENTS');

UPDATE module SET parent_code = 'GRP_FINANCE'
 WHERE code IN ('FIN_MOVEMENTS','FIN_FUNDS','FIN_BUDGETS','FIN_DONORS');

UPDATE module SET parent_code = 'GRP_OPERATIONS'
 WHERE code IN ('SPACES','INTEGRATIONS','INVENTORY');

UPDATE module SET parent_code = 'GRP_HR'
 WHERE code IN ('HR_STAFF','HR_LEAVE','HR_PAYROLL');

UPDATE module SET parent_code = 'GRP_DOCS_REPORTS'
 WHERE code IN ('DOC_TEMPLATES','REPORTS');
