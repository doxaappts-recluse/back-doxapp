-- V5 · La sesión recuerda la sede de trabajo elegida al ingresar (ORG_ADMIN elige una; los roles de sede llevan la suya).
ALTER TABLE refresh_token ADD COLUMN branch_id UUID;
