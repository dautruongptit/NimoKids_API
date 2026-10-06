-- Admin accounts use a static role enum for now (MVP): SUPER_ADMIN and ADMIN.
-- The role is a plain VARCHAR so the table can later be replaced by dynamic RBAC (roles / permissions tables)
-- without changing any controller: controllers only declare @PreAuthorize rules.
--
-- V3 named this table users(email, password, role = ADMIN only). The agreed contract is now
--   admin_users(id, email, password_hash, role, is_active, created_at, updated_at)
-- `password_hash` always holds a bcrypt hash. anonymous_players (the children) stays separate and is never merged.

ALTER TABLE users RENAME TO admin_users;
ALTER TABLE admin_users RENAME COLUMN password TO password_hash;

ALTER INDEX users_pkey RENAME TO admin_users_pkey;
ALTER INDEX uk_users_email_lower RENAME TO uk_admin_users_email_lower;

ALTER TABLE admin_users DROP CONSTRAINT ck_users_role;
ALTER TABLE admin_users ADD CONSTRAINT ck_admin_users_role CHECK (role IN ('SUPER_ADMIN', 'ADMIN'));
ALTER TABLE admin_users RENAME CONSTRAINT ck_users_email_format TO ck_admin_users_email_format;
