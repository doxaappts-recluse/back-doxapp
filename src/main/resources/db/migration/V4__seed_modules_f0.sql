-- V4 · Módulos de F0/M05 en el catálogo (M03). Cada módulo posterior agrega su seed en su propia migración.
-- Niveles: N1 sistema · N2 organización · N3 sede/apoyo · N4 portal. Rutas relativas: el front antepone /platform, /app o /portal.
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('DASHBOARD',           'Panel',                       'Dashboard',              ARRAY['N1','N2','N3'],        'BASE', ARRAY['V'],                 TRUE,  'dashboard',           'dashboard',   10),
 ('PLATFORM_STAFF',      'Personal de plataforma',      'Platform staff',         ARRAY['N1'],                  'BASE', ARRAY['V','C','E','S'],     FALSE, 'staff',               'team',        900),
 ('ORG_ADMINS',          'Administradores de organización','Organization administrators', ARRAY['N1'],         'BASE', ARRAY['V','C','E','S'],     FALSE, 'org-admins',          'crown',       910),
 ('BRANCH_ADMINS',       'Administradores de sede',     'Branch administrators',  ARRAY['N2'],                  'BASE', ARRAY['V','C','E','S'],     FALSE, 'branch-admins',       'apartment',   920),
 ('SUPPORT_TEAM',        'Equipo y accesos',            'Team and access',        ARRAY['N2','N3'],             'BASE', ARRAY['V','C','E','S'],     FALSE, 'team',                'usergroup-add', 930),
 ('PERMISSION_PROFILES', 'Perfiles de permisos',        'Permission profiles',    ARRAY['N2'],                  'BASE', ARRAY['V','C','E','S'],     FALSE, 'permission-profiles', 'safety',      940),
 ('MY_ACCOUNT',          'Mi cuenta',                   'My account',             ARRAY['N1','N2','N3','N4'],   'BASE', ARRAY['V','E'],             FALSE, 'account',             'user',        999);
