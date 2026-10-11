-- Dynamic questions (HOW_MANY): the correct answer is decided when a session is created, from the question's
-- metadata.generator, so game_questions may have no fixed correct_answer_item_id. A question must still say where its
-- answer comes from: either a fixed item or a generator (never both missing).
ALTER TABLE game_questions ALTER COLUMN correct_answer_item_id DROP NOT NULL;
ALTER TABLE game_questions
    ADD CONSTRAINT ck_game_questions_answer_source
    CHECK (correct_answer_item_id IS NOT NULL OR COALESCE(jsonb_typeof(metadata -> 'generator'), '') = 'object');
