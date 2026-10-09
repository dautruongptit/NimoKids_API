-- Rules 9.7: a session question must freeze everything the child saw (text, voice, image, topic, age group, question
-- key and the generation rules), not only the 4 options. Master data (answer_items, game_questions) can then change
-- without altering a running session or the history.
--
-- NULL on rows created before this migration: the API falls back to the live question for those.
ALTER TABLE session_questions ADD COLUMN question_snapshot JSONB;
