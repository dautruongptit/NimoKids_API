-- DEVELOPMENT SEED DATA. Loaded only when the dev profile adds classpath:db/seed to the Flyway locations.
-- NEVER ship this to production: it contains well-known passwords (admin123).
-- Repeatable and idempotent: every insert uses fixed ids or ON CONFLICT DO NOTHING, so re-running is safe.

-- ---------------------------------------------------------------------------
-- Admin accounts. Password is a bcrypt hash (cost 10) of "admin123".
--   admin@nimokids.local  SUPER_ADMIN  can call every admin API
--   staff@nimokids.local  ADMIN        lets you see the 403 of a SUPER_ADMIN-only API
-- ---------------------------------------------------------------------------
INSERT INTO admin_users (id, email, password_hash, role) VALUES
    ('e0000000-0000-4000-8000-000000000001', 'admin@nimokids.local',
     '$2a$10$6ek66jj7pERrG5Ykaf/OqOMCj.sV3bxhFXWqBmQJzj0ylHuYyZYBG', 'SUPER_ADMIN'),
    ('e0000000-0000-4000-8000-000000000002', 'staff@nimokids.local',
     '$2a$10$6ek66jj7pERrG5Ykaf/OqOMCj.sV3bxhFXWqBmQJzj0ylHuYyZYBG', 'ADMIN')
ON CONFLICT DO NOTHING;

-- The account created by earlier seeds had the role ADMIN; the main dev account is a SUPER_ADMIN.
UPDATE admin_users SET role = 'SUPER_ADMIN'
WHERE id = 'e0000000-0000-4000-8000-000000000001' AND role <> 'SUPER_ADMIN';

-- ---------------------------------------------------------------------------
-- Game mode (one generic mode so every topic can be played with it)
-- ---------------------------------------------------------------------------
INSERT INTO game_modes (id, code, name, description) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'GUESS', 'Guess', 'Look, listen and pick the right answer')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Topics
-- ---------------------------------------------------------------------------
INSERT INTO topics (id, code, name, slug, description, min_age, max_age, display_order) VALUES
    ('a1000000-0000-4000-8000-000000000001', 'ANIMALS', 'Animals', 'animals', 'Meet friendly animals', 1, 5, 1),
    ('a1000000-0000-4000-8000-000000000002', 'MATH', 'Math', 'math', 'Count and add with fun', 3, 5, 2)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Questions: 5 per topic, because a topic needs at least 5 playable questions to be offered (BR-002)
-- ---------------------------------------------------------------------------
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, difficulty, min_age, max_age, time_limit_seconds, display_order)
VALUES
    ('c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
     'Which animal says Meow?', 1, 1, 5, 5, 1),
    ('c1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
     'Which animal has a long trunk?', 1, 1, 5, 5, 2),
    ('c1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
     'Which animal can fly?', 1, 1, 5, 5, 3),
    ('c1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
     'Which animal lives in water?', 1, 1, 5, 5, 4),
    ('c1000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
     'Which animal gives us milk?', 2, 2, 5, 5, 5),
    ('c2000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001',
     'What is 1 + 1?', 1, 3, 5, 5, 1),
    ('c2000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001',
     'What is 2 + 3?', 2, 3, 5, 5, 2),
    ('c2000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001',
     'What comes after 3?', 1, 3, 5, 5, 3),
    ('c2000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001',
     'How many fingers are on one hand?', 1, 3, 5, 5, 4),
    ('c2000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001',
     'What is 5 - 2?', 2, 4, 5, 5, 5)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Options: 4 per question, exactly one correct (unique on (question_id, display_order))
