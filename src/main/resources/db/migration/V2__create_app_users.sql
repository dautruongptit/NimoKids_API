-- Back-office accounts (Admin portal). Players stay anonymous and never appear in this table.
-- Passwords are stored only as bcrypt hashes.

CREATE TABLE app_users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'USER'))
);

-- Usernames are unique regardless of case.
CREATE UNIQUE INDEX uk_app_users_username_lower ON app_users (lower(username));
