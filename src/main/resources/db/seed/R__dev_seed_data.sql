-- DEVELOPMENT SEED DATA. Loaded only when the dev profile adds classpath:db/seed to the Flyway locations.
-- NEVER ship this to production: it contains well-known passwords (admin123).
-- Repeatable and idempotent: rows are keyed by code / deterministic id and upserted, so re-running is safe.

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

UPDATE admin_users SET role = 'SUPER_ADMIN'
WHERE id = 'e0000000-0000-4000-8000-000000000001' AND role <> 'SUPER_ADMIN';

-- ---------------------------------------------------------------------------
-- Game mode (one generic mode so every topic can be played with it)
-- ---------------------------------------------------------------------------
INSERT INTO game_modes (id, code, name, description) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'GUESS', 'Guess', 'Look, listen and pick the right answer')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Topics: a tree (parent_id). These 14 are ROOT topics; sub-groups can be added under them later and a root then
-- draws questions from its whole subtree.
-- ---------------------------------------------------------------------------
INSERT INTO topics (code, name, slug, description, min_age, max_age, display_order) VALUES
    ('ANIMALS', 'Animals', 'animals', 'Meet friendly animals', 1, 5, 1),
    ('FRUITS', 'Fruits', 'fruits', 'Sweet and juicy', 1, 5, 2),
    ('VEHICLES', 'Vehicles', 'vehicles', 'Things that go', 1, 5, 3),
    ('COLORS', 'Colors', 'colors', 'Learn the colors', 1, 5, 4),
    ('SHAPES', 'Shapes', 'shapes', 'Circles, squares and more', 2, 5, 5),
    ('NUMBERS', 'Numbers', 'numbers', 'Count with me', 2, 5, 6),
    ('ALPHABET', 'Alphabet', 'alphabet', 'Learn your letters', 3, 5, 7),
    ('FOOD', 'Food', 'food', 'Yummy things to eat', 2, 5, 8),
    ('TOYS', 'Toys', 'toys', 'Time to play', 1, 5, 9),
    ('CLOTHES', 'Clothes', 'clothes', 'What do we wear?', 2, 5, 10),
    ('HOME', 'Home', 'home', 'Things at home', 2, 5, 11),
    ('NATURE', 'Nature', 'nature', 'Sun, rain and trees', 2, 5, 12),
    ('SEA_ANIMALS', 'Sea Animals', 'sea-animals', 'Life under the sea', 2, 5, 13),
    ('FARM_ANIMALS', 'Farm Animals', 'farm-animals', 'Down on the farm', 1, 5, 14)
ON CONFLICT (code) DO UPDATE
    SET name = EXCLUDED.name, slug = EXCLUDED.slug, description = EXCLUDED.description, min_age = EXCLUDED.min_age,
        max_age = EXCLUDED.max_age, display_order = EXCLUDED.display_order, parent_id = NULL, is_active = TRUE;

