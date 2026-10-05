-- NimoKids MVP schema (13 tables).
-- Sources: project_master_context.md (baseline, wins on conflicts) and project-context.md.
-- Conventions: UUID PK, TIMESTAMPTZ, JSONB metadata, soft deactivation via is_active.
-- Foreign key policies follow project_master_context.md section 5.15.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- topics
-- ---------------------------------------------------------------------------
CREATE TABLE topics (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    icon_url        VARCHAR(500),
    cover_image_url VARCHAR(500),
    min_age         SMALLINT     NOT NULL,
    max_age         SMALLINT     NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order   INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_topics_code UNIQUE (code),
    CONSTRAINT uk_topics_slug UNIQUE (slug),
    CONSTRAINT ck_topics_min_age CHECK (min_age >= 1),
    CONSTRAINT ck_topics_max_age CHECK (max_age >= min_age),
    CONSTRAINT ck_topics_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_topics_active_display_order ON topics (is_active, display_order);

-- ---------------------------------------------------------------------------
-- game_modes
-- ---------------------------------------------------------------------------
CREATE TABLE game_modes (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_game_modes_code UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- media_assets (metadata only; binary files live in object storage)
-- ---------------------------------------------------------------------------
CREATE TABLE media_assets (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_type      VARCHAR(30)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    storage_url     VARCHAR(1000) NOT NULL,
    mime_type       VARCHAR(100),
    duration_ms     INTEGER,
    file_size_bytes BIGINT,
    metadata        JSONB,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_media_assets_type CHECK (asset_type IN ('IMAGE', 'AUDIO', 'ANIMAL_SOUND', 'VIDEO')),
    CONSTRAINT ck_media_assets_duration CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_media_assets_file_size CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0)
);

-- ---------------------------------------------------------------------------
-- game_questions
-- ---------------------------------------------------------------------------
CREATE TABLE game_questions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id           UUID         NOT NULL REFERENCES topics (id) ON DELETE RESTRICT,
    game_mode_id       UUID         NOT NULL REFERENCES game_modes (id) ON DELETE RESTRICT,
    question_text      VARCHAR(500) NOT NULL,
    question_voice_id  UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    object_sound_id    UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    explanation        TEXT,
    difficulty         SMALLINT     NOT NULL,
    min_age            SMALLINT     NOT NULL,
    max_age            SMALLINT     NOT NULL,
    time_limit_seconds SMALLINT     NOT NULL DEFAULT 5,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order      INTEGER      NOT NULL DEFAULT 0,
    metadata           JSONB,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_game_questions_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_game_questions_min_age CHECK (min_age >= 1),
    CONSTRAINT ck_game_questions_max_age CHECK (max_age >= min_age),
    CONSTRAINT ck_game_questions_time_limit CHECK (time_limit_seconds > 0),
    CONSTRAINT ck_game_questions_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_game_questions_topic_mode_active ON game_questions (topic_id, game_mode_id, is_active);
CREATE INDEX idx_game_questions_game_mode_id ON game_questions (game_mode_id);

-- ---------------------------------------------------------------------------
-- question_options
-- ---------------------------------------------------------------------------
CREATE TABLE question_options (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id   UUID         NOT NULL REFERENCES game_questions (id) ON DELETE CASCADE,
    option_text   VARCHAR(255) NOT NULL,
    image_id      UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    voice_id      UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    is_correct    BOOLEAN      NOT NULL,
    display_order INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_options_question_display_order UNIQUE (question_id, display_order)
);

-- A question can never have more than one correct option.
-- ">= 2 options" and "exactly 1 correct" are verified in the service layer (playable-question check).
CREATE UNIQUE INDEX uk_question_options_one_correct ON question_options (question_id) WHERE is_correct;

-- ---------------------------------------------------------------------------
-- anonymous_players
-- ---------------------------------------------------------------------------
CREATE TABLE anonymous_players (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    anonymous_id    UUID         NOT NULL,
    device_type     VARCHAR(30),
    platform        VARCHAR(30),
    browser         VARCHAR(100),
    os              VARCHAR(100),
    first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    total_games     INTEGER      NOT NULL DEFAULT 0,
    total_questions INTEGER      NOT NULL DEFAULT 0,
    total_correct   INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_anonymous_players_anonymous_id UNIQUE (anonymous_id),
    CONSTRAINT ck_anonymous_players_totals CHECK (
        total_games >= 0 AND total_questions >= 0 AND total_correct >= 0 AND total_correct <= total_questions)
);

-- ---------------------------------------------------------------------------
-- game_sessions
-- session_id is the public identifier; id is the internal primary key used by foreign keys.
-- current_streak / max_streak come from project_master_context.md (5.7, 5.10).
-- ---------------------------------------------------------------------------
CREATE TABLE game_sessions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id              UUID        NOT NULL,
    player_id               UUID        NOT NULL REFERENCES anonymous_players (id) ON DELETE RESTRICT,
    topic_id                UUID        NOT NULL REFERENCES topics (id) ON DELETE RESTRICT,
    game_mode_id            UUID        NOT NULL REFERENCES game_modes (id) ON DELETE RESTRICT,
    total_questions         SMALLINT    NOT NULL DEFAULT 5,
    current_question_number SMALLINT    NOT NULL DEFAULT 1,
    correct_answers         SMALLINT    NOT NULL DEFAULT 0,
    wrong_answers           SMALLINT    NOT NULL DEFAULT 0,
    timeout_answers         SMALLINT    NOT NULL DEFAULT 0,
    score                   SMALLINT    NOT NULL DEFAULT 0,
    current_streak          SMALLINT    NOT NULL DEFAULT 0,
    max_streak              SMALLINT    NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_game_sessions_session_id UNIQUE (session_id),
    CONSTRAINT ck_game_sessions_status CHECK (status IN ('STARTED', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_game_sessions_total_questions CHECK (total_questions = 5),
    CONSTRAINT ck_game_sessions_current_question CHECK (current_question_number BETWEEN 1 AND total_questions),
    CONSTRAINT ck_game_sessions_counters CHECK (
        correct_answers >= 0 AND wrong_answers >= 0 AND timeout_answers >= 0
        AND correct_answers + wrong_answers + timeout_answers <= total_questions),
    CONSTRAINT ck_game_sessions_score CHECK (score = correct_answers),
    CONSTRAINT ck_game_sessions_streak CHECK (
        current_streak >= 0 AND max_streak >= current_streak AND max_streak <= total_questions),
    CONSTRAINT ck_game_sessions_finished_at CHECK (status <> 'COMPLETED' OR finished_at IS NOT NULL)
);

CREATE INDEX idx_game_sessions_player_status ON game_sessions (player_id, status);
CREATE INDEX idx_game_sessions_topic_id ON game_sessions (topic_id);
CREATE INDEX idx_game_sessions_game_mode_id ON game_sessions (game_mode_id);
CREATE INDEX idx_game_sessions_status_updated_at ON game_sessions (status, updated_at);

-- ---------------------------------------------------------------------------
-- session_questions
-- ---------------------------------------------------------------------------
CREATE TABLE session_questions (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id         UUID        NOT NULL REFERENCES game_sessions (id) ON DELETE CASCADE,
    question_id        UUID        NOT NULL REFERENCES game_questions (id) ON DELETE RESTRICT,
    question_number    SMALLINT    NOT NULL,
    selected_option_id UUID        REFERENCES question_options (id) ON DELETE RESTRICT,
    result             VARCHAR(20),
    response_time_ms   INTEGER,
    answered_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_session_questions_session_number UNIQUE (session_id, question_number),
    CONSTRAINT uk_session_questions_session_question UNIQUE (session_id, question_id),
    CONSTRAINT ck_session_questions_number CHECK (question_number BETWEEN 1 AND 5),
    CONSTRAINT ck_session_questions_result CHECK (result IS NULL OR result IN ('CORRECT', 'WRONG', 'TIMEOUT')),
    CONSTRAINT ck_session_questions_response_time CHECK (response_time_ms IS NULL OR response_time_ms >= 0)
);

CREATE INDEX idx_session_questions_question_id ON session_questions (question_id);

-- ---------------------------------------------------------------------------
-- stickers
-- ---------------------------------------------------------------------------
CREATE TABLE stickers (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    image_id    UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    rarity      VARCHAR(20)  NOT NULL DEFAULT 'COMMON',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_stickers_code UNIQUE (code),
    CONSTRAINT ck_stickers_rarity CHECK (rarity IN ('COMMON', 'RARE', 'EPIC', 'SPECIAL'))
);

-- ---------------------------------------------------------------------------
-- question_stickers
-- ---------------------------------------------------------------------------
CREATE TABLE question_stickers (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id      UUID        NOT NULL REFERENCES game_questions (id) ON DELETE CASCADE,
    sticker_id       UUID        NOT NULL REFERENCES stickers (id) ON DELETE RESTRICT,
    result_condition VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_stickers_question_sticker UNIQUE (question_id, sticker_id)
);

CREATE INDEX idx_question_stickers_sticker_id ON question_stickers (sticker_id);

-- ---------------------------------------------------------------------------
-- player_stickers
-- ---------------------------------------------------------------------------
CREATE TABLE player_stickers (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id  UUID        NOT NULL REFERENCES anonymous_players (id) ON DELETE RESTRICT,
    sticker_id UUID        NOT NULL REFERENCES stickers (id) ON DELETE RESTRICT,
    session_id UUID        REFERENCES game_sessions (id) ON DELETE SET NULL,
    earned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_player_stickers_player_sticker UNIQUE (player_id, sticker_id)
);

CREATE INDEX idx_player_stickers_sticker_id ON player_stickers (sticker_id);
CREATE INDEX idx_player_stickers_session_id ON player_stickers (session_id);

-- ---------------------------------------------------------------------------
-- activity_logs (analytics only, never a source of truth for scoring)
-- ---------------------------------------------------------------------------
CREATE TABLE activity_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id   UUID        REFERENCES anonymous_players (id) ON DELETE SET NULL,
    session_id  UUID        REFERENCES game_sessions (id) ON DELETE SET NULL,
    event_type  VARCHAR(50) NOT NULL,
    topic_id    UUID        REFERENCES topics (id) ON DELETE SET NULL,
    question_id UUID        REFERENCES game_questions (id) ON DELETE SET NULL,
    option_id   UUID        REFERENCES question_options (id) ON DELETE SET NULL,
    event_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_ms INTEGER,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_activity_logs_event_type CHECK (event_type IN (
        'START_GAME', 'VIEW_QUESTION', 'PLAY_QUESTION_AUDIO', 'SELECT_ANSWER',
        'ANSWER_CORRECT', 'ANSWER_WRONG', 'ANSWER_TIMEOUT', 'PLAY_ANSWER_AUDIO',
        'PLAY_OBJECT_SOUND', 'NEXT_QUESTION', 'FINISH_GAME', 'PLAY_AGAIN', 'GO_HOME')),
    CONSTRAINT ck_activity_logs_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX idx_activity_logs_session_id ON activity_logs (session_id);
CREATE INDEX idx_activity_logs_player_id ON activity_logs (player_id);
CREATE INDEX idx_activity_logs_event_type_time ON activity_logs (event_type, event_time);

-- ---------------------------------------------------------------------------
-- api_logs (technical request log; never store Authorization / Cookie values)
-- ---------------------------------------------------------------------------
CREATE TABLE api_logs (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID         NOT NULL,
    player_id           UUID         REFERENCES anonymous_players (id) ON DELETE SET NULL,
    session_id          UUID         REFERENCES game_sessions (id) ON DELETE SET NULL,
    http_method         VARCHAR(10)  NOT NULL,
    endpoint            VARCHAR(500) NOT NULL,
    status_code         SMALLINT     NOT NULL,
    response_time_ms    INTEGER,
    ip_address          INET,
    user_agent          TEXT,
    request_size_bytes  INTEGER,
    response_size_bytes INTEGER,
    error_code          VARCHAR(100),
    error_message       TEXT,
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_api_logs_request_id UNIQUE (request_id),
    CONSTRAINT ck_api_logs_response_time CHECK (response_time_ms IS NULL OR response_time_ms >= 0)
);

CREATE INDEX idx_api_logs_created_at ON api_logs (created_at);
CREATE INDEX idx_api_logs_endpoint ON api_logs (endpoint);
CREATE INDEX idx_api_logs_status_code ON api_logs (status_code);
CREATE INDEX idx_api_logs_session_id ON api_logs (session_id);
CREATE INDEX idx_api_logs_player_id ON api_logs (player_id);
