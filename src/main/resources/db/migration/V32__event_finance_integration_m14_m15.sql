-- V32 · Integración M14 (eventos) → M15 (finanzas): cierra la mitad de la deuda documentada como [D5] en el encabezado
-- de V26__finance_m15.sql ("la integración de M14 y M08 para generar FinancialMovement automáticamente queda pendiente").
-- Solo se cierra el lado de M14: M08 (ritos) todavía no tiene ningún campo de tarifa/monto implementado (quedó anotado
-- como "tarifas → M15" en los límites de M08 y nunca se construyó), así que no hay nada que enlazar todavía de ese lado;
-- se deja la referencia explícita en el propio D5 de V26 para que quien construya las tarifas de M08 sepa que debe
-- seguir este mismo patrón (createFromSource con type=INCOME), no uno nuevo.
--
-- fin_movement ya reservaba las columnas necesarias desde V26 (event_id, source_ref con 'EVENT' como valor previsto):
-- no se toca esa tabla. Se agrega solo lo que faltaba del lado de M14:
--   - org_event.fund_id: fondo opcional al que se abona la inscripción pagada; nulo mientras el evento no lo configure
--     o mientras FIN_MOVEMENTS no esté contratado (la app nunca lo exige, solo lo usa si está presente).
--   - event_registration.financial_movement_id: enlace de solo lectura al fin_movement generado al confirmar el pago
--     (mismo patrón "sin FK dura" que inventory_movement.financial_movement_id [V27] y payroll_run.financial_movement_id
--     [V28], porque FIN_MOVEMENTS puede no estar contratado y entonces la columna queda siempre null).
--
-- Comportamiento: EventRegistrationService.confirmPayment() ahora, además de marcar PAID como ya hacía, genera un
-- ingreso PENDING (categoría SERVICE_FEE) en fin_movement vía FinancialMovementService.createFromSource(...) —
-- generalizado en este mismo cambio para aceptar type=INCOME/EXPENSE y event_id, sin tocar las dos llamadas existentes
-- de M16 (compras de inventario) y M17 (planilla), que siguen exactamente igual — solo si (a) el evento tiene fondo
-- configurado y (b) la organización tiene FIN_MOVEMENTS contratado; si falta cualquiera de las dos, el comportamiento
-- es exactamente el de antes de este cambio (pago provisional dentro de event_registration, sin ningún movimiento).
-- El ingreso queda PENDING para que el equipo de finanzas lo apruebe desde la pantalla normal de Movimientos, igual
-- que ya ocurre con el egreso de planilla (M17) — no se auto-aprueba nunca, ni siquiera aquí.

ALTER TABLE org_event
    ADD COLUMN fund_id UUID REFERENCES fin_fund (id);

ALTER TABLE event_registration
    ADD COLUMN financial_movement_id UUID;                                                                       -- fin_movement.id, sin FK dura (ver cabecera)