-- ---------------------------------------------------------------------------
-- Answer items: the vocabulary of every group. Tags are EXPLICIT positive facts:
--   category : the group (wrong answers are drawn only from the same group)
--   features : EVERYTHING that is true about the item for the questions of its group. An item that is also a fair
--              answer to a question carries that question's feature, so it is never shown as a wrong answer.
--   emoji    : picture fallback until image assets exist
-- ---------------------------------------------------------------------------
INSERT INTO answer_items (code, name, metadata) VALUES
    ('ANIMALS_CAT', 'Cat', '{"category":"animals","emoji":"🐱","features":["says_meow"]}'),
    ('ANIMALS_DOG', 'Dog', '{"category":"animals","emoji":"🐶","features":["says_woof"]}'),
    ('ANIMALS_ELEPHANT', 'Elephant', '{"category":"animals","emoji":"🐘","features":["long_trunk"]}'),
    ('ANIMALS_GIRAFFE', 'Giraffe', '{"category":"animals","emoji":"🦒","features":["long_neck"]}'),
    ('ANIMALS_ZEBRA', 'Zebra', '{"category":"animals","emoji":"🦓","features":["black_white_stripes"]}'),
    ('ANIMALS_LION', 'Lion', '{"category":"animals","emoji":"🦁","features":["has_mane"]}'),
    ('ANIMALS_RABBIT', 'Rabbit', '{"category":"animals","emoji":"🐰","features":["can_hop"]}'),
    ('ANIMALS_COW', 'Cow', '{"category":"animals","emoji":"🐮","features":["gives_milk"]}'),
    ('ANIMALS_TURTLE', 'Turtle', '{"category":"animals","emoji":"🐢","features":["has_shell"]}'),
    ('ANIMALS_MONKEY', 'Monkey', '{"category":"animals","emoji":"🐵","features":["swings_from_trees"]}'),
    ('ANIMALS_PIG', 'Pig', '{"category":"animals","emoji":"🐷","features":[]}'),
    ('ANIMALS_BEAR', 'Bear', '{"category":"animals","emoji":"🐻","features":[]}'),
    ('ANIMALS_MOUSE', 'Mouse', '{"category":"animals","emoji":"🐭","features":[]}'),
    ('ANIMALS_FISH', 'Fish', '{"category":"animals","emoji":"🐟","features":[]}'),
    ('FRUITS_BANANA', 'Banana', '{"category":"fruits","emoji":"🍌","features":["yellow_long","grows_in_bunch"]}'),
    ('FRUITS_APPLE', 'Apple', '{"category":"fruits","emoji":"🍎","features":["red_round"]}'),
    ('FRUITS_ORANGE', 'Orange', '{"category":"fruits","emoji":"🍊","features":["orange_round"]}'),
    ('FRUITS_GRAPE', 'Grape', '{"category":"fruits","emoji":"🍇","features":["grows_in_bunch"]}'),
    ('FRUITS_WATERMELON', 'Watermelon', '{"category":"fruits","emoji":"🍉","features":["green_outside_red_inside"]}'),
    ('FRUITS_PINEAPPLE', 'Pineapple', '{"category":"fruits","emoji":"🍍","features":["has_crown"]}'),
    ('FRUITS_STRAWBERRY', 'Strawberry', '{"category":"fruits","emoji":"🍓","features":["red_round","seeds_outside"]}'),
    ('FRUITS_PEAR', 'Pear', '{"category":"fruits","emoji":"🍐","features":["pear_shaped"]}'),
    ('FRUITS_KIWI', 'Kiwi', '{"category":"fruits","emoji":"🥝","features":["hairy_green_inside"]}'),
    ('FRUITS_PLUM', 'Plum', '{"category":"fruits","emoji":"🟣","features":["big_pit_purple_or_yellow"]}'),
    ('FRUITS_LEMON', 'Lemon', '{"category":"fruits","emoji":"🍋","features":[]}'),
    ('FRUITS_CHERRY', 'Cherry', '{"category":"fruits","emoji":"🍒","features":["red_round","grows_in_bunch","big_pit_purple_or_yellow"]}'),
    ('VEHICLES_AIRPLANE', 'Airplane', '{"category":"vehicles","emoji":"✈️","features":["flies_in_sky"]}'),
    ('VEHICLES_BOAT', 'Boat', '{"category":"vehicles","emoji":"⛵","features":["travels_on_water"]}'),
    ('VEHICLES_TRAIN', 'Train', '{"category":"vehicles","emoji":"🚆","features":["goes_choo_choo"]}'),
    ('VEHICLES_BICYCLE', 'Bicycle', '{"category":"vehicles","emoji":"🚲","features":["two_wheels_pedals"]}'),
    ('VEHICLES_SCHOOL_BUS', 'School Bus', '{"category":"vehicles","emoji":"🚌","features":["takes_children_to_school","four_wheels_carries_people"]}'),
    ('VEHICLES_CAR', 'Car', '{"category":"vehicles","emoji":"🚗","features":["four_wheels_carries_people"]}'),
    ('VEHICLES_FIRE_TRUCK', 'Fire Truck', '{"category":"vehicles","emoji":"🚒","features":["four_wheels_carries_people","has_ladder_fights_fires"]}'),
    ('VEHICLES_AMBULANCE', 'Ambulance', '{"category":"vehicles","emoji":"🚑","features":["four_wheels_carries_people","takes_sick_to_hospital"]}'),
    ('VEHICLES_MOTORCYCLE', 'Motorcycle', '{"category":"vehicles","emoji":"🏍️","features":["two_wheels_motor"]}'),
    ('VEHICLES_HELICOPTER', 'Helicopter', '{"category":"vehicles","emoji":"🚁","features":["flies_in_sky","big_blades"]}'),
    ('VEHICLES_TRACTOR', 'Tractor', '{"category":"vehicles","emoji":"🚜","features":[]}'),
    ('VEHICLES_POLICE_CAR', 'Police Car', '{"category":"vehicles","emoji":"🚓","features":["four_wheels_carries_people"]}'),
    ('COLORS_BLUE', 'Blue', '{"category":"colors","emoji":"🔵","features":["sky_clear_day","night_sky"]}'),
    ('COLORS_DARK_BLUE', 'Dark Blue', '{"category":"colors","emoji":"🌃","features":["sky_clear_day","night_sky"]}'),
    ('COLORS_YELLOW', 'Yellow', '{"category":"colors","emoji":"🟡","features":["banana_color","lemon_color"]}'),
    ('COLORS_GREEN', 'Green', '{"category":"colors","emoji":"🟢","features":["grass_color"]}'),
    ('COLORS_RED', 'Red', '{"category":"colors","emoji":"🔴","features":["strawberry_color"]}'),
    ('COLORS_ORANGE', 'Orange', '{"category":"colors","emoji":"🟠","features":["orange_fruit_color","pumpkin_color"]}'),
    ('COLORS_PURPLE', 'Purple', '{"category":"colors","emoji":"🟣","features":["eggplant_color"]}'),
    ('COLORS_WHITE', 'White', '{"category":"colors","emoji":"⚪","features":["snow_color"]}'),
    ('COLORS_PINK', 'Pink', '{"category":"colors","emoji":"🌸","features":[]}'),
    ('COLORS_BROWN', 'Brown', '{"category":"colors","emoji":"🟤","features":[]}'),
    ('SHAPES_CIRCLE', 'Circle', '{"category":"shapes","emoji":"⚪","features":["is_round","no_corners"]}'),
    ('SHAPES_TRIANGLE', 'Triangle', '{"category":"shapes","emoji":"🔺","features":["three_sides"]}'),
    ('SHAPES_SQUARE', 'Square', '{"category":"shapes","emoji":"🟧","features":["four_equal_sides"]}'),
    ('SHAPES_RECTANGLE', 'Rectangle', '{"category":"shapes","emoji":"🚪","features":["looks_like_door"]}'),
    ('SHAPES_OVAL', 'Oval', '{"category":"shapes","emoji":"🥚","features":["is_round","looks_like_egg","no_corners"]}'),
    ('SHAPES_PENTAGON', 'Pentagon', '{"category":"shapes","emoji":"⬟","features":["five_sides"]}'),
    ('SHAPES_HEXAGON', 'Hexagon', '{"category":"shapes","emoji":"⬢","features":["six_sides"]}'),
    ('SHAPES_HEART', 'Heart', '{"category":"shapes","emoji":"❤️","features":["looks_like_heart"]}'),
    ('SHAPES_STAR', 'Star', '{"category":"shapes","emoji":"⭐","features":["looks_like_star"]}'),
    ('NUMBERS_ONE', 'One', '{"category":"numbers","emoji":"1️⃣","features":["count_one_apple"]}'),
    ('NUMBERS_TWO', 'Two', '{"category":"numbers","emoji":"2️⃣","features":["count_two_balls","after_one","before_three","bicycle_wheels"]}'),
    ('NUMBERS_THREE', 'Three', '{"category":"numbers","emoji":"3️⃣","features":["count_three_stars"]}'),
    ('NUMBERS_FOUR', 'Four', '{"category":"numbers","emoji":"4️⃣","features":["count_four_ducks","dog_legs"]}'),
    ('NUMBERS_FIVE', 'Five', '{"category":"numbers","emoji":"5️⃣","features":["fingers_on_hand","after_four"]}'),
    ('NUMBERS_SIX', 'Six', '{"category":"numbers","emoji":"6️⃣","features":[]}'),
    ('NUMBERS_SEVEN', 'Seven', '{"category":"numbers","emoji":"7️⃣","features":[]}'),
    ('NUMBERS_EIGHT', 'Eight', '{"category":"numbers","emoji":"8️⃣","features":[]}'),
    ('ALPHABET_A', 'A', '{"category":"alphabet","emoji":"A","features":["starts_apple"]}'),
    ('ALPHABET_B', 'B', '{"category":"alphabet","emoji":"B","features":["starts_ball","after_a","before_c"]}'),
    ('ALPHABET_C', 'C', '{"category":"alphabet","emoji":"C","features":["starts_cat"]}'),
    ('ALPHABET_D', 'D', '{"category":"alphabet","emoji":"D","features":["starts_dog"]}'),
    ('ALPHABET_E', 'E', '{"category":"alphabet","emoji":"E","features":["starts_elephant"]}'),
    ('ALPHABET_F', 'F', '{"category":"alphabet","emoji":"F","features":["starts_fish"]}'),
    ('ALPHABET_G', 'G', '{"category":"alphabet","emoji":"G","features":["starts_goat"]}'),
    ('ALPHABET_H', 'H', '{"category":"alphabet","emoji":"H","features":["starts_hat"]}'),
    ('ALPHABET_I', 'I', '{"category":"alphabet","emoji":"I","features":[]}'),
    ('ALPHABET_J', 'J', '{"category":"alphabet","emoji":"J","features":[]}'),
    ('FOOD_SANDWICH', 'Sandwich', '{"category":"food","emoji":"🥪","features":["bread_and_cheese"]}'),
    ('FOOD_PIZZA', 'Pizza', '{"category":"food","emoji":"🍕","features":["bread_and_cheese","round_cheese_tomato"]}'),
    ('FOOD_SOUP', 'Soup', '{"category":"food","emoji":"🍲","features":["eaten_with_spoon_in_bowl"]}'),
    ('FOOD_ICE_CREAM', 'Ice Cream', '{"category":"food","emoji":"🍦","features":["eaten_with_spoon_in_bowl","cold_sweet_flavors","birthday_sweet"]}'),
    ('FOOD_RICE', 'Rice', '{"category":"food","emoji":"🍚","features":["eaten_with_spoon_in_bowl","made_from_rice"]}'),
    ('FOOD_NOODLES', 'Noodles', '{"category":"food","emoji":"🍜","features":["eaten_with_spoon_in_bowl","made_from_rice","long_with_sauce"]}'),
    ('FOOD_OMELET', 'Omelet', '{"category":"food","emoji":"🍳","features":["made_from_eggs"]}'),
    ('FOOD_CAKE', 'Cake', '{"category":"food","emoji":"🎂","features":["birthday_sweet"]}'),
    ('FOOD_FRENCH_FRIES', 'French Fries', '{"category":"food","emoji":"🍟","features":["long_with_sauce","fried_potatoes"]}'),
    ('FOOD_HONEY', 'Honey', '{"category":"food","emoji":"🍯","features":["made_by_bees"]}'),
    ('FOOD_SALAD', 'Salad', '{"category":"food","emoji":"🥗","features":[]}'),
    ('FOOD_POPCORN', 'Popcorn', '{"category":"food","emoji":"🍿","features":[]}'),
    ('TOYS_BALL', 'Ball', '{"category":"toys","emoji":"⚽","features":["can_roll","push_around"]}'),
    ('TOYS_TEDDY_BEAR', 'Teddy Bear', '{"category":"toys","emoji":"🧸","features":["can_hug"]}'),
    ('TOYS_TOY_CAR', 'Toy Car', '{"category":"toys","emoji":"🚙","features":["can_roll","four_wheels","push_around"]}'),
    ('TOYS_BLOCKS', 'Blocks', '{"category":"toys","emoji":"🧱","features":["build_with","put_together_pieces"]}'),
    ('TOYS_TOY_GUITAR', 'Toy Guitar', '{"category":"toys","emoji":"🎸","features":["strings_instrument"]}'),
    ('TOYS_PAPER_PLANE', 'Paper Plane', '{"category":"toys","emoji":"✈️","features":["flies_when_thrown"]}'),
    ('TOYS_SPINNER', 'Spinner', '{"category":"toys","emoji":"🌀","features":["spins_around"]}'),
    ('TOYS_PUZZLE', 'Puzzle', '{"category":"toys","emoji":"🧩","features":["build_with","put_together_pieces"]}'),
    ('TOYS_DOLL', 'Doll', '{"category":"toys","emoji":"🪆","features":["can_hug","looks_like_baby"]}'),
    ('TOYS_DRUM', 'Drum', '{"category":"toys","emoji":"🥁","features":[]}'),
    ('TOYS_KITE', 'Kite', '{"category":"toys","emoji":"🪁","features":[]}'),
    ('CLOTHES_HAT', 'Hat', '{"category":"clothes","emoji":"🎩","features":["worn_on_head"]}'),
    ('CLOTHES_SHOES', 'Shoes', '{"category":"clothes","emoji":"👟","features":["worn_on_feet"]}'),
    ('CLOTHES_JACKET', 'Jacket', '{"category":"clothes","emoji":"🧥","features":["worn_when_cold"]}'),
    ('CLOTHES_SWIMSUIT', 'Swimsuit', '{"category":"clothes","emoji":"🩱","features":["worn_to_swim"]}'),
    ('CLOTHES_GLOVES', 'Gloves', '{"category":"clothes","emoji":"🧤","features":["worn_when_cold","worn_on_hands"]}'),
    ('CLOTHES_SCARF', 'Scarf', '{"category":"clothes","emoji":"🧣","features":["worn_when_cold","worn_on_neck"]}'),
    ('CLOTHES_RAINCOAT', 'Raincoat', '{"category":"clothes","emoji":"🌧️","features":["worn_when_rain"]}'),
    ('CLOTHES_PANTS', 'Pants', '{"category":"clothes","emoji":"👖","features":["worn_on_legs"]}'),
    ('CLOTHES_SUNGLASSES', 'Sunglasses', '{"category":"clothes","emoji":"🕶️","features":["protects_eyes_from_sun"]}'),
    ('CLOTHES_PAJAMAS', 'Pajamas', '{"category":"clothes","emoji":"🛌","features":["worn_to_sleep"]}'),
    ('CLOTHES_SOCKS', 'Socks', '{"category":"clothes","emoji":"🧦","features":["worn_on_feet"]}'),
    ('CLOTHES_BOOTS', 'Boots', '{"category":"clothes","emoji":"🥾","features":["worn_on_feet","worn_when_cold","worn_when_rain"]}'),
    ('CLOTHES_T_SHIRT', 'T-Shirt', '{"category":"clothes","emoji":"👕","features":[]}'),
    ('HOME_BED', 'Bed', '{"category":"home","emoji":"🛏️","features":["sleep_on","sit_on"]}'),
    ('HOME_CHAIR', 'Chair', '{"category":"home","emoji":"🪑","features":["sit_on"]}'),
    ('HOME_DOOR', 'Door', '{"category":"home","emoji":"🚪","features":["open_to_enter"]}'),
    ('HOME_WINDOW', 'Window', '{"category":"home","emoji":"🪟","features":["look_through_outside"]}'),
    ('HOME_SPOON', 'Spoon', '{"category":"home","emoji":"🥄","features":["eat_soup_with"]}'),
    ('HOME_CUP', 'Cup', '{"category":"home","emoji":"🥤","features":["drink_from"]}'),
    ('HOME_TOOTHBRUSH', 'Toothbrush', '{"category":"home","emoji":"🪥","features":["brush_teeth"]}'),
    ('HOME_REFRIGERATOR', 'Refrigerator', '{"category":"home","emoji":"🧊","features":["keeps_food_cold"]}'),
    ('HOME_LAMP', 'Lamp', '{"category":"home","emoji":"💡","features":["gives_light"]}'),
    ('HOME_REMOTE_CONTROL', 'Remote Control', '{"category":"home","emoji":"📺","features":["control_tv"]}'),
    ('HOME_SOFA', 'Sofa', '{"category":"home","emoji":"🛋️","features":["sleep_on","sit_on"]}'),
    ('HOME_TABLE', 'Table', '{"category":"home","emoji":"🍽️","features":[]}'),
    ('HOME_CLOCK', 'Clock', '{"category":"home","emoji":"⏰","features":[]}'),
    ('NATURE_SUN', 'Sun', '{"category":"nature","emoji":"☀️","features":["shines_by_day","flowers_need_from_sky"]}'),
    ('NATURE_MOON', 'Moon', '{"category":"nature","emoji":"🌙","features":["shines_by_night"]}'),
    ('NATURE_RAINBOW', 'Rainbow', '{"category":"nature","emoji":"🌈","features":["many_colors_after_rain"]}'),
    ('NATURE_RAIN', 'Rain', '{"category":"nature","emoji":"🌧️","features":["falls_when_raining","flowers_need_from_sky"]}'),
    ('NATURE_PLANT', 'Plant', '{"category":"nature","emoji":"🌱","features":["grows_from_ground_has_leaves"]}'),
    ('NATURE_TREE', 'Tree', '{"category":"nature","emoji":"🌳","features":["grows_from_ground_has_leaves","trunk_and_branches"]}'),
    ('NATURE_SUNLIGHT', 'Sunlight', '{"category":"nature","emoji":"🌞","features":["shines_by_day","flowers_need_from_sky"]}'),
    ('NATURE_WIND', 'Wind', '{"category":"nature","emoji":"💨","features":["moves_leaves"]}'),
    ('NATURE_CLOUD', 'Cloud', '{"category":"nature","emoji":"☁️","features":["white_floats_in_sky"]}'),
    ('NATURE_LEAF', 'Leaf', '{"category":"nature","emoji":"🍃","features":["found_on_forest_ground"]}'),
    ('NATURE_STAR', 'Star', '{"category":"nature","emoji":"⭐","features":["shines_by_night"]}'),
    ('NATURE_FLOWER', 'Flower', '{"category":"nature","emoji":"🌸","features":["grows_from_ground_has_leaves"]}'),
    ('NATURE_MOUNTAIN', 'Mountain', '{"category":"nature","emoji":"⛰️","features":[]}'),
    ('NATURE_RIVER', 'River', '{"category":"nature","emoji":"🏞️","features":[]}'),
    ('SEA_ANIMALS_OCTOPUS', 'Octopus', '{"category":"sea_animals","emoji":"🐙","features":["eight_arms"]}'),
    ('SEA_ANIMALS_WHALE', 'Whale', '{"category":"sea_animals","emoji":"🐳","features":["very_big_ocean","jumps_out_of_water"]}'),
    ('SEA_ANIMALS_SHARK', 'Shark', '{"category":"sea_animals","emoji":"🦈","features":["sharp_teeth"]}'),
    ('SEA_ANIMALS_SEA_TURTLE', 'Sea Turtle', '{"category":"sea_animals","emoji":"🐢","features":["shell_swims_in_sea","hard_shell_slow"]}'),
    ('SEA_ANIMALS_DOLPHIN', 'Dolphin', '{"category":"sea_animals","emoji":"🐬","features":["jumps_out_of_water"]}'),
    ('SEA_ANIMALS_STARFISH', 'Starfish', '{"category":"sea_animals","emoji":"⭐","features":["five_arms"]}'),
    ('SEA_ANIMALS_JELLYFISH', 'Jellyfish', '{"category":"sea_animals","emoji":"🪼","features":["tentacles_and_stings"]}'),
    ('SEA_ANIMALS_CRAB', 'Crab', '{"category":"sea_animals","emoji":"🦀","features":["shell_swims_in_sea","claws_walks_sideways"]}'),
    ('SEA_ANIMALS_TURTLE', 'Turtle', '{"category":"sea_animals","emoji":"🐢","features":["shell_swims_in_sea","hard_shell_slow"]}'),
    ('SEA_ANIMALS_FISH', 'Fish', '{"category":"sea_animals","emoji":"🐠","features":["jumps_out_of_water","fins_and_scales"]}'),
    ('SEA_ANIMALS_SEAL', 'Seal', '{"category":"sea_animals","emoji":"🦭","features":[]}'),
    ('SEA_ANIMALS_SHRIMP', 'Shrimp', '{"category":"sea_animals","emoji":"🦐","features":[]}'),
    ('FARM_ANIMALS_COW', 'Cow', '{"category":"farm_animals","emoji":"🐮","features":["says_moo","horns_and_milk"]}'),
    ('FARM_ANIMALS_PIG', 'Pig', '{"category":"farm_animals","emoji":"🐷","features":["says_oink","rolls_in_mud"]}'),
    ('FARM_ANIMALS_CHICKEN', 'Chicken', '{"category":"farm_animals","emoji":"🐔","features":["says_cluck","lays_eggs_feathers"]}'),
    ('FARM_ANIMALS_DUCK', 'Duck', '{"category":"farm_animals","emoji":"🦆","features":["says_quack","lays_eggs_feathers"]}'),
    ('FARM_ANIMALS_SHEEP', 'Sheep', '{"category":"farm_animals","emoji":"🐑","features":["gives_wool","horns_and_milk","says_baa"]}'),
    ('FARM_ANIMALS_HORSE', 'Horse', '{"category":"farm_animals","emoji":"🐴","features":["mane_and_rideable"]}'),
    ('FARM_ANIMALS_GOAT', 'Goat', '{"category":"farm_animals","emoji":"🐐","features":["horns_and_milk","says_baa"]}'),
    ('FARM_ANIMALS_DOG', 'Dog', '{"category":"farm_animals","emoji":"🐕","features":[]}'),
    ('FARM_ANIMALS_CAT', 'Cat', '{"category":"farm_animals","emoji":"🐈","features":[]}')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, metadata = EXCLUDED.metadata, is_active = TRUE;

