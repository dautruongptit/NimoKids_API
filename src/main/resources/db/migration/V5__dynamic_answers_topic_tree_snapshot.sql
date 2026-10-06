-- Dynamic answer generation, topic tree and grading snapshot (project_master_context.md v2).
--
--   * topics.parent_id           : topics form a tree; choosing a parent draws from its whole subtree.
--   * answer_items               : the vocabulary every answer option is generated from; explicit tags live in JSONB
--                                  `metadata` and are searched with `@>` (GIN index).
--   * game_questions             : keeps ONE correct_answer_item_id; `metadata.distractor_rules` describes the wrong ones.
--   * session_questions          : options_snapshot = the 4 generated options, permanent evidence for scoring.
--   * question_options           : removed (options are generated, never stored per question).
--   * time_limit_seconds         : default 8.
-- Existing data is migrated: old sessions keep a faithful snapshot, old questions become inactive "legacy" rows.

-- ---------------------------------------------------------------------------
-- 1. Topic tree
-- ---------------------------------------------------------------------------
ALTER TABLE topics ADD COLUMN parent_id UUID REFERENCES topics (id) ON DELETE RESTRICT;
ALTER TABLE topics ADD CONSTRAINT ck_topics_parent_not_self CHECK (parent_id IS NULL OR parent_id <> id);
CREATE INDEX idx_topics_parent_order ON topics (parent_id, display_order);

-- ---------------------------------------------------------------------------
-- 2. Answer items (vocabulary)
-- ---------------------------------------------------------------------------
CREATE TABLE answer_items (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(100) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    image_id   UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    voice_id   UUID         REFERENCES media_assets (id) ON DELETE RESTRICT,
    metadata   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_answer_items_code UNIQUE (code),
    CONSTRAINT ck_answer_items_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

-- Wrong answers are found with the containment operator (@>), which this index serves.
CREATE INDEX idx_answer_items_metadata ON answer_items USING GIN (metadata jsonb_path_ops);

-- ---------------------------------------------------------------------------
-- 3. Questions: one correct answer item + distractor rules
-- ---------------------------------------------------------------------------
ALTER TABLE game_questions ADD COLUMN correct_answer_item_id UUID REFERENCES answer_items (id) ON DELETE RESTRICT;

-- Legacy rows: one inactive answer item per old question, named after its correct option.
INSERT INTO answer_items (code, name, is_active)
SELECT 'LEGACY_' || replace(q.id::text, '-', ''), COALESCE(o.option_text, 'Unknown'), FALSE
FROM game_questions q
LEFT JOIN question_options o ON o.question_id = q.id AND o.is_correct;

UPDATE game_questions q
SET correct_answer_item_id = a.id, is_active = FALSE
FROM answer_items a
WHERE a.code = 'LEGACY_' || replace(q.id::text, '-', '');

ALTER TABLE game_questions ALTER COLUMN correct_answer_item_id SET NOT NULL;

UPDATE game_questions SET metadata = '{}'::jsonb WHERE metadata IS NULL;
ALTER TABLE game_questions ALTER COLUMN metadata SET DEFAULT '{}'::jsonb;
ALTER TABLE game_questions ALTER COLUMN metadata SET NOT NULL;

ALTER TABLE game_questions ALTER COLUMN time_limit_seconds SET DEFAULT 8;
UPDATE game_questions SET time_limit_seconds = 8;

CREATE INDEX idx_game_questions_correct_item ON game_questions (correct_answer_item_id);

-- ---------------------------------------------------------------------------
-- 4. Session questions: options snapshot and timer timestamps
-- ---------------------------------------------------------------------------
ALTER TABLE session_questions
    ADD COLUMN options_snapshot JSONB,
    ADD COLUMN presented_at     TIMESTAMPTZ,
    ADD COLUMN timer_started_at TIMESTAMPTZ;

-- Old sessions keep a faithful snapshot built from the options they were played with (same ids, so the
-- stored selected_option_id still points at the right entry).
UPDATE session_questions sq
SET options_snapshot = COALESCE((
    SELECT jsonb_agg(
               jsonb_build_object(
                   'optionId', o.id,
                   'answerItemId', NULL,
                   'text', o.option_text,
                   'imageUrl', (SELECT m.storage_url FROM media_assets m WHERE m.id = o.image_id),
                   'voiceUrl', (SELECT m.storage_url FROM media_assets m WHERE m.id = o.voice_id),
                   'displayOrder', o.display_order,
                   'isCorrect', o.is_correct)
               ORDER BY o.display_order)
    FROM question_options o
    WHERE o.question_id = sq.question_id), '[]'::jsonb);

ALTER TABLE session_questions ALTER COLUMN options_snapshot SET NOT NULL;
ALTER TABLE session_questions
    ADD CONSTRAINT ck_session_questions_snapshot_array CHECK (jsonb_typeof(options_snapshot) = 'array');

-- selected_option_id now holds an optionId from the snapshot (a plain UUID, not a foreign key).
ALTER TABLE session_questions DROP CONSTRAINT IF EXISTS session_questions_selected_option_id_fkey;
ALTER TABLE activity_logs DROP CONSTRAINT IF EXISTS activity_logs_option_id_fkey;

-- ---------------------------------------------------------------------------
-- 5. Static options are gone
-- ---------------------------------------------------------------------------
DROP TABLE question_options;
