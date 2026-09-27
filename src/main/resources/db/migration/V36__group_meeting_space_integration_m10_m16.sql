-- M10→M16 · Sexto punto de la lista de deudas de integración tras M24, y el último que cerraba el [D1] documentado en el
-- encabezado de V27__spaces_inventory_m16.sql ("las reservas automáticas desde M14/M10/M13 vía sourceType/sourceId quedan
-- pendientes"). M14 (eventos) se enlazó en V33 y M13 (formación) en V35; M10 (grupos) quedó aparte en ambas entregas porque
-- group_meeting no tenía ninguna hora de fin ni duración en su esquema — sin eso no hay forma de calcular el fin de una reserva.
--
-- A diferencia de course_class (una fila = toda la serie), group_meeting ya es una fila POR OCURRENCIA (cada reunión, incluida
-- una serie de "repetir N semanas", se inserta como una fila propia con su propio id) — por eso esta integración no necesita un
-- método de serie: cada reunión reserva, cuando mucho, una sola franja, igual que un evento de M14. Se reutiliza
-- ReservationService.createLinked() sin cambios, con sourceType = 'SMALL_GROUP' (ya contemplado en la validación de
-- ReservationService.submit(), igual que 'BIBLE_CLASS' ya lo estaba para M13).
--
-- Configurar el espacio y la hora de fin siempre se permite (para que quede listo cuando se contrate Instalaciones); la reserva
-- real solo se genera si SPACES está contratado, mismo criterio que M14→M15/M14→M16/M13→M16. Si el espacio ya está ocupado en
-- ese horario, la reunión igual se crea (o se guarda la reprogramación) pero esa ocurrencia puntual queda sin reserva — mismo
-- criterio de "no bloquear por un choque puntual" que ya usan submit() y createLinkedSeries(), coherente además con que
-- create() ya salta en silencio las fechas del grupo que chocan entre sí (contador `skipped` existente).
alter table group_meeting add column end_time time;
alter table group_meeting add column space_id uuid references space (id);