-- ---------------------------------------------------------------------------
-- Questions: 10 per topic. Each has ONE correct answer item, the pool of wrong answers (same category, 3 items) and
-- correct_match (the feature the question asks for). The generator drops any wrong answer that also carries it.
-- text_vi is the Vietnamese wording of the question. The id is derived from a stable key, so re-running updates rows.
-- ---------------------------------------------------------------------------
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, correct_answer_item_id, difficulty, min_age, max_age, display_order,
     metadata, is_active)
SELECT md5('nimokids:question:' || q.question_key)::uuid, t.id, m.id, q.question_text, a.id,
       q.difficulty, q.min_age, q.max_age, q.display_order, q.metadata::jsonb, TRUE
FROM (VALUES
    ('ANIMALS_1', 'ANIMALS', 'What animal says "Meow"?', 'ANIMALS_CAT', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"says_meow"},"text_vi":"Con vật nào kêu Meo meo?"}'),
    ('ANIMALS_2', 'ANIMALS', 'What animal says "Woof"?', 'ANIMALS_DOG', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"says_woof"},"text_vi":"Con vật nào kêu Gâu gâu?"}'),
    ('ANIMALS_3', 'ANIMALS', 'What animal has a big trunk?', 'ANIMALS_ELEPHANT', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"long_trunk"},"text_vi":"Con vật nào có cái vòi to?"}'),
    ('ANIMALS_4', 'ANIMALS', 'What animal has a very long neck?', 'ANIMALS_GIRAFFE', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"long_neck"},"text_vi":"Con vật nào có cổ rất dài?"}'),
    ('ANIMALS_5', 'ANIMALS', 'What animal has black and white stripes?', 'ANIMALS_ZEBRA', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"black_white_stripes"},"text_vi":"Con vật nào có vạch đen trắng?"}'),
    ('ANIMALS_6', 'ANIMALS', 'What animal is the king of the jungle?', 'ANIMALS_LION', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"has_mane"},"text_vi":"Con vật nào là chúa tể rừng xanh?"}'),
    ('ANIMALS_7', 'ANIMALS', 'What animal loves to hop and hop?', 'ANIMALS_RABBIT', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"can_hop"},"text_vi":"Con vật nào thích nhảy nhảy?"}'),
    ('ANIMALS_8', 'ANIMALS', 'What animal gives us milk?', 'ANIMALS_COW', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"gives_milk"},"text_vi":"Con vật nào cho chúng ta uống sữa?"}'),
    ('ANIMALS_9', 'ANIMALS', 'What animal carries its home on its back?', 'ANIMALS_TURTLE', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"has_shell"},"text_vi":"Con vật nào mang nhà trên lưng?"}'),
    ('ANIMALS_10', 'ANIMALS', 'What animal loves to swing on trees?', 'ANIMALS_MONKEY', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"swings_from_trees"},"text_vi":"Con vật nào thích đu cây?"}'),
    ('FRUITS_1', 'FRUITS', 'What fruit is yellow and long?', 'FRUITS_BANANA', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"yellow_long"},"text_vi":"Quả nào màu vàng và dài?"}'),
    ('FRUITS_2', 'FRUITS', 'What fruit is round and red?', 'FRUITS_APPLE', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"red_round"},"text_vi":"Quả nào tròn và màu đỏ?"}'),
    ('FRUITS_3', 'FRUITS', 'What fruit is orange and juicy?', 'FRUITS_ORANGE', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"orange_round"},"text_vi":"Quả nào màu cam và nhiều nước?"}'),
    ('FRUITS_4', 'FRUITS', 'What fruit grows in a big bunch?', 'FRUITS_GRAPE', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"grows_in_bunch"},"text_vi":"Quả nào mọc thành chùm to?"}'),
    ('FRUITS_5', 'FRUITS', 'What fruit is green outside and red inside?', 'FRUITS_WATERMELON', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"green_outside_red_inside"},"text_vi":"Quả nào xanh bên ngoài, đỏ bên trong?"}'),
    ('FRUITS_6', 'FRUITS', 'What fruit has a spiky top?', 'FRUITS_PINEAPPLE', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"has_crown"},"text_vi":"Quả nào có lá gai trên đỉnh?"}'),
    ('FRUITS_7', 'FRUITS', 'What tiny red fruit has seeds outside?', 'FRUITS_STRAWBERRY', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"seeds_outside"},"text_vi":"Quả đỏ nhỏ nào có hạt ở ngoài vỏ?"}'),
    ('FRUITS_8', 'FRUITS', 'What fruit looks like a light bulb?', 'FRUITS_PEAR', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"pear_shaped"},"text_vi":"Quả nào có hình giống bóng đèn?"}'),
    ('FRUITS_9', 'FRUITS', 'What fruit is fuzzy outside and green inside?', 'FRUITS_KIWI', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"hairy_green_inside"},"text_vi":"Quả nào có vỏ xù và ruột xanh?"}'),
    ('FRUITS_10', 'FRUITS', 'What small purple fruit has a pit inside?', 'FRUITS_PLUM', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"big_pit_purple_or_yellow"},"text_vi":"Quả tím nhỏ nào có hạt to bên trong?"}'),
    ('VEHICLES_1', 'VEHICLES', 'What flies up in the sky?', 'VEHICLES_AIRPLANE', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"flies_in_sky"},"text_vi":"Cái gì bay trên bầu trời?"}'),
    ('VEHICLES_2', 'VEHICLES', 'What goes on the water?', 'VEHICLES_BOAT', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"travels_on_water"},"text_vi":"Cái gì đi trên mặt nước?"}'),
    ('VEHICLES_3', 'VEHICLES', 'What vehicle goes "Choo choo"?', 'VEHICLES_TRAIN', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"goes_choo_choo"},"text_vi":"Phương tiện nào kêu Choo choo?"}'),
    ('VEHICLES_4', 'VEHICLES', 'What has two wheels and pedals?', 'VEHICLES_BICYCLE', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"two_wheels_pedals"},"text_vi":"Cái gì có hai bánh và bàn đạp?"}'),
    ('VEHICLES_5', 'VEHICLES', 'What big yellow bus takes kids to school?', 'VEHICLES_SCHOOL_BUS', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"takes_children_to_school"},"text_vi":"Xe bus vàng to nào chở trẻ em đến trường?"}'),
    ('VEHICLES_6', 'VEHICLES', 'What has four wheels and takes you places?', 'VEHICLES_CAR', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"four_wheels_carries_people"},"text_vi":"Cái gì có bốn bánh và đưa bạn đến nơi bạn muốn?"}'),
    ('VEHICLES_7', 'VEHICLES', 'What big red truck fights fires?', 'VEHICLES_FIRE_TRUCK', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"has_ladder_fights_fires"},"text_vi":"Xe tải đỏ to nào dùng để chữa cháy?"}'),
    ('VEHICLES_8', 'VEHICLES', 'What helps sick people go to the hospital?', 'VEHICLES_AMBULANCE', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"takes_sick_to_hospital"},"text_vi":"Cái gì đưa người bệnh đến bệnh viện?"}'),
    ('VEHICLES_9', 'VEHICLES', 'What has two wheels and goes vroom vroom?', 'VEHICLES_MOTORCYCLE', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"two_wheels_motor"},"text_vi":"Cái gì có hai bánh và kêu vù vù?"}'),
    ('VEHICLES_10', 'VEHICLES', 'What flies and has big spinning blades?', 'VEHICLES_HELICOPTER', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"big_blades"},"text_vi":"Cái gì bay và có cánh quạt to quay?"}'),
    ('COLORS_1', 'COLORS', 'What color is the sky?', 'COLORS_BLUE', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"sky_clear_day"},"text_vi":"Bầu trời có màu gì?"}'),
    ('COLORS_2', 'COLORS', 'What color is a banana?', 'COLORS_YELLOW', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"banana_color"},"text_vi":"Quả chuối có màu gì?"}'),
    ('COLORS_3', 'COLORS', 'What color is grass?', 'COLORS_GREEN', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"grass_color"},"text_vi":"Cỏ có màu gì?"}'),
    ('COLORS_4', 'COLORS', 'What color is an apple?', 'COLORS_RED', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"strawberry_color"},"text_vi":"Quả táo có màu gì?"}'),
    ('COLORS_5', 'COLORS', 'What color is an orange?', 'COLORS_ORANGE', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"orange_fruit_color"},"text_vi":"Quả cam có màu gì?"}'),
    ('COLORS_6', 'COLORS', 'What color is a grape?', 'COLORS_PURPLE', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"eggplant_color"},"text_vi":"Quả nho có màu gì?"}'),
    ('COLORS_7', 'COLORS', 'What color is snow?', 'COLORS_WHITE', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"snow_color"},"text_vi":"Tuyết có màu gì?"}'),
    ('COLORS_8', 'COLORS', 'What color is a pumpkin?', 'COLORS_ORANGE', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"pumpkin_color"},"text_vi":"Quả bí có màu gì?"}'),
    ('COLORS_9', 'COLORS', 'What color is a lemon?', 'COLORS_YELLOW', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"lemon_color"},"text_vi":"Quả chanh có màu gì?"}'),
    ('COLORS_10', 'COLORS', 'What color is the night sky?', 'COLORS_DARK_BLUE', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"night_sky"},"text_vi":"Bầu trời ban đêm có màu gì?"}'),
    ('SHAPES_1', 'SHAPES', 'What shape is perfectly round?', 'SHAPES_CIRCLE', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"is_round"},"text_vi":"Hình nào tròn hoàn toàn?"}'),
    ('SHAPES_2', 'SHAPES', 'What shape has three sides?', 'SHAPES_TRIANGLE', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"three_sides"},"text_vi":"Hình nào có ba cạnh?"}'),
    ('SHAPES_3', 'SHAPES', 'What shape has four equal sides?', 'SHAPES_SQUARE', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"four_equal_sides"},"text_vi":"Hình nào có bốn cạnh bằng nhau?"}'),
    ('SHAPES_4', 'SHAPES', 'What shape looks like a door?', 'SHAPES_RECTANGLE', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_door"},"text_vi":"Hình nào trông như cái cửa?"}'),
    ('SHAPES_5', 'SHAPES', 'What shape looks like an egg?', 'SHAPES_OVAL', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_egg"},"text_vi":"Hình nào trông như quả trứng?"}'),
    ('SHAPES_6', 'SHAPES', 'What shape has five sides?', 'SHAPES_PENTAGON', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"five_sides"},"text_vi":"Hình nào có năm cạnh?"}'),
    ('SHAPES_7', 'SHAPES', 'What shape has six sides?', 'SHAPES_HEXAGON', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"six_sides"},"text_vi":"Hình nào có sáu cạnh?"}'),
    ('SHAPES_8', 'SHAPES', 'What shape looks like a heart?', 'SHAPES_HEART', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_heart"},"text_vi":"Hình nào trông như trái tim?"}'),
    ('SHAPES_9', 'SHAPES', 'What shape looks like a star?', 'SHAPES_STAR', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_star"},"text_vi":"Hình nào trông như ngôi sao?"}'),
    ('SHAPES_10', 'SHAPES', 'What shape has no corners at all?', 'SHAPES_CIRCLE', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"no_corners"},"text_vi":"Hình nào không có góc nào cả?"}'),
    ('NUMBERS_1', 'NUMBERS', 'How many apples? 🍎', 'NUMBERS_ONE', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_one_apple"},"text_vi":"Có mấy quả táo?"}'),
    ('NUMBERS_2', 'NUMBERS', 'How many balls? ⚽⚽', 'NUMBERS_TWO', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_two_balls"},"text_vi":"Có mấy quả bóng?"}'),
    ('NUMBERS_3', 'NUMBERS', 'How many stars? ⭐⭐⭐', 'NUMBERS_THREE', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_three_stars"},"text_vi":"Có mấy ngôi sao?"}'),
    ('NUMBERS_4', 'NUMBERS', 'How many ducks? 🦆🦆🦆🦆', 'NUMBERS_FOUR', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_four_ducks"},"text_vi":"Có mấy con vịt?"}'),
    ('NUMBERS_5', 'NUMBERS', 'How many fingers on one hand?', 'NUMBERS_FIVE', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"fingers_on_hand"},"text_vi":"Một bàn tay có mấy ngón?"}'),
    ('NUMBERS_6', 'NUMBERS', 'What number comes after 1?', 'NUMBERS_TWO', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"after_one"},"text_vi":"Số nào đứng sau số 1?"}'),
    ('NUMBERS_7', 'NUMBERS', 'What number comes after 4?', 'NUMBERS_FIVE', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"after_four"},"text_vi":"Số nào đứng sau số 4?"}'),
    ('NUMBERS_8', 'NUMBERS', 'What number comes before 3?', 'NUMBERS_TWO', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"before_three"},"text_vi":"Số nào đứng trước số 3?"}'),
    ('NUMBERS_9', 'NUMBERS', 'How many wheels on a bicycle?', 'NUMBERS_TWO', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"bicycle_wheels"},"text_vi":"Xe đạp có mấy bánh?"}'),
    ('NUMBERS_10', 'NUMBERS', 'How many legs does a dog have?', 'NUMBERS_FOUR', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"dog_legs"},"text_vi":"Con chó có mấy chân?"}'),
    ('ALPHABET_1', 'ALPHABET', '"Apple" starts with what letter?', 'ALPHABET_A', 1, 3, 5, 1, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_apple"},"text_vi":"Từ Apple bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_2', 'ALPHABET', '"Ball" starts with what letter?', 'ALPHABET_B', 1, 3, 5, 2, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_ball"},"text_vi":"Từ Ball bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_3', 'ALPHABET', '"Cat" starts with what letter?', 'ALPHABET_C', 1, 3, 5, 3, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_cat"},"text_vi":"Từ Cat bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_4', 'ALPHABET', '"Dog" starts with what letter?', 'ALPHABET_D', 1, 3, 5, 4, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_dog"},"text_vi":"Từ Dog bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_5', 'ALPHABET', '"Elephant" starts with what letter?', 'ALPHABET_E', 1, 3, 5, 5, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_elephant"},"text_vi":"Từ Elephant bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_6', 'ALPHABET', '"Fish" starts with what letter?', 'ALPHABET_F', 2, 3, 5, 6, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_fish"},"text_vi":"Từ Fish bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_7', 'ALPHABET', '"Goat" starts with what letter?', 'ALPHABET_G', 2, 3, 5, 7, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_goat"},"text_vi":"Từ Goat bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_8', 'ALPHABET', '"Hat" starts with what letter?', 'ALPHABET_H', 2, 3, 5, 8, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"starts_hat"},"text_vi":"Từ Hat bắt đầu bằng chữ gì?"}'),
    ('ALPHABET_9', 'ALPHABET', 'What letter comes after A?', 'ALPHABET_B', 2, 3, 5, 9, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"after_a"},"text_vi":"Chữ nào đứng sau chữ A?"}'),
    ('ALPHABET_10', 'ALPHABET', 'What letter comes before C?', 'ALPHABET_B', 2, 3, 5, 10, '{"distractor_rules":[{"match":{"category":"alphabet"},"count":3}],"correct_match":{"features":"before_c"},"text_vi":"Chữ nào đứng trước chữ C?"}'),
    ('FOOD_1', 'FOOD', 'What food has bread on top and bottom?', 'FOOD_SANDWICH', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"bread_and_cheese"},"text_vi":"Món ăn nào có bánh mì cả trên lẫn dưới?"}'),
    ('FOOD_2', 'FOOD', 'What round food has cheese on top?', 'FOOD_PIZZA', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"round_cheese_tomato"},"text_vi":"Món ăn tròn nào có phô mai bên trên?"}'),
    ('FOOD_3', 'FOOD', 'What warm food do you eat from a bowl?', 'FOOD_SOUP', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"eaten_with_spoon_in_bowl"},"text_vi":"Món ăn nóng nào đựng trong tô?"}'),
    ('FOOD_4', 'FOOD', 'What cold sweet food do kids love?', 'FOOD_ICE_CREAM', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"cold_sweet_flavors"},"text_vi":"Món lạnh ngọt nào trẻ em rất thích?"}'),
    ('FOOD_5', 'FOOD', 'What tiny white grains do we eat?', 'FOOD_RICE', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"made_from_rice"},"text_vi":"Chúng ta ăn hạt trắng nhỏ là gì?"}'),
    ('FOOD_6', 'FOOD', 'What long food do you slurp?', 'FOOD_NOODLES', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"long_with_sauce"},"text_vi":"Món ăn dài nào bạn húp khi ăn?"}'),
    ('FOOD_7', 'FOOD', 'What yellow food is made from eggs?', 'FOOD_OMELET', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"made_from_eggs"},"text_vi":"Món ăn vàng nào làm từ trứng?"}'),
    ('FOOD_8', 'FOOD', 'What sweet food has candles on top?', 'FOOD_CAKE', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"birthday_sweet"},"text_vi":"Món ngọt nào cắm nến lên trên?"}'),
    ('FOOD_9', 'FOOD', 'What long thin food is made from potatoes?', 'FOOD_FRENCH_FRIES', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"fried_potatoes"},"text_vi":"Món ăn dài mỏng nào làm từ khoai tây?"}'),
    ('FOOD_10', 'FOOD', 'What sweet food do bees make?', 'FOOD_HONEY', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"food"},"count":3}],"correct_match":{"features":"made_by_bees"},"text_vi":"Con ong làm ra món ngọt gì?"}'),
    ('TOYS_1', 'TOYS', 'What round toy can you roll and kick?', 'TOYS_BALL', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"can_roll"},"text_vi":"Đồ chơi tròn nào bạn có thể lăn và đá?"}'),
    ('TOYS_2', 'TOYS', 'What soft toy can you hug?', 'TOYS_TEDDY_BEAR', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"can_hug"},"text_vi":"Đồ chơi mềm nào bạn có thể ôm?"}'),
    ('TOYS_3', 'TOYS', 'What toy has four wheels and goes zoom?', 'TOYS_TOY_CAR', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"four_wheels"},"text_vi":"Đồ chơi nào có bốn bánh và chạy vèo vèo?"}'),
    ('TOYS_4', 'TOYS', 'What toy do you stack to build things?', 'TOYS_BLOCKS', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"build_with"},"text_vi":"Đồ chơi nào bạn xếp chồng để xây?"}'),
    ('TOYS_5', 'TOYS', 'What toy has strings and makes music?', 'TOYS_TOY_GUITAR', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"strings_instrument"},"text_vi":"Đồ chơi nào có dây và tạo ra âm nhạc?"}'),
    ('TOYS_6', 'TOYS', 'What toy can you push across the floor?', 'TOYS_TOY_CAR', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"push_around"},"text_vi":"Đồ chơi nào bạn có thể đẩy trên sàn?"}'),
    ('TOYS_7', 'TOYS', 'What toy flies when you throw it?', 'TOYS_PAPER_PLANE', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"flies_when_thrown"},"text_vi":"Đồ chơi nào bay khi bạn ném?"}'),
    ('TOYS_8', 'TOYS', 'What toy spins round and round?', 'TOYS_SPINNER', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"spins_around"},"text_vi":"Đồ chơi nào xoay tròn mãi?"}'),
    ('TOYS_9', 'TOYS', 'What toy has many pieces to fit together?', 'TOYS_PUZZLE', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"put_together_pieces"},"text_vi":"Đồ chơi nào có nhiều mảnh để ghép lại?"}'),
    ('TOYS_10', 'TOYS', 'What toy looks like a little baby?', 'TOYS_DOLL', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"looks_like_baby"},"text_vi":"Đồ chơi nào trông như em bé nhỏ?"}'),
    ('CLOTHES_1', 'CLOTHES', 'What do you wear on your head?', 'CLOTHES_HAT', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_on_head"},"text_vi":"Bạn đội gì trên đầu?"}'),
    ('CLOTHES_2', 'CLOTHES', 'What do you wear on your feet?', 'CLOTHES_SHOES', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_on_feet"},"text_vi":"Bạn mang gì ở bàn chân?"}'),
    ('CLOTHES_3', 'CLOTHES', 'What do you wear when it is cold?', 'CLOTHES_JACKET', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_when_cold"},"text_vi":"Bạn mặc gì khi trời lạnh?"}'),
    ('CLOTHES_4', 'CLOTHES', 'What do you wear when you swim?', 'CLOTHES_SWIMSUIT', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_to_swim"},"text_vi":"Bạn mặc gì khi đi bơi?"}'),
    ('CLOTHES_5', 'CLOTHES', 'What do you wear on your hands?', 'CLOTHES_GLOVES', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_on_hands"},"text_vi":"Bạn đeo gì vào tay?"}'),
    ('CLOTHES_6', 'CLOTHES', 'What do you wear around your neck?', 'CLOTHES_SCARF', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_on_neck"},"text_vi":"Bạn quàng gì quanh cổ?"}'),
    ('CLOTHES_7', 'CLOTHES', 'What do you wear when it rains?', 'CLOTHES_RAINCOAT', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_when_rain"},"text_vi":"Bạn mặc gì khi trời mưa?"}'),
    ('CLOTHES_8', 'CLOTHES', 'What do you wear on your legs?', 'CLOTHES_PANTS', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_on_legs"},"text_vi":"Bạn mặc gì ở chân?"}'),
    ('CLOTHES_9', 'CLOTHES', 'What do you wear to protect your eyes?', 'CLOTHES_SUNGLASSES', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"protects_eyes_from_sun"},"text_vi":"Bạn đeo gì để bảo vệ mắt?"}'),
    ('CLOTHES_10', 'CLOTHES', 'What do you wear when you sleep?', 'CLOTHES_PAJAMAS', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"clothes"},"count":3}],"correct_match":{"features":"worn_to_sleep"},"text_vi":"Bạn mặc gì khi đi ngủ?"}'),
    ('HOME_1', 'HOME', 'What do you sleep on?', 'HOME_BED', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"sleep_on"},"text_vi":"Bạn ngủ trên cái gì?"}'),
    ('HOME_2', 'HOME', 'What do you sit on?', 'HOME_CHAIR', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"sit_on"},"text_vi":"Bạn ngồi lên cái gì?"}'),
    ('HOME_3', 'HOME', 'What do you open to go into a room?', 'HOME_DOOR', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"open_to_enter"},"text_vi":"Bạn mở cái gì để vào phòng?"}'),
    ('HOME_4', 'HOME', 'What do you look through to see outside?', 'HOME_WINDOW', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"look_through_outside"},"text_vi":"Bạn nhìn qua cái gì để thấy bên ngoài?"}'),
    ('HOME_5', 'HOME', 'What do you use to eat soup?', 'HOME_SPOON', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"eat_soup_with"},"text_vi":"Bạn dùng cái gì để ăn súp?"}'),
    ('HOME_6', 'HOME', 'What do you drink from?', 'HOME_CUP', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"drink_from"},"text_vi":"Bạn uống nước từ cái gì?"}'),
    ('HOME_7', 'HOME', 'What do you use to brush your teeth?', 'HOME_TOOTHBRUSH', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"brush_teeth"},"text_vi":"Bạn dùng cái gì để đánh răng?"}'),
    ('HOME_8', 'HOME', 'What keeps our food cold?', 'HOME_REFRIGERATOR', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"keeps_food_cold"},"text_vi":"Cái gì giữ thức ăn luôn lạnh?"}'),
    ('HOME_9', 'HOME', 'What gives us light at night?', 'HOME_LAMP', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"gives_light"},"text_vi":"Cái gì cho chúng ta ánh sáng ban đêm?"}'),
    ('HOME_10', 'HOME', 'What do you press to change TV channels?', 'HOME_REMOTE_CONTROL', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"home"},"count":3}],"correct_match":{"features":"control_tv"},"text_vi":"Bạn nhấn cái gì để đổi kênh TV?"}'),
    ('NATURE_1', 'NATURE', 'What is bright and warm in the sky?', 'NATURE_SUN', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"shines_by_day"},"text_vi":"Cái gì sáng và ấm trên bầu trời?"}'),
    ('NATURE_2', 'NATURE', 'What glows in the sky at night?', 'NATURE_MOON', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"shines_by_night"},"text_vi":"Cái gì sáng lên trên bầu trời ban đêm?"}'),
    ('NATURE_3', 'NATURE', 'What colorful thing appears after rain?', 'NATURE_RAINBOW', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"many_colors_after_rain"},"text_vi":"Cái gì nhiều màu sắc xuất hiện sau mưa?"}'),
    ('NATURE_4', 'NATURE', 'What falls from the sky on a rainy day?', 'NATURE_RAIN', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"falls_when_raining"},"text_vi":"Cái gì rơi từ trên trời khi trời mưa?"}'),
    ('NATURE_5', 'NATURE', 'What small green thing grows from the ground?', 'NATURE_PLANT', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"grows_from_ground_has_leaves"},"text_vi":"Cái gì màu xanh nhỏ mọc từ đất?"}'),
    ('NATURE_6', 'NATURE', 'What is tall and has a trunk and branches?', 'NATURE_TREE', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"trunk_and_branches"},"text_vi":"Cái gì cao và có thân với các cành?"}'),
    ('NATURE_7', 'NATURE', 'What do plants need from the sun to grow?', 'NATURE_SUNLIGHT', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"flowers_need_from_sky"},"text_vi":"Cây cần gì từ mặt trời để lớn lên?"}'),
    ('NATURE_8', 'NATURE', 'What makes the leaves move on the trees?', 'NATURE_WIND', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"moves_leaves"},"text_vi":"Cái gì làm lá cây lay động?"}'),
    ('NATURE_9', 'NATURE', 'What white fluffy thing floats in the sky?', 'NATURE_CLOUD', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"white_floats_in_sky"},"text_vi":"Cái gì trắng xốp trôi trên bầu trời?"}'),
    ('NATURE_10', 'NATURE', 'What green thing falls from trees in autumn?', 'NATURE_LEAF', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"nature"},"count":3}],"correct_match":{"features":"found_on_forest_ground"},"text_vi":"Cái gì màu xanh rụng từ cây vào mùa thu?"}'),
    ('SEA_ANIMALS_1', 'SEA_ANIMALS', 'What sea animal has eight arms?', 'SEA_ANIMALS_OCTOPUS', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"eight_arms"},"text_vi":"Con vật biển nào có tám cánh tay?"}'),
    ('SEA_ANIMALS_2', 'SEA_ANIMALS', 'What is the biggest animal in the ocean?', 'SEA_ANIMALS_WHALE', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"very_big_ocean"},"text_vi":"Con vật nào to nhất dưới biển?"}'),
    ('SEA_ANIMALS_3', 'SEA_ANIMALS', 'What ocean animal has very sharp teeth?', 'SEA_ANIMALS_SHARK', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"sharp_teeth"},"text_vi":"Con vật biển nào có răng rất sắc?"}'),
    ('SEA_ANIMALS_4', 'SEA_ANIMALS', 'What sea animal has a hard shell?', 'SEA_ANIMALS_SEA_TURTLE', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"shell_swims_in_sea"},"text_vi":"Con vật biển nào có mai cứng?"}'),
    ('SEA_ANIMALS_5', 'SEA_ANIMALS', 'What friendly sea animal loves to jump?', 'SEA_ANIMALS_DOLPHIN', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"jumps_out_of_water"},"text_vi":"Con vật biển thân thiện nào thích nhảy?"}'),
    ('SEA_ANIMALS_6', 'SEA_ANIMALS', 'What sea animal looks like a star?', 'SEA_ANIMALS_STARFISH', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"five_arms"},"text_vi":"Con vật biển nào trông như ngôi sao?"}'),
    ('SEA_ANIMALS_7', 'SEA_ANIMALS', 'What sea animal looks like a floating umbrella?', 'SEA_ANIMALS_JELLYFISH', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"tentacles_and_stings"},"text_vi":"Con vật biển nào trông như cái ô đang nổi?"}'),
    ('SEA_ANIMALS_8', 'SEA_ANIMALS', 'What sea animal has big claws and walks sideways?', 'SEA_ANIMALS_CRAB', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"claws_walks_sideways"},"text_vi":"Con vật biển nào có càng to và đi ngang?"}'),
    ('SEA_ANIMALS_9', 'SEA_ANIMALS', 'What animal swims slowly with a shell on its back?', 'SEA_ANIMALS_TURTLE', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"hard_shell_slow"},"text_vi":"Con vật nào bơi chậm và có mai trên lưng?"}'),
    ('SEA_ANIMALS_10', 'SEA_ANIMALS', 'What animal lives in water and has fins?', 'SEA_ANIMALS_FISH', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"fins_and_scales"},"text_vi":"Con vật nào sống dưới nước và có vây?"}'),
    ('FARM_ANIMALS_1', 'FARM_ANIMALS', 'What animal says "Moo"?', 'FARM_ANIMALS_COW', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"says_moo"},"text_vi":"Con vật nào kêu Ò ò?"}'),
    ('FARM_ANIMALS_2', 'FARM_ANIMALS', 'What animal says "Oink"?', 'FARM_ANIMALS_PIG', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"says_oink"},"text_vi":"Con vật nào kêu Ụt ịt?"}'),
    ('FARM_ANIMALS_3', 'FARM_ANIMALS', 'What animal says "Cluck"?', 'FARM_ANIMALS_CHICKEN', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"says_cluck"},"text_vi":"Con vật nào kêu Cục tác?"}'),
    ('FARM_ANIMALS_4', 'FARM_ANIMALS', 'What animal says "Quack"?', 'FARM_ANIMALS_DUCK', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"says_quack"},"text_vi":"Con vật nào kêu Cạp cạp?"}'),
    ('FARM_ANIMALS_5', 'FARM_ANIMALS', 'What animal gives us fluffy wool?', 'FARM_ANIMALS_SHEEP', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"gives_wool"},"text_vi":"Con vật nào cho chúng ta bộ lông xù?"}'),
    ('FARM_ANIMALS_6', 'FARM_ANIMALS', 'What farm animal can you ride?', 'FARM_ANIMALS_HORSE', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"mane_and_rideable"},"text_vi":"Con vật nông trại nào bạn có thể cưỡi?"}'),
    ('FARM_ANIMALS_7', 'FARM_ANIMALS', 'What farm animal gives us milk?', 'FARM_ANIMALS_COW', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"horns_and_milk"},"text_vi":"Con vật nông trại nào cho chúng ta sữa?"}'),
    ('FARM_ANIMALS_8', 'FARM_ANIMALS', 'What farm animal loves playing in mud?', 'FARM_ANIMALS_PIG', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"rolls_in_mud"},"text_vi":"Con vật nông trại nào thích đùa trong bùn?"}'),
    ('FARM_ANIMALS_9', 'FARM_ANIMALS', 'What farm animal lays eggs?', 'FARM_ANIMALS_CHICKEN', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"lays_eggs_feathers"},"text_vi":"Con vật nông trại nào đẻ trứng?"}'),
    ('FARM_ANIMALS_10', 'FARM_ANIMALS', 'What animal says "Baa"?', 'FARM_ANIMALS_SHEEP', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"farm_animals"},"count":3}],"correct_match":{"features":"says_baa"},"text_vi":"Con vật nào kêu Be be?"}')
) AS q(question_key, topic_code, question_text, correct_code, difficulty, min_age, max_age, display_order, metadata)
JOIN topics t ON t.code = q.topic_code
JOIN answer_items a ON a.code = q.correct_code
CROSS JOIN game_modes m
WHERE m.code = 'GUESS'
ON CONFLICT (id) DO UPDATE
    SET topic_id = EXCLUDED.topic_id, question_text = EXCLUDED.question_text,
        correct_answer_item_id = EXCLUDED.correct_answer_item_id, difficulty = EXCLUDED.difficulty,
        min_age = EXCLUDED.min_age, max_age = EXCLUDED.max_age, display_order = EXCLUDED.display_order,
        metadata = EXCLUDED.metadata, is_active = TRUE;

