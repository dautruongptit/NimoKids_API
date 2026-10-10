-- Authentication, session and token management (design: docs 06-authentication).
--
--   users / oauth_accounts   account holders who sign in with Google (optional; playing stays anonymous)
--   user_sessions            one row per login (user OR admin); the family key of its refresh tokens
--   refresh_tokens           hashed rotating refresh tokens (raw values are never stored)
--   oauth_login_attempts     state / nonce / PKCE of a login in progress (10 minutes)
--   login_history            immutable: every sign-in attempt
--   security_events          immutable: security signals and admin audit actions
--   auth_settings            the active policy (one row, versioned)

-- ---------------------------------------------------------------------------------------------------------------
-- users, oauth_accounts
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(254) NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    display_name    VARCHAR(100),
    avatar_url      VARCHAR(500),
    locale          VARCHAR(10),
    status          VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    disabled_at     TIMESTAMPTZ,
    disabled_reason VARCHAR(200),
    deleted_at      TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'))
);
CREATE INDEX idx_users_email_lower ON users (lower(email));
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE oauth_accounts (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider                 VARCHAR(20)  NOT NULL,
    provider_subject         VARCHAR(255) NOT NULL,
    email_at_link            VARCHAR(254),
    email_verified_at_link   BOOLEAN,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at            TIMESTAMPTZ,
    CONSTRAINT ck_oauth_accounts_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT uk_oauth_accounts_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_accounts_user_provider UNIQUE (user_id, provider)
);

-- admin_users: counters for the progressive lockout of the password login
ALTER TABLE admin_users ADD COLUMN last_login_at     TIMESTAMPTZ;
ALTER TABLE admin_users ADD COLUMN failed_login_count SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE admin_users ADD COLUMN locked_until      TIMESTAMPTZ;

