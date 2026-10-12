-- Content for the Language Resolver (V10): fills the `i18n` jsonb of question_templates and answer_items.
-- REPEATABLE and idempotent, so it is safe in every environment and re-runs whenever this file changes.
--
-- The file name starts with "zz" on purpose: Flyway runs repeatable migrations in the alphabetical order of their
-- description, and this one must run AFTER R__dev_seed_data (which creates the rows in dev databases). Rows that do
-- not exist yet are simply not touched.
--
-- Vietnamese answer words: see V16 and V19.
-- A language without a text is not an error: LanguageResolverService falls back to English.

-- 1. Every answer item gets its English text (the canonical name).
UPDATE answer_items
SET i18n = jsonb_set(COALESCE(i18n, '{}'::jsonb), '{en}', COALESCE(i18n -> 'en', '{}'::jsonb) || jsonb_build_object('text', name))
WHERE i18n #>> '{en,text}' IS DISTINCT FROM name;

-- 2. (removed) Hand-written questions no longer get a CURATED template: V21 deleted them and they are not offered.

-- 3. Vietnamese answer words now live in V16 and V19 (one place for the whole vocabulary).
