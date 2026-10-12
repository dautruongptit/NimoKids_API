-- Content for the Language Resolver (V10): fills the `i18n` jsonb of question_templates and answer_items.
-- REPEATABLE and idempotent, so it is safe in every environment and re-runs whenever this file changes.
--
-- The file name starts with "zz" on purpose: Flyway runs repeatable migrations in the alphabetical order of their
-- description, and this one must run AFTER R__dev_seed_data (which creates the rows in dev databases). Rows that do
-- not exist yet are simply not touched.
--
-- Vietnamese answer words: see R__zz_aa_vocabulary.sql.
-- A language without a text is not an error: LanguageResolverService falls back to English.

-- 1. Every answer item gets its English text (the canonical name).
UPDATE answer_items
SET i18n = jsonb_set(COALESCE(i18n, '{}'::jsonb), '{en}', COALESCE(i18n -> 'en', '{}'::jsonb) || jsonb_build_object('text', name))
WHERE i18n #>> '{en,text}' IS DISTINCT FROM name;

-- 2. Hand-written questions: one CURATED template each, English from question_text, Vietnamese from the content seed
--    (game_questions.metadata ->> 'text_vi'), then linked through game_questions.template_id.
INSERT INTO question_templates (code, kind, question_type, age_group, difficulty, i18n)
SELECT DISTINCT ON (q.question_key)
       q.question_key, 'CURATED', q.question_type, q.age_group, LEAST(GREATEST(q.difficulty, 1), 3),
       jsonb_build_object('en', jsonb_build_object('text', q.question_text))
           || CASE WHEN q.metadata ->> 'text_vi' IS NOT NULL
                   THEN jsonb_build_object('vi', jsonb_build_object('text', q.metadata ->> 'text_vi'))
                   ELSE '{}'::jsonb END
FROM game_questions q
WHERE q.question_key IS NOT NULL AND q.question_key NOT LIKE 'TPL\_%'   -- generated rows already have their template
ORDER BY q.question_key, q.created_at
ON CONFLICT (code) DO UPDATE
    SET i18n = EXCLUDED.i18n, question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group,
        difficulty = EXCLUDED.difficulty, updated_at = now()
    WHERE question_templates.kind = 'CURATED' AND question_templates.i18n IS DISTINCT FROM EXCLUDED.i18n;

UPDATE game_questions q
SET template_id = t.id
FROM question_templates t
WHERE t.code = q.question_key AND q.template_id IS DISTINCT FROM t.id;

-- 3. Vietnamese answer words now live in R__zz_aa_vocabulary.sql (one place for the whole vocabulary).
