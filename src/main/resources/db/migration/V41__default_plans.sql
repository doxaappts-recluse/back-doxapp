-- V41 · Planes comerciales por defecto, listos para ofrecer en contratos nuevos desde el primer arranque
-- (status PUBLISHED). Precios estimados en soles (PEN, moneda por defecto del plan); el equipo comercial
-- los puede editar, reordenar su alcance o retirar luego desde Catálogo > Planes sin tocar código. Cada
-- plan incluye, además de los módulos BASE que trae toda organización según su nivel, el set acumulado de
-- módulos CONTRACTABLE de su tier y los de los tiers anteriores.

INSERT INTO plan (id, code, name, description, price, currency, status, created_at, version) VALUES
 ('b6e1b8f2-6c3f-4a3b-9b0f-1a2b3c4d5e01', 'PLAN_ESENCIAL', 'Esencial',
  'Para una sola sede que empieza a digitalizar membresía, bautismos, presentación de niños, grupos pequeños, cuidado pastoral, oración y eventos.',
  149.00, 'PEN', 'PUBLISHED', now(), 0),
 ('b6e1b8f2-6c3f-4a3b-9b0f-1a2b3c4d5e02', 'PLAN_CRECIMIENTO', 'Crecimiento',
  'Para iglesias en crecimiento: suma matrimonios, visitantes, check-in de niños, turnos de voluntarios, formación, finanzas, documentos y certificados, y el directorio del portal del miembro.',
  349.00, 'PEN', 'PUBLISHED', now(), 0),
 ('b6e1b8f2-6c3f-4a3b-9b0f-1a2b3c4d5e03', 'PLAN_MULTISEDE', 'Multisede',
  'Para organizaciones con varias sedes: traslados y visibilidad entre sedes, presupuestos, vacaciones y planilla, espacios, inventario, integraciones y autorregistro del portal.',
  699.00, 'PEN', 'PUBLISHED', now(), 0);

INSERT INTO plan_module (plan_id, module_code)
SELECT 'b6e1b8f2-6c3f-4a3b-9b0f-1a2b3c4d5e01'::uuid, m FROM unnest(ARRAY[
 'MEMBERSHIP','BAPTISM','CHILD_DEDICATION','SMALL_GROUP','PASTORAL_CARE','PRAYER','EVENTS','REPORTS'
]) AS m
UNION ALL
SELECT 'b6e1b8f2-6c3f-4a3b-9b0f-1a2b3c4d5e02'::uuid, m FROM unnest(ARRAY[
 'MEMBERSHIP','BAPTISM','CHILD_DEDICATION','SMALL_GROUP','PASTORAL_CARE','PRAYER','EVENTS','REPORTS',
 'MARRIAGE','VISITOR','CHILD_CHECKIN','VOLUNTEER_SCHEDULING','TRAINING','FIN_MOVEMENTS','FIN_FUNDS','FIN_DONORS',
 'DOC_TEMPLATES','HR_STAFF','PORTAL_DIRECTORY'
]) AS m
UNION ALL
SELECT 'b6e1b8f2-6c3f-4a3b-9b0f-1a2b3c4d5e03'::uuid, m FROM unnest(ARRAY[
 'MEMBERSHIP','BAPTISM','CHILD_DEDICATION','SMALL_GROUP','PASTORAL_CARE','PRAYER','EVENTS','REPORTS',
 'MARRIAGE','VISITOR','CHILD_CHECKIN','VOLUNTEER_SCHEDULING','TRAINING','FIN_MOVEMENTS','FIN_FUNDS','FIN_DONORS',
 'DOC_TEMPLATES','HR_STAFF','PORTAL_DIRECTORY',
 'BRANCH_TRANSFER','VISIBILITY_RULES','FIN_BUDGETS','HR_LEAVE','HR_PAYROLL','SPACES','INVENTORY','INTEGRATIONS','PORTAL_SIGNUP'
]) AS m;
