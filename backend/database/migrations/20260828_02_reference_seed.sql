-- Hangat reference seed (idempotent, non-overwriting)
-- Existing rows are never updated. A conflicting existing code/name/order must
-- fail visibly and be investigated instead of being overwritten.

INSERT INTO data_sources (
    code, display_name, provider_name, attribution_text, display_order, is_active
)
SELECT
    'KTO',
    '한국관광공사 TourAPI',
    '한국관광공사',
    '관광정보·사진: 한국관광공사 TourAPI',
    1,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM data_sources WHERE code = 'KTO'
);

INSERT INTO data_sources (
    code, display_name, provider_name, attribution_text, display_order, is_active
)
SELECT
    'KAKAO_LOCAL',
    '카카오 로컬',
    '카카오',
    '장소정보: 카카오 로컬',
    2,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM data_sources WHERE code = 'KAKAO_LOCAL'
);

INSERT INTO regions (
    code, name, center_lat, center_lng, kma_grid_x, kma_grid_y,
    display_order, is_active
)
SELECT 'EAST', '동부', NULL, NULL, NULL, NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM regions WHERE code = 'EAST');

INSERT INTO regions (
    code, name, center_lat, center_lng, kma_grid_x, kma_grid_y,
    display_order, is_active
)
SELECT 'WEST', '서부', NULL, NULL, NULL, NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM regions WHERE code = 'WEST');

INSERT INTO regions (
    code, name, center_lat, center_lng, kma_grid_x, kma_grid_y,
    display_order, is_active
)
SELECT 'SOUTH', '남부', NULL, NULL, NULL, NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM regions WHERE code = 'SOUTH');

INSERT INTO regions (
    code, name, center_lat, center_lng, kma_grid_x, kma_grid_y,
    display_order, is_active
)
SELECT 'NORTH', '북부', NULL, NULL, NULL, NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM regions WHERE code = 'NORTH');

INSERT INTO place_categories (code, name, display_order, is_active)
SELECT 'TOURIST', '관광지', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM place_categories WHERE code = 'TOURIST');

INSERT INTO place_categories (code, name, display_order, is_active)
SELECT 'CAFE', '카페', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM place_categories WHERE code = 'CAFE');

INSERT INTO place_categories (code, name, display_order, is_active)
SELECT 'FOOD', '음식점', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM place_categories WHERE code = 'FOOD');

INSERT INTO place_categories (code, name, display_order, is_active)
SELECT 'LODGING', '숙박', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM place_categories WHERE code = 'LODGING');

-- Fail-by-verification policy:
-- 20260828_verify.sql compares every official seed field. This seed file does
-- not silently UPDATE a row whose code already exists with different values.