-- ---------------------------------------------------------------------------
-- Clean-up of the first dev content (4 roots with sub-topics and its own vocabulary). Rows already used by a game
-- session are kept but switched off, so history stays intact.
-- ---------------------------------------------------------------------------
UPDATE game_questions SET is_active = FALSE
WHERE id IN (
    md5('nimokids:question:' || 'FLY_1')::uuid,
    md5('nimokids:question:' || 'FLY_2')::uuid,
    md5('nimokids:question:' || 'FLY_3')::uuid,
    md5('nimokids:question:' || 'FLY_4')::uuid,
    md5('nimokids:question:' || 'FLY_5')::uuid,
    md5('nimokids:question:' || 'FARM_1')::uuid,
    md5('nimokids:question:' || 'FARM_2')::uuid,
    md5('nimokids:question:' || 'FARM_3')::uuid,
    md5('nimokids:question:' || 'FARM_4')::uuid,
    md5('nimokids:question:' || 'FARM_5')::uuid,
    md5('nimokids:question:' || 'WATER_1')::uuid,
    md5('nimokids:question:' || 'WATER_2')::uuid,
    md5('nimokids:question:' || 'WATER_3')::uuid,
    md5('nimokids:question:' || 'WATER_4')::uuid,
    md5('nimokids:question:' || 'WATER_5')::uuid,
    md5('nimokids:question:' || 'FRUIT_1')::uuid,
    md5('nimokids:question:' || 'FRUIT_2')::uuid,
    md5('nimokids:question:' || 'FRUIT_3')::uuid,
    md5('nimokids:question:' || 'FRUIT_4')::uuid,
    md5('nimokids:question:' || 'FRUIT_5')::uuid,
    md5('nimokids:question:' || 'VEG_1')::uuid,
    md5('nimokids:question:' || 'VEG_2')::uuid,
    md5('nimokids:question:' || 'VEG_3')::uuid,
    md5('nimokids:question:' || 'VEG_4')::uuid,
    md5('nimokids:question:' || 'VEG_5')::uuid,
    md5('nimokids:question:' || 'AIR_1')::uuid,
    md5('nimokids:question:' || 'AIR_2')::uuid,
    md5('nimokids:question:' || 'AIR_3')::uuid,
    md5('nimokids:question:' || 'AIR_4')::uuid,
    md5('nimokids:question:' || 'AIR_5')::uuid,
    md5('nimokids:question:' || 'SEA_1')::uuid,
    md5('nimokids:question:' || 'SEA_2')::uuid,
    md5('nimokids:question:' || 'SEA_3')::uuid,
    md5('nimokids:question:' || 'SEA_4')::uuid,
    md5('nimokids:question:' || 'SEA_5')::uuid,
    md5('nimokids:question:' || 'MATH_1')::uuid,
    md5('nimokids:question:' || 'MATH_2')::uuid,
    md5('nimokids:question:' || 'MATH_3')::uuid,
    md5('nimokids:question:' || 'MATH_4')::uuid,
    md5('nimokids:question:' || 'MATH_5')::uuid);