-- an anonymous player can be claimed by an account after the user's explicit consent
ALTER TABLE anonymous_players ADD COLUMN user_id UUID REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX idx_anonymous_players_user ON anonymous_players (user_id) WHERE user_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------------------
-- user_sessions: one row per login. One session = one refresh-token family.
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE user_sessions (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         REFERENCES users (id) ON DELETE CASCADE,
    admin_user_id        UUID         REFERENCES admin_users (id) ON DELETE CASCADE,
    auth_method          VARCHAR(15)  NOT NULL,
    status               VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_refreshed_at    TIMESTAMPTZ,
    idle_expires_at      TIMESTAMPTZ  NOT NULL,
    absolute_expires_at  TIMESTAMPTZ  NOT NULL,
    ended_at             TIMESTAMPTZ,
    ended_reason         VARCHAR(20),
    revoked_by_admin_id  UUID         REFERENCES admin_users (id) ON DELETE SET NULL,
    login_ip             INET,
    last_ip              INET,
    ip_change_count      INTEGER      NOT NULL DEFAULT 0,
    user_agent_login     VARCHAR(300),
    user_agent_last      VARCHAR(300),
    browser              VARCHAR(60),
    os                   VARCHAR(60),
    device_type          VARCHAR(20),
    device_id_hash       BYTEA,
    login_history_id     UUID,
    policy_snapshot      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_sessions_one_owner CHECK (num_nonnulls(user_id, admin_user_id) = 1),
    CONSTRAINT ck_user_sessions_method CHECK (auth_method IN ('GOOGLE', 'PASSWORD')),
    CONSTRAINT ck_user_sessions_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_user_sessions_reason CHECK (ended_reason IS NULL OR ended_reason IN (
        'IDLE', 'ABSOLUTE', 'LOGOUT', 'LOGOUT_ALL', 'ADMIN_REVOKED', 'ACCOUNT_DISABLED', 'REFRESH_REUSE', 'EVICTED', 'POLICY_CHANGED')),
    CONSTRAINT ck_user_sessions_limits CHECK (absolute_expires_at >= created_at)
);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id, status);
CREATE INDEX idx_user_sessions_admin ON user_sessions (admin_user_id, status);
CREATE INDEX idx_user_sessions_absolute ON user_sessions (absolute_expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_user_sessions_idle ON user_sessions (idle_expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_user_sessions_last_seen ON user_sessions (last_seen_at DESC);
CREATE INDEX idx_user_sessions_login_ip ON user_sessions (login_ip);

-- ---------------------------------------------------------------------------------------------------------------
-- refresh_tokens: SHA-256 of the token only. At most one ACTIVE token per session.
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID         NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    token_hash      BYTEA        NOT NULL,
    generation      INTEGER      NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    replaced_by_id  UUID         REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    issued_ip       INET,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED'))
);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id, status);
CREATE UNIQUE INDEX uk_refresh_tokens_one_active ON refresh_tokens (session_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------------------------------------------
-- oauth_login_attempts: transient
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE oauth_login_attempts (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash        BYTEA        NOT NULL,
    nonce_hash        BYTEA        NOT NULL,
    code_verifier_enc BYTEA        NOT NULL,
    return_to         VARCHAR(200),
    ip                INET,
    user_agent        VARCHAR(300),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    consumed_at       TIMESTAMPTZ,
    CONSTRAINT uk_oauth_login_attempts_state UNIQUE (state_hash)
);
CREATE INDEX idx_oauth_login_attempts_expiry ON oauth_login_attempts (expires_at);

-- ---------------------------------------------------------------------------------------------------------------
-- login_history: immutable (see V12). No foreign keys at all, so purging a session or an account keeps its history.
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE login_history (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- No foreign keys: the history is append-only (V12 blocks UPDATE), so ON DELETE SET NULL could never run, and a
    -- history row must be writable in its own transaction right after the account was created.
    user_id          UUID,
    admin_user_id    UUID,
    session_id       UUID,
    method           VARCHAR(15)  NOT NULL,
    outcome          VARCHAR(10)  NOT NULL,
    failure_code     VARCHAR(40),
    ip               INET,
    country          VARCHAR(2),
    user_agent       VARCHAR(300),
    browser          VARCHAR(60),
    os               VARCHAR(60),
    device_type      VARCHAR(20),
    device_id_hash   BYTEA,
    is_new_device    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_new_ip        BOOLEAN      NOT NULL DEFAULT FALSE,
    identifier_hint  VARCHAR(120),
    request_id       UUID,
    CONSTRAINT ck_login_history_method CHECK (method IN ('GOOGLE', 'PASSWORD')),
    CONSTRAINT ck_login_history_outcome CHECK (outcome IN ('SUCCESS', 'FAILED', 'BLOCKED'))
);
CREATE INDEX idx_login_history_time ON login_history (occurred_at DESC);
CREATE INDEX idx_login_history_user ON login_history (user_id, occurred_at DESC);
CREATE INDEX idx_login_history_admin ON login_history (admin_user_id, occurred_at DESC);
CREATE INDEX idx_login_history_ip ON login_history (ip, occurred_at DESC);
CREATE INDEX idx_login_history_outcome ON login_history (outcome, occurred_at DESC);

-- ---------------------------------------------------------------------------------------------------------------
-- security_events: immutable. category SECURITY = detected by the system, AUDIT = an action by an admin or the system.
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE security_events (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    category         VARCHAR(10)  NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    severity         VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    actor_type       VARCHAR(10)  NOT NULL,
    actor_id         UUID,
    target_user_id   UUID,
    target_admin_id  UUID,
    session_id       UUID,
    ip               INET,
    request_id       UUID,
    result           VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS',
    metadata         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_security_events_category CHECK (category IN ('SECURITY', 'AUDIT')),
    CONSTRAINT ck_security_events_severity CHECK (severity IN ('INFO', 'WARN', 'CRITICAL')),
    CONSTRAINT ck_security_events_actor CHECK (actor_type IN ('USER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_security_events_result CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED'))
);
CREATE INDEX idx_security_events_time ON security_events (occurred_at DESC);
CREATE INDEX idx_security_events_type ON security_events (event_type, occurred_at DESC);
CREATE INDEX idx_security_events_target_user ON security_events (target_user_id, occurred_at DESC);
CREATE INDEX idx_security_events_session ON security_events (session_id);
CREATE INDEX idx_security_events_notable ON security_events (occurred_at DESC) WHERE severity <> 'INFO';

-- ---------------------------------------------------------------------------------------------------------------
-- auth_settings: a single row. No row = the defaults in the application.
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE auth_settings (
    id                   SMALLINT     PRIMARY KEY,
    version              INTEGER      NOT NULL DEFAULT 1,
    config               JSONB        NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_admin_id  UUID         REFERENCES admin_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_auth_settings_single CHECK (id = 1),
    CONSTRAINT ck_auth_settings_object CHECK (jsonb_typeof(config) = 'object')
);