-- ---------------------------------------------------------------------------
INSERT INTO question_options (question_id, option_text, is_correct, display_order) VALUES
    ('c1000000-0000-4000-8000-000000000001', 'Cat', TRUE, 1),
    ('c1000000-0000-4000-8000-000000000001', 'Dog', FALSE, 2),
    ('c1000000-0000-4000-8000-000000000001', 'Cow', FALSE, 3),
    ('c1000000-0000-4000-8000-000000000001', 'Duck', FALSE, 4),

    ('c1000000-0000-4000-8000-000000000002', 'Elephant', TRUE, 1),
    ('c1000000-0000-4000-8000-000000000002', 'Lion', FALSE, 2),
    ('c1000000-0000-4000-8000-000000000002', 'Monkey', FALSE, 3),
    ('c1000000-0000-4000-8000-000000000002', 'Rabbit', FALSE, 4),

    ('c1000000-0000-4000-8000-000000000003', 'Bird', TRUE, 1),
    ('c1000000-0000-4000-8000-000000000003', 'Fish', FALSE, 2),
    ('c1000000-0000-4000-8000-000000000003', 'Cow', FALSE, 3),
    ('c1000000-0000-4000-8000-000000000003', 'Dog', FALSE, 4),

    ('c1000000-0000-4000-8000-000000000004', 'Fish', TRUE, 1),
    ('c1000000-0000-4000-8000-000000000004', 'Horse', FALSE, 2),
    ('c1000000-0000-4000-8000-000000000004', 'Pig', FALSE, 3),
    ('c1000000-0000-4000-8000-000000000004', 'Sheep', FALSE, 4),

    ('c1000000-0000-4000-8000-000000000005', 'Cow', TRUE, 1),
    ('c1000000-0000-4000-8000-000000000005', 'Tiger', FALSE, 2),
    ('c1000000-0000-4000-8000-000000000005', 'Frog', FALSE, 3),
    ('c1000000-0000-4000-8000-000000000005', 'Snake', FALSE, 4),

    ('c2000000-0000-4000-8000-000000000001', '2', TRUE, 1),
    ('c2000000-0000-4000-8000-000000000001', '1', FALSE, 2),
    ('c2000000-0000-4000-8000-000000000001', '3', FALSE, 3),
    ('c2000000-0000-4000-8000-000000000001', '4', FALSE, 4),

    ('c2000000-0000-4000-8000-000000000002', '5', TRUE, 1),
    ('c2000000-0000-4000-8000-000000000002', '4', FALSE, 2),
    ('c2000000-0000-4000-8000-000000000002', '6', FALSE, 3),
    ('c2000000-0000-4000-8000-000000000002', '2', FALSE, 4),

    ('c2000000-0000-4000-8000-000000000003', '4', TRUE, 1),
    ('c2000000-0000-4000-8000-000000000003', '2', FALSE, 2),
    ('c2000000-0000-4000-8000-000000000003', '5', FALSE, 3),
    ('c2000000-0000-4000-8000-000000000003', '6', FALSE, 4),

    ('c2000000-0000-4000-8000-000000000004', '5', TRUE, 1),
    ('c2000000-0000-4000-8000-000000000004', '4', FALSE, 2),
    ('c2000000-0000-4000-8000-000000000004', '3', FALSE, 3),
    ('c2000000-0000-4000-8000-000000000004', '10', FALSE, 4),

    ('c2000000-0000-4000-8000-000000000005', '3', TRUE, 1),
    ('c2000000-0000-4000-8000-000000000005', '2', FALSE, 2),
    ('c2000000-0000-4000-8000-000000000005', '4', FALSE, 3),
    ('c2000000-0000-4000-8000-000000000005', '7', FALSE, 4)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Stickers. FIRST_GAME and PERFECT_SCORE are awarded by rules in the service.
-- ANIMAL_LOVER has no award rule defined yet in the business documents.
-- ---------------------------------------------------------------------------
INSERT INTO stickers (id, code, name, description, rarity) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'FIRST_GAME', 'First Game', 'You finished your first game!', 'COMMON'),
    ('d0000000-0000-4000-8000-000000000002', 'PERFECT_SCORE', 'Perfect Score', 'All answers correct!', 'RARE'),
    ('d0000000-0000-4000-8000-000000000003', 'ANIMAL_LOVER', 'Animal Lover', 'You love animals!', 'COMMON')
ON CONFLICT DO NOTHING;
