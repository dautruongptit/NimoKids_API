-- Every vocabulary-v2 answer item carries its own code as metadata.item. A generated question names its target with it
-- (correct_match = {"item": code}) and the option generator uses it to prove that exactly one option is right.
-- V16 rewrote the metadata of the items and did not set the tag; R__zz_templates.sql used to add it afterwards, but a
-- repeatable migration does not run again when only the data changed, so databases upgrading to V16 were left without it.
-- Items of V16 and V17 all have metadata.age_group (the old sample items do not).

UPDATE answer_items
SET metadata = metadata || jsonb_build_object('item', code), updated_at = now()
WHERE metadata ? 'age_group'
  AND metadata ->> 'item' IS DISTINCT FROM code;
