-- Deletes 21 question templates that are not used any more (7 enabled fixed math templates and 14 planned ones that
-- were never switched on). Their code is also removed from R__zz_templates.sql, so they are not created again.
--
-- The 7 enabled ones each have one generated question in game_questions. Those questions are switched off and kept
-- (finished sessions point at them); game_questions.template_id is ON DELETE SET NULL, so deleting the template only
-- clears that link. The 14 planned ones have no questions at all.
-- The DO block writes one NOTICE per template (code, active flag, questions, questions used in sessions): Flyway prints
-- these notices in the application log.

UPDATE game_questions
SET is_active = FALSE, updated_at = now()
WHERE is_active
  AND template_id IN (SELECT id FROM question_templates WHERE code IN (
    'MINUS_4_1',
    'PLUS_1_1',
    'PLUS_2_1',
    'AFTER_NUMBER_3',
    'BEFORE_NUMBER_5',
    'PLUS_3_1',
    'MINUS_3_1',
    'GROUP_FEWER',
    'WHAT_COMES_NEXT',
    'WHICH_SAME',
    'WHICH_DIFFERENT',
    'RED_OR_BLUE',
    'BIG_OR_SMALL',
    'ONE_OR_TWO',
    'GROUP_MORE',
    'WHERE_BELONGS',
    'WHAT_COMES_BEFORE',
    'WHAT_COMES_AFTER',
    'WHO_HAS_THIS',
    'WHO_USES_THIS',
    'WHAT_FOR'));

DO $$
DECLARE
    t RECORD;
    deleted_count INTEGER;
BEGIN
    FOR t IN
        SELECT qt.code, qt.kind, qt.is_active,
               (SELECT count(*) FROM game_questions q WHERE q.template_id = qt.id) AS questions,
               (SELECT count(*) FROM session_questions sq JOIN game_questions q ON q.id = sq.question_id
                 WHERE q.template_id = qt.id) AS used_in_sessions
        FROM question_templates qt
        WHERE qt.code IN (
    'MINUS_4_1',
    'PLUS_1_1',
    'PLUS_2_1',
    'AFTER_NUMBER_3',
    'BEFORE_NUMBER_5',
    'PLUS_3_1',
    'MINUS_3_1',
    'GROUP_FEWER',
    'WHAT_COMES_NEXT',
    'WHICH_SAME',
    'WHICH_DIFFERENT',
    'RED_OR_BLUE',
    'BIG_OR_SMALL',
    'ONE_OR_TWO',
    'GROUP_MORE',
    'WHERE_BELONGS',
    'WHAT_COMES_BEFORE',
    'WHAT_COMES_AFTER',
    'WHO_HAS_THIS',
    'WHO_USES_THIS',
    'WHAT_FOR')
        ORDER BY qt.code
    LOOP
        RAISE NOTICE 'Deleting question template % (kind=%, active=%, questions kept inactive=%, used in sessions=%)',
            t.code, t.kind, t.is_active, t.questions, t.used_in_sessions;
    END LOOP;

    DELETE FROM question_templates
    WHERE code IN (
    'MINUS_4_1',
    'PLUS_1_1',
    'PLUS_2_1',
    'AFTER_NUMBER_3',
    'BEFORE_NUMBER_5',
    'PLUS_3_1',
    'MINUS_3_1',
    'GROUP_FEWER',
    'WHAT_COMES_NEXT',
    'WHICH_SAME',
    'WHICH_DIFFERENT',
    'RED_OR_BLUE',
    'BIG_OR_SMALL',
    'ONE_OR_TWO',
    'GROUP_MORE',
    'WHERE_BELONGS',
    'WHAT_COMES_BEFORE',
    'WHAT_COMES_AFTER',
    'WHO_HAS_THIS',
    'WHO_USES_THIS',
    'WHAT_FOR');
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Deleted % question template(s)', deleted_count;
END
$$;
