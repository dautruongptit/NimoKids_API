-- Architecture decision (hybrid authentication):
--   * anonymous_players = children, no login, identified by X-Anonymous-Id.   (unchanged, never merged with users)
--   * users             = back-office accounts, ADMIN only for now, login with email + password, JWT.
-- V2 created this table as app_users (username, password_hash, USER/ADMIN). This migration reshapes it to the
-- agreed contract: users(id, email, password, role, is_active, created_at, updated_at).
-- `password` always holds a bcrypt hash, never plain text.

-- Only ADMIN accounts are allowed. V2 only ever created non-ADMIN rows through the dev seed.
DELETE FROM app_users WHERE role <> 'ADMIN';

-- Accounts that were created with a plain username become <username>@nimokids.local so that they satisfy the email rule.
UPDATE app_users SET username = username || '@nimokids.local' WHERE position('@' IN username) = 0;

ALTER TABLE app_users RENAME TO users;
ALTER TABLE users RENAME COLUMN username TO email;
ALTER TABLE users RENAME COLUMN password_hash TO password;
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(254);
ALTER TABLE users DROP COLUMN last_login_at;

ALTER INDEX app_users_pkey RENAME TO users_pkey;
ALTER INDEX uk_app_users_username_lower RENAME TO uk_users_email_lower;

ALTER TABLE users DROP CONSTRAINT ck_app_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role = 'ADMIN');
ALTER TABLE users ADD CONSTRAINT ck_users_email_format CHECK (position('@' IN email) > 1);