DELETE FROM game_questions q
WHERE q.is_active = FALSE
  AND q.id IN (
    md5('nimokids:question:' || 'FLY_1')::uuid,
    md5('nimokids:question:' || 'FLY_2')::uuid,
    md5('nimokids:question:' || 'FLY_3')::uuid,
    md5('nimokids:question:' || 'FLY_4')::uuid,
    md5('nimokids:question:' || 'FLY_5')::uuid,
    md5('nimokids:question:' || 'FARM_1')::uuid,
    md5('nimokids:question:' || 'FARM_2')::uuid,
    md5('nimokids:question:' || 'FARM_3')::uuid,
    md5('nimokids:question:' || 'FARM_4')::uuid,
    md5('nimokids:question:' || 'FARM_5')::uuid,
    md5('nimokids:question:' || 'WATER_1')::uuid,
    md5('nimokids:question:' || 'WATER_2')::uuid,
    md5('nimokids:question:' || 'WATER_3')::uuid,
    md5('nimokids:question:' || 'WATER_4')::uuid,
    md5('nimokids:question:' || 'WATER_5')::uuid,
    md5('nimokids:question:' || 'FRUIT_1')::uuid,
    md5('nimokids:question:' || 'FRUIT_2')::uuid,
    md5('nimokids:question:' || 'FRUIT_3')::uuid,
    md5('nimokids:question:' || 'FRUIT_4')::uuid,
    md5('nimokids:question:' || 'FRUIT_5')::uuid,
    md5('nimokids:question:' || 'VEG_1')::uuid,
    md5('nimokids:question:' || 'VEG_2')::uuid,
    md5('nimokids:question:' || 'VEG_3')::uuid,
    md5('nimokids:question:' || 'VEG_4')::uuid,
    md5('nimokids:question:' || 'VEG_5')::uuid,
    md5('nimokids:question:' || 'AIR_1')::uuid,
    md5('nimokids:question:' || 'AIR_2')::uuid,
    md5('nimokids:question:' || 'AIR_3')::uuid,
    md5('nimokids:question:' || 'AIR_4')::uuid,
    md5('nimokids:question:' || 'AIR_5')::uuid,
    md5('nimokids:question:' || 'SEA_1')::uuid,
    md5('nimokids:question:' || 'SEA_2')::uuid,
    md5('nimokids:question:' || 'SEA_3')::uuid,
    md5('nimokids:question:' || 'SEA_4')::uuid,
    md5('nimokids:question:' || 'SEA_5')::uuid,
    md5('nimokids:question:' || 'MATH_1')::uuid,
    md5('nimokids:question:' || 'MATH_2')::uuid,
    md5('nimokids:question:' || 'MATH_3')::uuid,
    md5('nimokids:question:' || 'MATH_4')::uuid,
    md5('nimokids:question:' || 'MATH_5')::uuid)
  AND NOT EXISTS (SELECT 1 FROM session_questions sq WHERE sq.question_id = q.id);

