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

-- 1. The 40 templates ---------------------------------------------------------------------------------------------
INSERT INTO question_templates (code, kind, question_type, age_group, difficulty, i18n, generation, is_active)
VALUES
    ('WHAT_IS_THIS', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "What is this?"}, "vi": {"text": "Đây là gì?"}, "ja": {"text": "これは何ですか？"}, "zh": {"text": "这是什么？"}, "ko": {"text": "이게 뭐예요?"}}', '{"bank_key": "Q01", "status": "ACTIVE", "target": "item", "topics": ["ANIMALS", "FRUITS", "VEGETABLES", "VEHICLES", "TOYS", "CLOTHES", "HOME", "NATURE", "SEA_ANIMALS", "FARM_ANIMALS", "FOOD", "BODY_PARTS", "MUSIC_INSTRUMENTS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHO_IS_THIS', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "Who is this?"}, "vi": {"text": "Đây là ai?"}, "ja": {"text": "これは誰ですか？"}, "zh": {"text": "这是谁？"}, "ko": {"text": "누구예요?"}}', '{"bank_key": "Q02", "status": "ACTIVE", "target": "item", "topics": ["FAMILY"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHAT_COLOR', 'TEMPLATE', 'COLOR_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "What color is it?"}, "vi": {"text": "Nó có màu gì?"}, "ja": {"text": "何色ですか？"}, "zh": {"text": "它是什么颜色？"}, "ko": {"text": "무슨 색이에요?"}}', '{"bank_key": "Q03", "status": "ACTIVE", "target": "item", "topics": ["COLORS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHAT_SHAPE', 'TEMPLATE', 'SHAPE_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "What shape is it?"}, "vi": {"text": "Nó có hình gì?"}, "ja": {"text": "何の形ですか？"}, "zh": {"text": "它是什么形状？"}, "ko": {"text": "무슨 모양이에요?"}}', '{"bank_key": "Q04", "status": "ACTIVE", "target": "item", "topics": ["SHAPES"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('WHAT_NUMBER', 'TEMPLATE', 'NUMBER_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "What number is this?"}, "vi": {"text": "Đây là số mấy?"}, "ja": {"text": "これは何の数字ですか？"}, "zh": {"text": "这是几？"}}', '{"bank_key": "Q05", "status": "ACTIVE", "target": "item", "topics": ["NUMBERS"], "distractors": {"from": "category", "count": 3}}', TRUE),
    ('HOW_MANY', 'TEMPLATE', 'COUNTING', 'AGE_1_3', 1, '{"en": {"text": "How many are there?"}, "vi": {"text": "Có bao nhiêu cái?"}, "ja": {"text": "いくつありますか？"}, "zh": {"text": "有几个？"}, "ko": {"text": "몇 개 있어요?"}}', '{"bank_key": "Q06", "status": "PLANNED", "reason": "needs a generated picture of N objects (counting generator)"}', FALSE),
    ('WHO_SAYS', 'TEMPLATE', 'SOUND_TO_ANIMAL', 'AGE_1_3', 1, '{"en": {"text": "Who makes this sound?"}, "vi": {"text": "Ai phát ra âm thanh này?"}, "ja": {"text": "これは誰の声ですか？"}, "zh": {"text": "这是谁的声音？"}, "ko": {"text": "누구의 소리예요?"}}', '{"bank_key": "Q07", "status": "PLANNED", "reason": "needs animal sound files"}', FALSE),
    ('WHO_CAN_FLY', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_1_3', 1, '{"en": {"text": "Who can fly?"}, "vi": {"text": "Ai có thể bay?"}, "ja": {"text": "誰が飛べますか？"}, "zh": {"text": "谁会飞？"}, "ko": {"text": "누가 날 수 있어요?"}}', '{"bank_key": "Q08", "status": "PLANNED", "reason": "needs the can_fly tag on answer items"}', FALSE),
    ('WHO_CAN_SWIM', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_1_3', 1, '{"en": {"text": "Who can swim?"}, "vi": {"text": "Ai có thể bơi?"}, "ja": {"text": "誰が泳げますか？"}, "zh": {"text": "谁会游泳？"}}', '{"bank_key": "Q09", "status": "PLANNED", "reason": "needs the can_swim tag on answer items"}', FALSE),
    ('WHERE_IS_IT', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_1_3', 1, '{"en": {"text": "Where is it?"}, "vi": {"text": "Nó ở đâu?"}, "ja": {"text": "どこにありますか？"}, "zh": {"text": "它在哪里？"}, "ko": {"text": "어디에 있어요?"}}', '{"bank_key": "Q10", "status": "PLANNED", "reason": "needs scene pictures"}', FALSE),
    ('WHICH_ONE', 'TEMPLATE', 'WORD_TO_IMAGE', 'AGE_1_3', 1, '{"en": {"text": "Which one is it?"}, "vi": {"text": "Đó là cái nào?"}, "ja": {"text": "どれですか？"}, "zh": {"text": "是哪一个？"}, "ko": {"text": "어느 것이에요?"}}', '{"bank_key": "Q11", "status": "PLANNED", "reason": "needs the picture-answer mode (options are pictures)"}', FALSE),
    ('WHAT_DO_YOU_SEE', 'TEMPLATE', 'IMAGE_TO_WORD', 'AGE_1_3', 1, '{"en": {"text": "What do you see?"}, "vi": {"text": "Con nhìn thấy gì?"}, "ja": {"text": "何が見えますか？"}, "zh": {"text": "你看到了什么？"}}', '{"bank_key": "Q12", "status": "PLANNED", "reason": "same shape as WHAT_IS_THIS: kept off so a topic never repeats the same question"}', FALSE),
    ('BIG_OR_SMALL', 'TEMPLATE', 'COMPARISON', 'AGE_1_3', 1, '{"en": {"text": "Is it big or small?"}, "vi": {"text": "Nó to hay nhỏ?"}, "ja": {"text": "大きいですか、小さいですか？"}, "zh": {"text": "它是大还是小？"}, "ko": {"text": "커요, 작아요?"}}', '{"bank_key": "Q13", "status": "PLANNED", "reason": "has 2 options, the rules require 4: needs an owner decision"}', FALSE),
    ('RED_OR_BLUE', 'TEMPLATE', 'COLOR_RECOGNITION', 'AGE_1_3', 1, '{"en": {"text": "Is it red or blue?"}, "vi": {"text": "Nó màu đỏ hay xanh?"}, "ja": {"text": "赤ですか、青ですか？"}, "zh": {"text": "它是红色还是蓝色？"}}', '{"bank_key": "Q14", "status": "PLANNED", "reason": "has 2 options, the rules require 4: needs an owner decision"}', FALSE),
    ('ONE_OR_TWO', 'TEMPLATE', 'COUNTING', 'AGE_1_3', 1, '{"en": {"text": "Is it one or two?"}, "vi": {"text": "Là một hay hai?"}, "ja": {"text": "一つですか、二つですか？"}, "zh": {"text": "是一个还是两个？"}}', '{"bank_key": "Q15", "status": "PLANNED", "reason": "has 2 options, the rules require 4: needs an owner decision"}', FALSE),
    ('WHICH_BIGGER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one is bigger?"}, "vi": {"text": "Cái nào lớn hơn?"}, "ja": {"text": "どちらが大きいですか？"}, "zh": {"text": "哪个更大？"}}', '{"bank_key": "Q16", "status": "PLANNED", "reason": "AGE_4_5: needs size data"}', FALSE),
    ('WHICH_SMALLER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one is smaller?"}, "vi": {"text": "Cái nào nhỏ hơn?"}, "ja": {"text": "どちらが小さいですか？"}, "zh": {"text": "哪个更小？"}}', '{"bank_key": "Q17", "status": "PLANNED", "reason": "AGE_4_5: needs size data"}', FALSE),
    ('WHICH_MORE', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one has more?"}, "vi": {"text": "Cái nào có nhiều hơn?"}, "ja": {"text": "どちらが多いですか？"}, "zh": {"text": "哪个更多？"}}', '{"bank_key": "Q18", "status": "PLANNED", "reason": "AGE_4_5: needs the counting generator"}', FALSE),
    ('WHICH_FEWER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one has fewer?"}, "vi": {"text": "Cái nào có ít hơn?"}, "ja": {"text": "どちらが少ないですか？"}, "zh": {"text": "哪个更少？"}}', '{"bank_key": "Q19", "status": "PLANNED", "reason": "AGE_4_5: needs the counting generator"}', FALSE),
    ('WHICH_DIFFERENT', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which one is different?"}, "vi": {"text": "Cái nào khác?"}, "ja": {"text": "どれが違いますか？"}, "zh": {"text": "哪个不一样？"}}', '{"bank_key": "Q20", "status": "PLANNED", "reason": "AGE_4_5: needs a generator"}', FALSE),
    ('WHICH_SAME', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which ones are the same?"}, "vi": {"text": "Những cái nào giống nhau?"}, "ja": {"text": "どれが同じですか？"}, "zh": {"text": "哪些是一样的？"}}', '{"bank_key": "Q21", "status": "PLANNED", "reason": "AGE_4_5: needs a generator"}', FALSE),
    ('WHAT_COMES_NEXT', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "What comes next?"}, "vi": {"text": "Tiếp theo là gì?"}, "ja": {"text": "次は何ですか？"}, "zh": {"text": "接下来是什么？"}}', '{"bank_key": "Q22", "status": "PLANNED", "reason": "AGE_4_5: needs ordered items"}', FALSE),
    ('WHAT_COMES_BEFORE', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "What comes before it?"}, "vi": {"text": "Trước nó là gì?"}, "ja": {"text": "その前は何ですか？"}, "zh": {"text": "它前面是什么？"}}', '{"bank_key": "Q23", "status": "PLANNED", "reason": "AGE_4_5: needs ordered items"}', FALSE),
    ('WHAT_COMES_AFTER', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "What comes after it?"}, "vi": {"text": "Sau nó là gì?"}, "ja": {"text": "その後は何ですか？"}, "zh": {"text": "它后面是什么？"}}', '{"bank_key": "Q24", "status": "PLANNED", "reason": "AGE_4_5: needs ordered items"}', FALSE),
    ('WHO_HAS_THIS', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_4_5', 2, '{"en": {"text": "Who has this?"}, "vi": {"text": "Ai có cái này?"}, "ja": {"text": "これを持っているのは誰ですか？"}, "zh": {"text": "谁拥有这个？"}}', '{"bank_key": "Q25", "status": "PLANNED", "reason": "AGE_4_5: needs relation tags"}', FALSE),
    ('WHO_USES_THIS', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_4_5', 2, '{"en": {"text": "Who uses this?"}, "vi": {"text": "Ai sử dụng cái này?"}, "ja": {"text": "これは誰が使いますか？"}, "zh": {"text": "谁使用这个？"}}', '{"bank_key": "Q26", "status": "PLANNED", "reason": "AGE_4_5: needs relation tags"}', FALSE),
    ('WHAT_FOR', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_4_5', 2, '{"en": {"text": "What do we use it for?"}, "vi": {"text": "Chúng ta dùng nó để làm gì?"}, "ja": {"text": "これは何に使いますか？"}, "zh": {"text": "这个是用来做什么的？"}}', '{"bank_key": "Q27", "status": "PLANNED", "reason": "AGE_4_5: needs the used_for tag"}', FALSE),
    ('WHERE_BELONGS', 'TEMPLATE', 'ATTRIBUTE_MATCHING', 'AGE_4_5', 2, '{"en": {"text": "Where does it belong?"}, "vi": {"text": "Nó thuộc về đâu?"}, "ja": {"text": "これはどこにありますか？"}, "zh": {"text": "它应该放在哪里？"}}', '{"bank_key": "Q28", "status": "PLANNED", "reason": "AGE_4_5: needs the belongs_to tag"}', FALSE),
    ('GROUP_MORE', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which group has more?"}, "vi": {"text": "Nhóm nào có nhiều hơn?"}, "ja": {"text": "どちらのグループが多いですか？"}, "zh": {"text": "哪一组更多？"}}', '{"bank_key": "Q29", "status": "PLANNED", "reason": "AGE_4_5: needs the counting generator"}', FALSE),
    ('GROUP_FEWER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which group has fewer?"}, "vi": {"text": "Nhóm nào có ít hơn?"}, "ja": {"text": "どちらのグループが少ないですか？"}, "zh": {"text": "哪一组更少？"}}', '{"bank_key": "Q30", "status": "PLANNED", "reason": "AGE_4_5: needs the counting generator"}', FALSE),
    ('AFTER_NUMBER_3', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "What comes after 3?"}, "vi": {"text": "Sau số 3 là số mấy?"}, "ja": {"text": "3の次は何ですか？"}, "zh": {"text": "3后面是几？"}}', '{"bank_key": "Q31", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('BEFORE_NUMBER_5', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "What comes before 5?"}, "vi": {"text": "Trước số 5 là số mấy?"}, "ja": {"text": "5の前は何ですか？"}, "zh": {"text": "5前面是几？"}}', '{"bank_key": "Q32", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('NUMBER_BIGGER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which number is bigger?"}, "vi": {"text": "Số nào lớn hơn?"}, "ja": {"text": "どの数字が大きいですか？"}, "zh": {"text": "哪个数字更大？"}}', '{"bank_key": "Q33", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('NUMBER_SMALLER', 'TEMPLATE', 'COMPARISON', 'AGE_4_5', 2, '{"en": {"text": "Which number is smaller?"}, "vi": {"text": "Số nào nhỏ hơn?"}, "ja": {"text": "どの数字が小さいですか？"}, "zh": {"text": "哪个数字更小？"}}', '{"bank_key": "Q34", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('PLUS_1_1', 'TEMPLATE', 'SIMPLE_ADDITION', 'AGE_4_5', 2, '{"en": {"text": "What is 1 plus 1?"}, "vi": {"text": "1 cộng 1 bằng mấy?"}, "ja": {"text": "1たす1はいくつですか？"}, "zh": {"text": "1加1等于几？"}}', '{"bank_key": "Q35", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('PLUS_2_1', 'TEMPLATE', 'SIMPLE_ADDITION', 'AGE_4_5', 2, '{"en": {"text": "What is 2 plus 1?"}, "vi": {"text": "2 cộng 1 bằng mấy?"}, "ja": {"text": "2たす1はいくつですか？"}, "zh": {"text": "2加1等于几？"}}', '{"bank_key": "Q36", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('PLUS_3_1', 'TEMPLATE', 'SIMPLE_ADDITION', 'AGE_4_5', 2, '{"en": {"text": "What is 3 plus 1?"}, "vi": {"text": "3 cộng 1 bằng mấy?"}, "ja": {"text": "3たす1はいくつですか？"}, "zh": {"text": "3加1等于几？"}}', '{"bank_key": "Q37", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('MINUS_3_1', 'TEMPLATE', 'SIMPLE_SUBTRACTION', 'AGE_4_5', 2, '{"en": {"text": "What is 3 minus 1?"}, "vi": {"text": "3 trừ 1 bằng mấy?"}, "ja": {"text": "3ひく1はいくつですか？"}, "zh": {"text": "3减1等于几？"}}', '{"bank_key": "Q38", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('MINUS_4_1', 'TEMPLATE', 'SIMPLE_SUBTRACTION', 'AGE_4_5', 2, '{"en": {"text": "What is 4 minus 1?"}, "vi": {"text": "4 trừ 1 bằng mấy?"}, "ja": {"text": "4ひく1はいくつですか？"}, "zh": {"text": "4减1等于几？"}}', '{"bank_key": "Q39", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE),
    ('MISSING_NUMBER', 'TEMPLATE', 'SEQUENCE', 'AGE_4_5', 2, '{"en": {"text": "Which number is missing?"}, "vi": {"text": "Số nào còn thiếu?"}, "ja": {"text": "どの数字が足りませんか？"}, "zh": {"text": "缺少哪个数字？"}}', '{"bank_key": "Q40", "status": "PLANNED", "reason": "AGE_4_5: needs the math generator"}', FALSE)
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
       TRUE, 'TPL_' || tpl.code || '_' || i.code, tpl.age_group, tpl.question_type, tpl.id
FROM question_templates tpl
CROSS JOIN LATERAL jsonb_array_elements_text(tpl.generation -> 'topics') AS tc(code)
JOIN topics t ON t.code = tc.code AND t.is_active
JOIN answer_items i ON i.metadata ->> 'category' = lower(t.code) AND i.is_active
JOIN game_modes m ON m.code = 'GUESS'
WHERE tpl.kind = 'TEMPLATE' AND tpl.is_active
ON CONFLICT (id) DO UPDATE
    SET question_text = EXCLUDED.question_text, metadata = EXCLUDED.metadata, template_id = EXCLUDED.template_id,
        question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty,
        is_active = TRUE, updated_at = now();

-- A template that was switched off takes its generated questions with it (rows are kept for the history).
UPDATE game_questions q
SET is_active = FALSE, updated_at = now()
FROM question_templates tpl
WHERE q.template_id = tpl.id AND tpl.kind = 'TEMPLATE' AND NOT tpl.is_active AND q.is_active;
