-- Convention: the game only uses short TEMPLATE question templates. The hand-written (CURATED) templates, one per old
-- question, are deleted. The 34 planned TEMPLATE rows (is_active = FALSE) and the 9 active ones are kept.
--
-- The old hand-written questions stay in game_questions (sessions point at them) but are switched off; game_questions
-- .template_id is ON DELETE SET NULL, so deleting their templates only clears that link. Finished sessions keep their
-- own question snapshot, so history is not affected.

UPDATE game_questions
SET is_active = FALSE, updated_at = now()
WHERE is_active
  AND template_id IN (SELECT id FROM question_templates WHERE kind = 'CURATED');

DELETE FROM question_templates
WHERE kind = 'CURATED';