UPDATE answer_items SET is_active = FALSE WHERE code IN ('FOOD_FRUITS', 'ANIMAL_CAT', 'ANIMAL_DOG', 'ANIMAL_RABBIT', 'ANIMAL_COW', 'ANIMAL_PIG', 'ANIMAL_SHEEP', 'ANIMAL_HORSE', 'ANIMAL_GOAT', 'ANIMAL_ELEPHANT', 'ANIMAL_LION', 'ANIMAL_MONKEY', 'ANIMAL_BEAR', 'ANIMAL_TIGER', 'ANIMAL_FISH', 'ANIMAL_WHALE', 'ANIMAL_DOLPHIN', 'ANIMAL_CRAB', 'ANIMAL_OCTOPUS', 'ANIMAL_TURTLE', 'ANIMAL_BIRD', 'ANIMAL_EAGLE', 'ANIMAL_OWL', 'ANIMAL_BUTTERFLY', 'ANIMAL_BEE', 'FOOD_APPLE', 'FOOD_BANANA', 'FOOD_STRAWBERRY', 'FOOD_ORANGE', 'FOOD_GRAPES', 'FOOD_CARROT', 'FOOD_BROCCOLI', 'FOOD_POTATO', 'FOOD_CORN', 'FOOD_CUCUMBER', 'VEHICLE_CAR', 'VEHICLE_BUS', 'VEHICLE_BICYCLE', 'VEHICLE_TRAIN', 'VEHICLE_TRUCK', 'VEHICLE_AIRPLANE', 'VEHICLE_HELICOPTER', 'VEHICLE_ROCKET', 'VEHICLE_BALLOON', 'VEHICLE_DRONE', 'VEHICLE_BOAT', 'VEHICLE_SHIP', 'VEHICLE_SUBMARINE', 'VEHICLE_CANOE', 'VEHICLE_FERRY', 'NUMBER_1', 'NUMBER_2', 'NUMBER_3', 'NUMBER_4', 'NUMBER_5', 'NUMBER_6', 'NUMBER_7', 'NUMBER_8', 'NUMBER_9');

