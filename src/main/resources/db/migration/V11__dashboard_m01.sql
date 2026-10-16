-- V11 · M01 Panel. Solo guarda la personalización (orden y widgets ocultos); los widgets viven en código y consultan a su módulo dueño.
-- owner_id = id de la persona o del personal de plataforma (los códigos de widget son propios de cada nivel, así que no chocan).
CREATE TABLE user_dashboard_pref (
    owner_id     UUID        NOT NULL,
    widget_code  VARCHAR(40) NOT NULL,
    position     INT         NOT NULL,
    hidden       BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (owner_id, widget_code)
);
