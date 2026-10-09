-- Language mode (EN / VI / VI_EN). Structural only: the Vietnamese texts themselves are filled by the repeatable
-- migration R__zz_content_vi.sql, so every environment (dev and prod) gets them.
--
-- The mode is stored on the session: the options snapshot of a session is generated in the session's language and
-- every later response (question text, feedback, result) follows it.

ALTER TABLE game_sessions ADD COLUMN language_mode VARCHAR(10) NOT NULL DEFAULT 'EN';
ALTER TABLE game_sessions ADD CONSTRAINT chk_game_sessions_language_mode CHECK (language_mode IN ('EN', 'VI', 'VI_EN'));

-- Vietnamese counterparts. NULL = not translated yet: the API then falls back to the English text.
ALTER TABLE topics        ADD COLUMN name_vi          VARCHAR(100);
ALTER TABLE topics        ADD COLUMN description_vi   TEXT;
ALTER TABLE answer_items  ADD COLUMN name_vi          VARCHAR(100);
ALTER TABLE game_questions ADD COLUMN question_text_vi VARCHAR(500);