DELETE FROM answer_items a
WHERE a.code IN ('FOOD_FRUITS', 'ANIMAL_CAT', 'ANIMAL_DOG', 'ANIMAL_RABBIT', 'ANIMAL_COW', 'ANIMAL_PIG', 'ANIMAL_SHEEP', 'ANIMAL_HORSE', 'ANIMAL_GOAT', 'ANIMAL_ELEPHANT', 'ANIMAL_LION', 'ANIMAL_MONKEY', 'ANIMAL_BEAR', 'ANIMAL_TIGER', 'ANIMAL_FISH', 'ANIMAL_WHALE', 'ANIMAL_DOLPHIN', 'ANIMAL_CRAB', 'ANIMAL_OCTOPUS', 'ANIMAL_TURTLE', 'ANIMAL_BIRD', 'ANIMAL_EAGLE', 'ANIMAL_OWL', 'ANIMAL_BUTTERFLY', 'ANIMAL_BEE', 'FOOD_APPLE', 'FOOD_BANANA', 'FOOD_STRAWBERRY', 'FOOD_ORANGE', 'FOOD_GRAPES', 'FOOD_CARROT', 'FOOD_BROCCOLI', 'FOOD_POTATO', 'FOOD_CORN', 'FOOD_CUCUMBER', 'VEHICLE_CAR', 'VEHICLE_BUS', 'VEHICLE_BICYCLE', 'VEHICLE_TRAIN', 'VEHICLE_TRUCK', 'VEHICLE_AIRPLANE', 'VEHICLE_HELICOPTER', 'VEHICLE_ROCKET', 'VEHICLE_BALLOON', 'VEHICLE_DRONE', 'VEHICLE_BOAT', 'VEHICLE_SHIP', 'VEHICLE_SUBMARINE', 'VEHICLE_CANOE', 'VEHICLE_FERRY', 'NUMBER_1', 'NUMBER_2', 'NUMBER_3', 'NUMBER_4', 'NUMBER_5', 'NUMBER_6', 'NUMBER_7', 'NUMBER_8', 'NUMBER_9')
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);

