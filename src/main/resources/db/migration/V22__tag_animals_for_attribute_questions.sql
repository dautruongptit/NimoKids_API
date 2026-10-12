-- Tags that let "Who can fly?" and "Who can swim?" have exactly one kind of right answer.
--   animal   : the item is an animal (the pool all wrong answers of those questions come from)
--   can_fly  : true / false for every animal (21 fly, 46 do not)
--   can_swim : true / false only where it is beyond doubt for a child; doubtful animals get NO value, and an item without
--              the tag is never offered as a wrong answer (wrong answers are found by explicit tag matching only).
-- The questions themselves are generated in R__zz_templates.sql (section 3c).

UPDATE answer_items
SET metadata = metadata || '{"animal": true}'::jsonb, updated_at = now()
WHERE metadata ->> 'category' IN ('land_animals', 'sea_animals', 'fly_animals')
  AND (metadata -> 'animal') IS DISTINCT FROM 'true'::jsonb;

UPDATE answer_items
SET metadata = metadata || '{"can_fly": true}'::jsonb, updated_at = now()
WHERE code IN (
    'FLY_ANIMALS_BAT',
    'FLY_ANIMALS_BEE',
    'FLY_ANIMALS_BUTTERFLY',
    'FLY_ANIMALS_CROW',
    'FLY_ANIMALS_DRAGONFLY',
    'FLY_ANIMALS_DUCK',
    'FLY_ANIMALS_EAGLE',
    'FLY_ANIMALS_FIREFLY',
    'FLY_ANIMALS_FLY',
    'FLY_ANIMALS_GRASSHOPPER',
    'FLY_ANIMALS_MOSQUITO',
    'FLY_ANIMALS_OWL',
    'FLY_ANIMALS_PARROT',
    'FLY_ANIMALS_PELICAN',
    'FLY_ANIMALS_PIGEON',
    'FLY_ANIMALS_SPARROW',
    'FLY_ANIMALS_STORK',
    'FLY_ANIMALS_SWALLOW',
    'FLY_ANIMALS_SWAN',
    'FLY_ANIMALS_SWIFT',
    'FLY_ANIMALS_WOODPECKER');

UPDATE answer_items
SET metadata = metadata || '{"can_fly": false}'::jsonb, updated_at = now()
WHERE metadata ->> 'category' IN ('land_animals', 'sea_animals');

UPDATE answer_items
SET metadata = metadata || '{"can_swim": true}'::jsonb, updated_at = now()
WHERE code IN (
    'FLY_ANIMALS_DUCK',
    'FLY_ANIMALS_SWAN',
    'LAND_ANIMALS_DOG',
    'LAND_ANIMALS_FROG',
    'LAND_ANIMALS_HIPPO',
    'SEA_ANIMALS_BLUE_WHALE',
    'SEA_ANIMALS_CRAB',
    'SEA_ANIMALS_CROCODILE',
    'SEA_ANIMALS_DOLPHIN',
    'SEA_ANIMALS_GOLDFISH',
    'SEA_ANIMALS_GREEN_FROG',
    'SEA_ANIMALS_JELLYFISH',
    'SEA_ANIMALS_LOBSTER',
    'SEA_ANIMALS_OCTOPUS',
    'SEA_ANIMALS_OTTER',
    'SEA_ANIMALS_PENGUIN',
    'SEA_ANIMALS_SEAHORSE',
    'SEA_ANIMALS_SEAL',
    'SEA_ANIMALS_SEA_CRAB',
    'SEA_ANIMALS_SHARK',
    'SEA_ANIMALS_SHRIMP',
    'SEA_ANIMALS_SQUID',
    'SEA_ANIMALS_TURTLE',
    'SEA_ANIMALS_WHALE');

UPDATE answer_items
SET metadata = metadata || '{"can_swim": false}'::jsonb, updated_at = now()
WHERE code IN (
    'FLY_ANIMALS_BAT',
    'FLY_ANIMALS_BEE',
    'FLY_ANIMALS_BUTTERFLY',
    'FLY_ANIMALS_CROW',
    'FLY_ANIMALS_DRAGONFLY',
    'FLY_ANIMALS_EAGLE',
    'FLY_ANIMALS_FIREFLY',
    'FLY_ANIMALS_FLY',
    'FLY_ANIMALS_GRASSHOPPER',
    'FLY_ANIMALS_MOSQUITO',
    'FLY_ANIMALS_OWL',
    'FLY_ANIMALS_PARROT',
    'FLY_ANIMALS_PIGEON',
    'FLY_ANIMALS_SPARROW',
    'FLY_ANIMALS_STORK',
    'FLY_ANIMALS_SWALLOW',
    'FLY_ANIMALS_SWIFT',
    'FLY_ANIMALS_WOODPECKER',
    'LAND_ANIMALS_CAMEL',
    'LAND_ANIMALS_CAT',
    'LAND_ANIMALS_CHICKEN',
    'LAND_ANIMALS_DINOSAUR',
    'LAND_ANIMALS_GIRAFFE',
    'LAND_ANIMALS_KANGAROO',
    'LAND_ANIMALS_MONKEY',
    'LAND_ANIMALS_OSTRICH',
    'LAND_ANIMALS_RABBIT',
    'LAND_ANIMALS_ZEBRA');
