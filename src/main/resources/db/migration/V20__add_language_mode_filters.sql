-- Scaffolding for "learn English" (VI_EN): a language mode only offers the topics and the words that are marked for it.
--   topics.supported_modes      : the language modes (VI, EN, VI_EN) a topic can be played in. GET /topics?mode=... and
--                                 the session engine only use topics whose array contains the requested mode.
--   answer_items.is_basic_english: TRUE = a basic English word a toddler can take in ("Cat"), FALSE = a hard one
--                                 ("Ostrich"). A mode that restricts to basic English (VI_EN) never uses the FALSE ones.
-- The rules live in these columns, so changing what VI_EN offers is a data change, not a code change.

ALTER TABLE topics
    ADD COLUMN supported_modes VARCHAR(20)[] NOT NULL DEFAULT ARRAY['VI', 'EN', 'VI_EN']::VARCHAR(20)[];
ALTER TABLE topics
    ADD CONSTRAINT ck_topics_supported_modes
        CHECK (cardinality(supported_modes) > 0 AND supported_modes <@ ARRAY['VI', 'EN', 'VI_EN']::VARCHAR(20)[]);

ALTER TABLE answer_items
    ADD COLUMN is_basic_english BOOLEAN NOT NULL DEFAULT TRUE;

-- Core topics of "learn English": Colors, Animals (with their sub-topics) and Numbers (inside Math). Every other topic is
-- played in Vietnamese (and full English) only. A parent only reaches the sub-topics that support the mode too, so Math
-- offers just Numbers in VI_EN.
UPDATE topics
SET supported_modes = ARRAY['VI', 'EN']::VARCHAR(20)[], updated_at = now()
WHERE code NOT IN ('COLORS', 'ANIMALS', 'LAND_ANIMALS', 'SEA_ANIMALS', 'FLY_ANIMALS', 'MATH', 'NUMBERS')
  AND supported_modes IS DISTINCT FROM ARRAY['VI', 'EN']::VARCHAR(20)[];

-- Hard English words in the core topics (colors and numbers are all basic). Items of the other topics keep the default
-- TRUE: review them before a topic is opened to VI_EN.
UPDATE answer_items
SET is_basic_english = FALSE, updated_at = now()
WHERE code IN (
    'LAND_ANIMALS_OSTRICH',
    'SEA_ANIMALS_CLAM', 'SEA_ANIMALS_SQUID', 'SEA_ANIMALS_OTTER', 'SEA_ANIMALS_LOBSTER',
    'SEA_ANIMALS_SEA_CRAB', 'SEA_ANIMALS_GREEN_FROG',
    'FLY_ANIMALS_PELICAN', 'FLY_ANIMALS_WOODPECKER', 'FLY_ANIMALS_SPARROW', 'FLY_ANIMALS_SWIFT',
    'FLY_ANIMALS_SWALLOW', 'FLY_ANIMALS_GRASSHOPPER', 'FLY_ANIMALS_CROW', 'FLY_ANIMALS_STORK',
    'FLY_ANIMALS_MOSQUITO', 'FLY_ANIMALS_FIREFLY', 'FLY_ANIMALS_DRAGONFLY')
  AND is_basic_english;