UPDATE topics SET is_active = FALSE WHERE code IN ('ANIMALS_FLY', 'ANIMALS_FARM', 'ANIMALS_WATER', 'FOOD_FRUITS', 'FOOD_VEGETABLES', 'VEHICLES_AIR', 'VEHICLES_WATER', 'MATH');

DELETE FROM topics t
WHERE t.code IN ('ANIMALS_FLY', 'ANIMALS_FARM', 'ANIMALS_WATER', 'FOOD_FRUITS', 'FOOD_VEGETABLES', 'VEHICLES_AIR', 'VEHICLES_WATER', 'MATH')
  AND t.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.topic_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM game_sessions s WHERE s.topic_id = t.id);

DELETE FROM topics t
WHERE t.code IN ('ANIMALS_FLY', 'ANIMALS_FARM', 'ANIMALS_WATER', 'FOOD_FRUITS', 'FOOD_VEGETABLES', 'VEHICLES_AIR', 'VEHICLES_WATER', 'MATH')
  AND NOT EXISTS (SELECT 1 FROM topics c WHERE c.parent_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.topic_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM game_sessions s WHERE s.topic_id = t.id);

-- Questions created by the very first (static options) seed became inactive "LEGACY" rows in V5: drop them when unused.
DELETE FROM game_questions q
WHERE q.correct_answer_item_id IN (SELECT id FROM answer_items WHERE code LIKE 'LEGACY\_%')
  AND NOT EXISTS (SELECT 1 FROM session_questions sq WHERE sq.question_id = q.id);

DELETE FROM answer_items a
WHERE a.code LIKE 'LEGACY\_%'
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);

-- ---------------------------------------------------------------------------
-- Stickers. FIRST_GAME and PERFECT_SCORE are awarded by rules in the service.
-- ANIMAL_LOVER has no award rule defined yet in the business documents.
-- ---------------------------------------------------------------------------
INSERT INTO stickers (id, code, name, description, rarity) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'FIRST_GAME', 'First Game', 'You finished your first game!', 'COMMON'),
    ('d0000000-0000-4000-8000-000000000002', 'PERFECT_SCORE', 'Perfect Score', 'All answers correct!', 'RARE'),
    ('d0000000-0000-4000-8000-000000000003', 'ANIMAL_LOVER', 'Animal Lover', 'You love animals!', 'COMMON')
ON CONFLICT DO NOTHING;
