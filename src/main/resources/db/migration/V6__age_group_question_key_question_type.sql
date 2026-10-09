-- Age-group enum, question_key and question_type columns on game_questions.
--
-- The documentation (Rules Creat question.md, startGame.md) defines two age groups: AGE_1_3 (toddlers) and AGE_4_5
-- (preschoolers). AGE_4_5 inherits AGE_1_3 questions (40 % easy + 60 % hard mixing), so the query must be able to
-- select by group rather than by raw min_age/max_age range.
--
-- question_key is a stable human-readable identifier (e.g. 'ANIMALS_1') used for translations and de-duplication.
-- question_type classifies the question template (IMAGE_TO_WORD, COUNTING, etc.) per Rules Creat question.md.

-- ---------------------------------------------------------------------------
-- 1. PostgreSQL enum type for age groups
-- ---------------------------------------------------------------------------
DO $$ BEGIN
    CREATE TYPE age_group AS ENUM ('AGE_1_3', 'AGE_4_5');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ---------------------------------------------------------------------------
-- 2. New columns on game_questions
-- ---------------------------------------------------------------------------
ALTER TABLE game_questions ADD COLUMN age_group   age_group    NOT NULL DEFAULT 'AGE_1_3';
ALTER TABLE game_questions ADD COLUMN question_key VARCHAR(50);
ALTER TABLE game_questions ADD COLUMN question_type VARCHAR(30);

-- ---------------------------------------------------------------------------
-- 3. Backfill age_group from existing min_age / max_age
--    Current seed: all questions have min_age=1, max_age=5 (both groups).
--    Questions with max_age <= 3 would be AGE_1_3 only; min_age >= 4 would be AGE_4_5 only.
--    For now every row covers both groups, so AGE_1_3 (the default) is correct — AGE_4_5 inherits them.
-- ---------------------------------------------------------------------------
-- (default already handles this; no UPDATE needed for current data)

-- ---------------------------------------------------------------------------
-- 4. Backfill question_key from seed metadata
--    The seed uses md5('nimokids:question:' || question_key) as the id, but question_key itself is only in the
--    VALUES clause. We extract it from the metadata where it is stored as the key prefix of each question.
--    For rows that already have a key in their id derivation, we reconstruct it.
-- ---------------------------------------------------------------------------
-- The dev seed already sets question_key via the VALUES alias, so after re-running R__dev_seed_data.sql the column
-- will be populated. For any rows that pre-date the seed update, leave NULL (they are inactive legacy rows).

-- ---------------------------------------------------------------------------
-- 5. Indexes
-- ---------------------------------------------------------------------------
CREATE INDEX idx_game_questions_age_group ON game_questions (age_group);
CREATE INDEX idx_game_questions_question_key ON game_questions (question_key) WHERE question_key IS NOT NULL;
CREATE INDEX idx_game_questions_question_type ON game_questions (question_type) WHERE question_type IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. Allow MIX mode: game_sessions.topic_id becomes nullable
--    When topic_id IS NULL, questions were drawn from all root topics.
-- ---------------------------------------------------------------------------
ALTER TABLE game_sessions ALTER COLUMN topic_id DROP NOT NULL;
