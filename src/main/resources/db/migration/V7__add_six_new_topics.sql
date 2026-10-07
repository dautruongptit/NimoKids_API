-- Add 6 new topics from the Question Bank review: Vegetables, Body Parts, Weather, Family,
-- Music & Instruments, Daily Activities. These are structural migrations (not seed data) so they
-- run in every environment.

INSERT INTO topics (code, name, slug, description, min_age, max_age, display_order) VALUES
    ('VEGETABLES',        'Vegetables',           'vegetables',           'Healthy veggies',                2, 5, 15),
    ('BODY_PARTS',        'Body Parts',           'body-parts',           'Head, shoulders and more',        1, 5, 16),
    ('WEATHER',           'Weather',              'weather',              'Sun, rain, snow and wind',         2, 5, 17),
    ('FAMILY',            'Family',               'family',               'People who love you',             1, 5, 18),
    ('MUSIC_INSTRUMENTS', 'Music & Instruments',  'music-instruments',    'Sounds and rhythms',              2, 5, 19),
    ('DAILY_ACTIVITIES',  'Daily Activities',      'daily-activities',     'Things we do every day',          1, 5, 20)
ON CONFLICT (code) DO UPDATE
    SET name = EXCLUDED.name, slug = EXCLUDED.slug, description = EXCLUDED.description,
        min_age = EXCLUDED.min_age, max_age = EXCLUDED.max_age, display_order = EXCLUDED.display_order,
        is_active = TRUE;
