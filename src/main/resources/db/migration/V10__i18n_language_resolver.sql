-- Language Resolver model: text and audio per language live in ONE jsonb column `i18n`:
--
--   {"vi": {"text": "...", "audio": "..."}, "en": {"text": "...", "audio": "..."}}
--
-- on `question_templates` (what the question says) and `answer_items` (what each option says). The question
-- generator knows nothing about languages; LanguageResolverService picks the keys just before the session question
-- is saved (VI: vi/vi, EN: en/en, VI_EN: vi for the question, en for the answers).
--
-- This replaces the per-language columns added by V8 (answer_items.name_vi, game_questions.question_text_vi).
-- topics.name_vi / description_vi are NOT touched: topics are not part of the question/answer i18n.

-- ---------------------------------------------------------------------------------------------------------------
-- 1. question_templates
--    kind TEMPLATE = reusable short wording ("What is this?"), usable with many topics and answer items
--    kind CURATED  = one existing hand-written question (wrapped so every question has exactly one text source)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE question_templates (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(100) NOT NULL,
    kind          VARCHAR(10)  NOT NULL DEFAULT 'TEMPLATE',
    question_type VARCHAR(30),
    age_group     age_group    NOT NULL DEFAULT 'AGE_1_3',
    difficulty    SMALLINT     NOT NULL DEFAULT 1,
    i18n          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    generation    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_templates_code UNIQUE (code),
    CONSTRAINT ck_question_templates_kind CHECK (kind IN ('TEMPLATE', 'CURATED')),
    CONSTRAINT ck_question_templates_difficulty CHECK (difficulty BETWEEN 1 AND 3),
    CONSTRAINT ck_question_templates_i18n_object CHECK (jsonb_typeof(i18n) = 'object'),
    CONSTRAINT ck_question_templates_generation_object CHECK (jsonb_typeof(generation) = 'object')
);

-- ---------------------------------------------------------------------------------------------------------------
-- 2. answer_items.i18n  (from name / name_vi / the existing voice asset, which is English)
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE answer_items ADD COLUMN i18n JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE answer_items ADD CONSTRAINT ck_answer_items_i18n_object CHECK (jsonb_typeof(i18n) = 'object');

UPDATE answer_items a
SET i18n = jsonb_build_object('en', jsonb_strip_nulls(jsonb_build_object(
                   'text', a.name,
                   'audio', (SELECT m.storage_url FROM media_assets m WHERE m.id = a.voice_id))))
           || CASE WHEN a.name_vi IS NOT NULL AND a.name_vi <> ''
                   THEN jsonb_build_object('vi', jsonb_build_object('text', a.name_vi))
                   ELSE '{}'::jsonb END;

-- ---------------------------------------------------------------------------------------------------------------
-- 3. game_questions.template_id: every existing question gets a CURATED template holding its wording
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE game_questions ADD COLUMN template_id UUID REFERENCES question_templates (id) ON DELETE SET NULL;
CREATE INDEX idx_game_questions_template ON game_questions (template_id);

INSERT INTO question_templates (code, kind, question_type, age_group, difficulty, i18n)
SELECT DISTINCT ON (q.question_key)
       q.question_key, 'CURATED', q.question_type, q.age_group, LEAST(GREATEST(q.difficulty, 1), 3),
       jsonb_build_object('en', jsonb_build_object('text', q.question_text))
           || CASE WHEN q.question_text_vi IS NOT NULL AND q.question_text_vi <> ''
                   THEN jsonb_build_object('vi', jsonb_build_object('text', q.question_text_vi))
                   ELSE '{}'::jsonb END
FROM game_questions q
WHERE q.question_key IS NOT NULL
ORDER BY q.question_key, q.created_at
ON CONFLICT (code) DO NOTHING;

UPDATE game_questions q
SET template_id = t.id
FROM question_templates t
WHERE t.code = q.question_key AND q.template_id IS NULL;

-- ---------------------------------------------------------------------------------------------------------------
-- 4. Remove the hard-coded per-language columns (their content now lives in i18n)
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE answer_items   DROP COLUMN name_vi;
ALTER TABLE game_questions DROP COLUMN question_text_vi;
