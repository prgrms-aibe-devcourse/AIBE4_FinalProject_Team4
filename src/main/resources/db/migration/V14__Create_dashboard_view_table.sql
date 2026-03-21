-- =====================================================================
-- V14: dashboard_view 테이블 생성
-- =====================================================================

CREATE TABLE dashboard_view
(
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID         NOT NULL REFERENCES project (id),
    created_by          UUID         NOT NULL REFERENCES member (id),
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    layout_config       JSONB        NOT NULL DEFAULT '[]',
    global_time_range   VARCHAR(20)  NOT NULL DEFAULT '1h',
    refresh_interval_ms INTEGER,
    is_default          BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_refresh_interval
        CHECK (refresh_interval_ms IS NULL OR refresh_interval_ms >= 10000),
    CONSTRAINT chk_global_time_range
        CHECK (global_time_range IN ('15m', '1h', '6h', '24h', '7d', '30d'))
);

CREATE INDEX idx_dashboard_view_project_id      ON dashboard_view (project_id);
CREATE INDEX idx_dashboard_view_project_created ON dashboard_view (project_id, created_at DESC);
