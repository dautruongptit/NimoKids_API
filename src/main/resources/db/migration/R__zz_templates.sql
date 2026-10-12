-- Short question templates ("What is this?") and the questions generated from them.
-- REPEATABLE and idempotent. It must run AFTER the content seed that creates topics and answer items, hence the "zz"
-- (Flyway orders repeatable migrations by description: "zz content vi" < "zz templates").
--
-- Formula (rules 9): Question Template + Topic + AnswerItem + Generation Rule + AgeGroup -> Question Instance.
-- The instance is materialised as one game_questions row with a deterministic id, so the session engine, the
-- candidate query and the snapshot work unchanged.
--
-- Source of the wording: Question_Bank_review.md (Q01-Q40) and Rules_Creat_question.md. Only templates that can be
-- generated with TODAY's data are active (is_active = TRUE); the others are stored as PLANNED with the reason.
-- Texts: en and vi are used by LanguageResolverService; ja, zh and ko are kept for later.
--
-- The generated questions depend on the words of V16 (topics, items, tags) and V17/V18. Change this file whenever those
-- tags change so the questions are generated again (a repeatable migration only re-runs when its own text changes).

-- 1. The templates (21 unused ones were deleted by V23) ---------------------------------------------------------------------------------------------
INSERT INTO question_templates (code, kind, question_type, age_group, difficulty, i18n, generation, is_active)
VALUES
    ('WHAT_IS_THIS', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "What is this?"}, "vi": {"text": "Đây là gì?"}, "ja": {"text": "これは何ですか？"}, "zh": {"text": "这是什么？"}, "ko": {"text": "이게 뭐예요?"}}', '{"bank_key": "Q01", "status": "ACTIVE", "target": "item", "topics": ["LAND_ANIMALS", "SEA_ANIMALS", "FLY_ANIMALS", "LAND_VEHICLES", "SEA_VEHICLES", "FLY_VEHICLES", "FRUITS", "VEGETABLES", "OBJECTS", "BODY_PARTS", "TOYS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHO_IS_THIS', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "Who is this?"}, "vi": {"text": "Đây là ai?"}, "ja": {"text": "これは誰ですか？"}, "zh": {"text": "这是谁？"}, "ko": {"text": "누구예요?"}}', '{"bank_key": "Q02", "status": "ACTIVE", "target": "item", "topics": ["JOB", "PEOPLE_CHARACTERS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHAT_COLOR', 'TEMPLATE', 'COLOR_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "What color is it?"}, "vi": {"text": "Nó có màu gì?"}, "ja": {"text": "何色ですか？"}, "zh": {"text": "它是什么颜色？"}, "ko": {"text": "무슨 색이에요?"}}', '{"bank_key": "Q03", "status": "ACTIVE", "target": "item", "topics": ["COLORS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHAT_SHAPE', 'TEMPLATE', 'SHAPE_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "What shape is it?"}, "vi": {"text": "Nó có hình gì?"}, "ja": {"text": "何の形ですか？"}, "zh": {"text": "它是什么形状？"}, "ko": {"text": "무슨 모양이에요?"}}', '{"bank_key": "Q04", "status": "ACTIVE", "target": "item", "topics": ["SHAPES"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHAT_NUMBER', 'TEMPLATE', 'NUMBER_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "What number is this?"}, "vi": {"text": "Đây là số mấy?"}, "ja": {"text": "これは何の数字ですか？"}, "zh": {"text": "这是几？"}}', '{"bank_key": "Q05", "status": "ACTIVE", "target": "item", "topics": ["NUMBERS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('HOW_MANY', 'TEMPLATE', 'COUNTING', 'AGE_1_3', 1, '{"en": {"text": "How many are there?"}, "vi": {"text": "Có bao nhiêu cái?"}, "ja": {"text": "いくつありますか？"}, "zh": {"text": "有几个？"}, "ko": {"text": "몇 개 있어요?"}}', '{"bank_key": "Q06", "status": "ACTIVE", "target": "dynamic", "generator": "COUNT_OBJECTS", "topics": ["NUMBERS"], "slots": 3, "min": 1, "max": 5}', TRUE),
    ('WHO_SAYS', 'TEMPLATE', 'SOUND_TO_ANIMAL', 'AGE_1_3', 1, '{"en": {"text": "Who makes this sound?"}, "vi": {"text": "Ai phát ra âm thanh này?"}, "ja": {"text": "これは誰の声ですか？"}, "zh": {"text": "这是谁的声音？"}, "ko": {"text": "누구의 소리예요?"}}', '{"bank_key": "Q07", "status": "PLANNED", "reason": "needs animal sound files"}', FALSE),
    ('WHO_CAN_FLY', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_1_3', 1, '{"en": {"text": "Who can fly?"}, "vi": {"text": "Ai có thể bay?"}, "ja": {"text": "誰が飛べますか？"}, "zh": {"text": "谁会飞？"}, "ko": {"text": "누가 날 수 있어요?"}}', '{"bank_key": "Q08", "status": "ACTIVE", "target": "attribute", "tag": "can_fly", "picture": "🌤️"}', TRUE),
    ('WHO_CAN_SWIM', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_1_3', 1, '{"en": {"text": "Who can swim?"}, "vi": {"text": "Ai có thể bơi?"}, "ja": {"text": "誰が泳げますか？"}, "zh": {"text": "谁会游泳？"}}', '{"bank_key": "Q09", "status": "ACTIVE", "target": "attribute", "tag": "can_swim", "picture": "🌊"}', TRUE),
    ('WHERE_IS_IT', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_1_3', 1, '{"en": {"text": "Where is it?"}, "vi": {"text": "Nó ở đâu?"}, "ja": {"text": "どこにありますか？"}, "zh": {"text": "它在哪里？"}, "ko": {"text": "어디에 있어요?"}}', '{"bank_key": "Q10", "status": "PLANNED", "reason": "needs scene pictures"}', FALSE),
    ('WHICH_ONE', 'TEMPLATE', 'WORD_TO_IMAGE', 'AGE_1_3', 1, '{"en": {"text": "Which one is it?"}, "vi": {"text": "Đó là cái nào?"}, "ja": {"text": "どれですか？"}, "zh": {"text": "是哪一个？"}, "ko": {"text": "어느 것이에요?"}}', '{"bank_key": "Q11", "status": "PLANNED", "reason": "needs the picture-answer mode (options are pictures)"}', FALSE),
    ('WHAT_DO_YOU_SEE', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "What do you see?"}, "vi": {"text": "Con nhìn thấy gì?"}, "ja": {"text": "何が見えますか？"}, "zh": {"text": "你看到了什么？"}}', '{"bank_key": "Q12", "status": "ACTIVE", "target": "item", "topics": ["LAND_ANIMALS", "SEA_ANIMALS", "FLY_ANIMALS", "LAND_VEHICLES", "SEA_VEHICLES", "FLY_VEHICLES", "FRUITS", "VEGETABLES", "OBJECTS", "BODY_PARTS", "TOYS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHICH_BIGGER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one is bigger?"}, "vi": {"text": "Cái nào lớn hơn?"}, "ja": {"text": "どちらが大きいですか？"}, "zh": {"text": "哪个更大？"}}', '{"bank_key": "Q16", "status": "PLANNED", "reason": "AGE_4_5: needs size data"}', FALSE),
    ('WHICH_SMALLER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one is smaller?"}, "vi": {"text": "Cái nào nhỏ hơn?"}, "ja": {"text": "どちらが小さいですか？"}, "zh": {"text": "哪个更小？"}}', '{"bank_key": "Q17", "status": "PLANNED", "reason": "AGE_4_5: needs size data"}', FALSE),
    ('WHICH_MORE', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one has more?"}, "vi": {"text": "Cái nào có nhiều hơn?"}, "ja": {"text": "どちらが多いですか？"}, "zh": {"text": "哪个更多？"}}', '{"bank_key": "Q18", "status": "PLANNED", "reason": "AGE_4_5: needs the counting generator"}', FALSE),
    ('WHICH_FEWER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one has fewer?"}, "vi": {"text": "Cái nào có ít hơn?"}, "ja": {"text": "どちらが少ないですか？"}, "zh": {"text": "哪个更少？"}}', '{"bank_key": "Q19", "status": "PLANNED", "reason": "AGE_4_5: needs the counting generator"}', FALSE),
    ('NUMBER_BIGGER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which number is bigger?"}, "vi": {"text": "Số nào lớn hơn?"}, "ja": {"text": "どの数字が大きいですか？"}, "zh": {"text": "哪个数字更大？"}}', '{"bank_key": "Q33", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('NUMBER_SMALLER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which number is smaller?"}, "vi": {"text": "Số nào nhỏ hơn?"}, "ja": {"text": "どの数字が小さいですか？"}, "zh": {"text": "哪个数字更小？"}}', '{"bank_key": "Q34", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('MISSING_NUMBER', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "Which number is missing?"}, "vi": {"text": "Số nào còn thiếu?"}, "ja": {"text": "どの数字が足りませんか？"}, "zh": {"text": "缺少哪个数字？"}}', '{"bank_key": "Q40", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('WHAT_LETTER', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "What letter is this?"}, "vi": {"text": "Đây là chữ gì?"}}', '{"bank_key": "EXT-01", "status": "ACTIVE", "target": "item", "topics": ["ALPHABET"], "distractors": {"from": "category", "count": 3}}', TRUE)
ON CONFLICT (code) DO UPDATE
    SET question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty,
        i18n = EXCLUDED.i18n, generation = EXCLUDED.generation, is_active = EXCLUDED.is_active, updated_at = now()
    WHERE question_templates.kind = 'TEMPLATE';

-- 2. Item tags used to prove "exactly one right answer" ----------------------------------------------------------
--    item      = the item's own code: the correct_match of a generated question names its target.
--    lookalike = items that can be mistaken for each other in a picture share a tag, so none of them is ever shown
--                as a wrong answer for another one (Blue / Dark Blue, Sun / Sunlight, Turtle / Sea Turtle ...).
UPDATE answer_items
SET metadata = metadata || jsonb_build_object('item', code)
WHERE metadata ->> 'item' IS DISTINCT FROM code;

UPDATE answer_items a
SET metadata = a.metadata || jsonb_build_object('lookalike', v.tag)
FROM (VALUES
    ('COLORS_BLUE', 'blue'),
    ('COLORS_DARK_BLUE', 'blue'),
    ('SEA_ANIMALS_TURTLE', 'turtle'),
    ('SEA_ANIMALS_SEA_TURTLE', 'turtle'),
    ('NATURE_SUN', 'sun'),
    ('NATURE_SUNLIGHT', 'sun'),
    ('NATURE_PLANT', 'plant'),
    ('NATURE_TREE', 'plant'),
    ('BODY_PARTS_HANDS', 'hands'),
    ('BODY_PARTS_FINGERS', 'hands'),
    ('BODY_PARTS_HEAD', 'head'),
    ('BODY_PARTS_HAIR', 'head'),
    ('CLOTHES_SHOES', 'footwear'),
    ('CLOTHES_BOOTS', 'footwear'),
    ('CLOTHES_JACKET', 'coat'),
    ('CLOTHES_RAINCOAT', 'coat'),
    ('VEHICLES_CAR', 'car'),
    ('VEHICLES_POLICE_CAR', 'car')
) AS v(code, tag)
WHERE a.code = v.code AND a.metadata ->> 'lookalike' IS DISTINCT FROM v.tag;

-- 2b. Counting data (HOW_MANY) -------------------------------------------------------------------------------------
--     value    : the number an item stands for (a real number, so the answer is found by value, never by name).
--     counting : the numbers a counting question may use (1-5 today); wrong answers are drawn from them too.
--     countable: single things that look good repeated N times (emoji today, a picture later).
UPDATE answer_items a
SET metadata = a.metadata || jsonb_build_object('value', v.n)
FROM (VALUES ('NUMBERS_ONE', 1), ('NUMBERS_TWO', 2), ('NUMBERS_THREE', 3), ('NUMBERS_FOUR', 4), ('NUMBERS_FIVE', 5),
             ('NUMBERS_SIX', 6), ('NUMBERS_SEVEN', 7), ('NUMBERS_EIGHT', 8), ('NUMBERS_NINE', 9), ('NUMBERS_TEN', 10))
     AS v(code, n)
WHERE a.code = v.code AND (a.metadata -> 'value') IS DISTINCT FROM to_jsonb(v.n);

UPDATE answer_items
SET metadata = metadata || '{"counting": true}'::jsonb
WHERE code IN ('NUMBERS_ONE', 'NUMBERS_TWO', 'NUMBERS_THREE', 'NUMBERS_FOUR', 'NUMBERS_FIVE')
  AND (metadata -> 'counting') IS DISTINCT FROM 'true'::jsonb;

UPDATE answer_items
SET metadata = metadata || '{"countable": true}'::jsonb
WHERE code IN ('FRUITS_APPLE', 'FRUITS_BANANA', 'FRUITS_ORANGE', 'TOYS_BALL', 'FLY_ANIMALS_DUCK', 'SHAPES_STAR',
               'LAND_ANIMALS_CAT', 'LAND_ANIMALS_DOG', 'LAND_VEHICLES_CAR')
  AND (metadata -> 'countable') IS DISTINCT FROM 'true'::jsonb;

-- 3. One question per (template, topic, answer item) --------------------------------------------------------------
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, correct_answer_item_id, difficulty, min_age, max_age, display_order,
     metadata, is_active, question_key, age_group, question_type, template_id)
SELECT md5('nimokids:template-question:' || tpl.code || ':' || i.code)::uuid,
       t.id, m.id, tpl.i18n #>> '{en,text}', i.id, tpl.difficulty, 1, 5,
       100 + row_number() OVER (PARTITION BY t.id ORDER BY tpl.code, i.code),
       jsonb_build_object(
           'distractor_rules', jsonb_build_array(jsonb_build_object(
               'match', jsonb_build_object('category', i.metadata ->> 'category'), 'count', 3)),
           'correct_match', CASE WHEN i.metadata ? 'lookalike'
                                 THEN jsonb_build_object('lookalike', i.metadata ->> 'lookalike')
                                 ELSE jsonb_build_object('item', i.code) END,
           'source', 'TEMPLATE'),
       TRUE, left('TPL_' || tpl.code || '_' || i.code, 50),   -- question_key is VARCHAR(50)
       -- A word that is only taught at 4-5 is only asked in the 4-5 group.
       CASE WHEN i.metadata ->> 'age_group' = 'AGE_4_5' THEN 'AGE_4_5'::age_group ELSE tpl.age_group END,
       tpl.question_type, tpl.id
FROM question_templates tpl
CROSS JOIN LATERAL jsonb_array_elements_text(tpl.generation -> 'topics') AS tc(code)
JOIN topics t ON t.code = tc.code AND t.is_active
JOIN answer_items i ON i.metadata ->> 'category' = lower(t.code) AND i.is_active
JOIN game_modes m ON m.code = 'GUESS'
WHERE tpl.kind = 'TEMPLATE' AND tpl.is_active AND tpl.generation ->> 'generator' IS NULL
ON CONFLICT (id) DO UPDATE
    SET question_text = EXCLUDED.question_text, metadata = EXCLUDED.metadata, template_id = EXCLUDED.template_id,
        question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty,
        is_active = TRUE, updated_at = now();

-- 3b. Dynamic questions (generator templates): a few rows per topic, NO fixed answer item ---------------------------
--     The count and the object are drawn for every session (DynamicQuestionService). The row only says how to draw
--     them (metadata.generator) and where wrong answers come from. "slots" rows let one session meet it more than once.
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, correct_answer_item_id, difficulty, min_age, max_age, display_order,
     metadata, is_active, question_key, age_group, question_type, template_id)
SELECT md5('nimokids:template-question:' || tpl.code || ':' || tc.code || ':' || slot.n)::uuid,
       t.id, m.id, tpl.i18n #>> '{en,text}', NULL, tpl.difficulty, 1, 5,
       200 + slot.n,
       jsonb_build_object(
           'generator', jsonb_build_object(
               'type', tpl.generation ->> 'generator',
               'min', COALESCE((tpl.generation ->> 'min')::int, 1),
               'max', COALESCE((tpl.generation ->> 'max')::int, 5),
               'object_match', jsonb_build_object('countable', true),
               'number_match', jsonb_build_object('category', 'numbers', 'counting', true)),
           'distractor_rules', jsonb_build_array(jsonb_build_object(
               'match', jsonb_build_object('category', 'numbers', 'counting', true), 'count', 3)),
           'source', 'TEMPLATE'),
       TRUE, 'TPL_' || tpl.code || '_' || tc.code || '_' || slot.n, tpl.age_group, tpl.question_type, tpl.id
FROM question_templates tpl
CROSS JOIN LATERAL jsonb_array_elements_text(tpl.generation -> 'topics') AS tc(code)
CROSS JOIN LATERAL generate_series(1, COALESCE((tpl.generation ->> 'slots')::int, 1)) AS slot(n)
JOIN topics t ON t.code = tc.code AND t.is_active
JOIN game_modes m ON m.code = 'GUESS'
WHERE tpl.kind = 'TEMPLATE' AND tpl.is_active AND tpl.generation ->> 'generator' IS NOT NULL
ON CONFLICT (id) DO UPDATE
    SET question_text = EXCLUDED.question_text, metadata = EXCLUDED.metadata, template_id = EXCLUDED.template_id,
        question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty,
        is_active = TRUE, updated_at = now();

-- 3c. Attribute questions --------------------------------------------------------------------------------------
--     WHO_CAN_FLY / WHO_CAN_SWIM: one question per animal that has the tag (V22), under ANIMALS. The wrong answers are
--     animals explicitly tagged false for the same ability. The picture is a neutral scene: the answer's own picture
--     would show the right answer (metadata.question_image replaces it).
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, correct_answer_item_id, difficulty, min_age, max_age, display_order,
     metadata, is_active, question_key, age_group, question_type, template_id)
SELECT md5('nimokids:template-question:' || tpl.code || ':' || i.code)::uuid,
       t.id, m.id, tpl.i18n #>> '{en,text}', i.id, tpl.difficulty, 1, 5,
       400 + row_number() OVER (PARTITION BY tpl.code ORDER BY i.code),
       jsonb_build_object(
           'question_image', tpl.generation ->> 'picture',
           'distractor_rules', jsonb_build_array(jsonb_build_object(
               'match', jsonb_build_object('animal', true, tpl.generation ->> 'tag', false), 'count', 3)),
           'correct_match', jsonb_build_object(tpl.generation ->> 'tag', true),
           'source', 'TEMPLATE'),
       TRUE, left('TPL_' || tpl.code || '_' || i.code, 50), tpl.age_group, tpl.question_type, tpl.id
FROM question_templates tpl
JOIN topics t ON t.code = 'ANIMALS' AND t.is_active
JOIN game_modes m ON m.code = 'GUESS'
JOIN answer_items i ON i.is_active AND (i.metadata -> 'animal') = 'true'::jsonb
                   AND (i.metadata -> (tpl.generation ->> 'tag')) = 'true'::jsonb
WHERE tpl.kind = 'TEMPLATE' AND tpl.is_active AND tpl.generation ->> 'target' = 'attribute'
ON CONFLICT (id) DO UPDATE
    SET question_text = EXCLUDED.question_text, metadata = EXCLUDED.metadata, template_id = EXCLUDED.template_id,
        correct_answer_item_id = EXCLUDED.correct_answer_item_id, question_type = EXCLUDED.question_type,
        age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty, is_active = TRUE, updated_at = now();

-- A template that was switched off takes its generated questions with it (rows are kept for the history).
UPDATE game_questions q
SET is_active = FALSE, updated_at = now()
FROM question_templates tpl
WHERE q.template_id = tpl.id AND tpl.kind = 'TEMPLATE' AND NOT tpl.is_active AND q.is_active;

-- 4. Only template questions are used -------------------------------------------------------------------------------
--    CURATED questions (hand-written, no template or from a CURATED template) are deactivated so they never appear
--    regardless of the TEMPLATE_ONLY_FROM threshold.  Rows are kept for history; only is_active is flipped.
UPDATE game_questions
SET is_active = FALSE, updated_at = now()
WHERE is_active
  AND (template_id IS NULL
       OR template_id IN (SELECT id FROM question_templates WHERE kind <> 'TEMPLATE'));
