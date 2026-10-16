-- V38 · Enlaza los certificados propios de M08 (rite, `certificate`) y M13 (formación, `training_certificate`) con el
-- motor de M18 (`issued_document`) — cierra el [D1] documentado en el encabezado de V29__templates_certificates_m18.sql
-- ("migrarlos a este motor... se deja pendiente de una entrega aparte").
--
-- Diseño (documentado en detalle en M08_membresia_ritos_como_ejecutar.md / M13_formacion_como_ejecutar.md, sección
-- "Certificados y M18" agregada en esta entrega): el certificado propio de cada módulo (número, código de
-- verificación, snapshot, verificación pública `/v/:code` y `/vt/:code`) SIGUE siendo la fuente de verdad y sigue
-- funcionando exactamente igual si la organización no tiene DOC_TEMPLATES configurado — nada de esto cambia. Cuando
-- la organización SÍ tiene DOC_TEMPLATES contratado y ya publicó una plantilla predeterminada para el tipo (org o
-- sede), el servicio además emite el documento real (PDF+QR, motor `DocumentRenderService`) a través de
-- `IssuedDocumentService.issue()` y guarda su id en esta columna nueva, puramente informativa (sin FK dura, mismo
-- patrón de referencia suave que `fin_movement.source_id`/`org_event.fund_id`/`org_event.space_id` de las
-- integraciones anteriores) para que el front pueda ofrecer "Descargar PDF" además de la vista imprimible
-- provisional que ya existía.
ALTER TABLE certificate ADD COLUMN issued_document_id UUID;
ALTER TABLE training_certificate ADD COLUMN issued_document_id UUID;

-- ---------------------------------------------------------------- plantillas base (N1) para los 4 tipos que M08/M13
-- ya anticipaban en TemplateSupport.SPECIFIC pero que hasta ahora no tenían ninguna plantilla base publicada — sin
-- esto ninguna organización podría copiar-y-personalizar, tendría que empezar del elemento en blanco. Diseño mínimo
-- en A4 vertical con los tokens exigidos por [V1][V4] de TemplateSupport (número + variable principal) más QR de
-- verificación; cada organización que quiera usarlas las copia a `document_template`, las personaliza (logo, colores,
-- firma) y las publica como predeterminada — hasta que alguna lo haga, M08/M13 siguen emitiendo solo su certificado
-- propio, exactamente como hoy.
INSERT INTO template_base (id, type, name, design, locale, version, status, created_at) VALUES
 (gen_random_uuid(), 'BAPTISM_CERTIFICATE', 'Certificado de bautismo (base)',
  '{"pageSize":"A4","orientation":"portrait","elements":[
     {"type":"TEXT","x":20,"y":40,"width":170,"height":12,"content":"{{org.displayName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":70,"width":170,"height":14,"content":"Certificado de Bautismo","align":"center","fontSize":20,"bold":true},
     {"type":"TEXT","x":20,"y":110,"width":170,"height":10,"content":"Se certifica que","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":124,"width":170,"height":14,"content":"{{person.fullName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":150,"width":170,"height":10,"content":"fue bautizado(a) el {{rite.date}} en {{branch.name}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":168,"width":170,"height":10,"content":"Ministro oficiante: {{officiant}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":230,"width":100,"height":8,"content":"N.° {{number}}","align":"left","fontSize":9},
     {"type":"TEXT","x":20,"y":242,"width":100,"height":8,"content":"Emitido el {{date}}","align":"left","fontSize":9},
     {"type":"QR","x":150,"y":220,"width":30,"height":30}
   ]}', 'es', 1, 'PUBLISHED', now()),
 (gen_random_uuid(), 'MARRIAGE_CERTIFICATE', 'Certificado de matrimonio (base)',
  '{"pageSize":"A4","orientation":"portrait","elements":[
     {"type":"TEXT","x":20,"y":40,"width":170,"height":12,"content":"{{org.displayName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":70,"width":170,"height":14,"content":"Certificado de Matrimonio","align":"center","fontSize":20,"bold":true},
     {"type":"TEXT","x":20,"y":110,"width":170,"height":10,"content":"Se certifica el matrimonio entre","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":124,"width":170,"height":14,"content":"{{person.fullName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":140,"width":170,"height":10,"content":"y","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":152,"width":170,"height":14,"content":"{{person2.fullName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":174,"width":170,"height":10,"content":"celebrado el {{rite.date}} en {{branch.name}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":190,"width":170,"height":10,"content":"Ministro oficiante: {{officiant}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":230,"width":100,"height":8,"content":"N.° {{number}}","align":"left","fontSize":9},
     {"type":"TEXT","x":20,"y":242,"width":100,"height":8,"content":"Emitido el {{date}}","align":"left","fontSize":9},
     {"type":"QR","x":150,"y":220,"width":30,"height":30}
   ]}', 'es', 1, 'PUBLISHED', now()),
 (gen_random_uuid(), 'CHILD_DEDICATION_CERTIFICATE', 'Certificado de presentación de niños (base)',
  '{"pageSize":"A4","orientation":"portrait","elements":[
     {"type":"TEXT","x":20,"y":40,"width":170,"height":12,"content":"{{org.displayName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":70,"width":170,"height":14,"content":"Certificado de Presentación","align":"center","fontSize":20,"bold":true},
     {"type":"TEXT","x":20,"y":110,"width":170,"height":10,"content":"Se certifica que","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":124,"width":170,"height":14,"content":"{{person.fullName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":150,"width":170,"height":10,"content":"fue presentado(a) el {{rite.date}} en {{branch.name}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":168,"width":170,"height":10,"content":"Ministro oficiante: {{officiant}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":230,"width":100,"height":8,"content":"N.° {{number}}","align":"left","fontSize":9},
     {"type":"TEXT","x":20,"y":242,"width":100,"height":8,"content":"Emitido el {{date}}","align":"left","fontSize":9},
     {"type":"QR","x":150,"y":220,"width":30,"height":30}
   ]}', 'es', 1, 'PUBLISHED', now()),
 (gen_random_uuid(), 'BIBLE_ACADEMY_CERTIFICATE', 'Certificado de Academia Bíblica (base)',
  '{"pageSize":"A4","orientation":"landscape","elements":[
     {"type":"TEXT","x":20,"y":30,"width":257,"height":12,"content":"{{org.displayName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":60,"width":257,"height":14,"content":"Certificado de Academia Bíblica","align":"center","fontSize":20,"bold":true},
     {"type":"TEXT","x":20,"y":95,"width":257,"height":10,"content":"Se certifica que","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":109,"width":257,"height":14,"content":"{{person.fullName}}","align":"center","fontSize":16,"bold":true},
     {"type":"TEXT","x":20,"y":132,"width":257,"height":10,"content":"aprobó satisfactoriamente el curso {{course.name}} ({{course.hours}} horas)","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":146,"width":257,"height":10,"content":"en {{branch.name}}","align":"center","fontSize":11},
     {"type":"TEXT","x":20,"y":180,"width":100,"height":8,"content":"N.° {{number}}","align":"left","fontSize":9},
     {"type":"TEXT","x":20,"y":192,"width":100,"height":8,"content":"Emitido el {{date}}","align":"left","fontSize":9},
     {"type":"QR","x":220,"y":160,"width":30,"height":30}
   ]}', 'es', 1, 'PUBLISHED', now());
