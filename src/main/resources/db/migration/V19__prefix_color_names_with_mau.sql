-- Vietnamese color answers read "Màu đỏ", "Màu đen" ... instead of the bare color word ("Đỏ", "Đen").
-- Only the Vietnamese text of the 11 COLORS answer items changes; English texts and the item codes stay the same.

UPDATE answer_items a
SET i18n = jsonb_set(COALESCE(a.i18n, '{}'::jsonb), '{vi,text}', to_jsonb(v.name_vi)), updated_at = now()
FROM (VALUES
    ('COLORS_ORANGE', 'Màu cam'),
    ('COLORS_PINK', 'Màu hồng'),
    ('COLORS_BROWN', 'Màu nâu'),
    ('COLORS_WHITE', 'Màu trắng'),
    ('COLORS_PURPLE', 'Màu tím'),
    ('COLORS_YELLOW', 'Màu vàng'),
    ('COLORS_BLUE', 'Màu xanh dương'),
    ('COLORS_GREEN', 'Màu xanh lá cây'),
    ('COLORS_GRAY', 'Màu xám'),
    ('COLORS_BLACK', 'Màu đen'),
    ('COLORS_RED', 'Màu đỏ')
) AS v(code, name_vi)
WHERE a.code = v.code
  AND a.i18n #>> '{vi,text}' IS DISTINCT FROM v.name_vi;
