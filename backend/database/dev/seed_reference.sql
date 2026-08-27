-- Hangat local development reference data (MariaDB 10.6)
USE `web`;

INSERT INTO `regions`
    (`code`, `name`, `center_lat`, `center_lng`, `kma_grid_x`, `kma_grid_y`, `display_order`, `is_active`)
VALUES
    ('EAST', '동부', NULL, NULL, NULL, NULL, 1, 1),
    ('WEST', '서부', NULL, NULL, NULL, NULL, 2, 1),
    ('SOUTH', '남부', NULL, NULL, NULL, NULL, 3, 1),
    ('NORTH', '북부', NULL, NULL, NULL, NULL, 4, 1);

INSERT INTO `place_categories` (`code`, `name`, `display_order`, `is_active`)
VALUES
    ('TOURIST', '관광지', 1, 1),
    ('CAFE', '카페', 2, 1),
    ('FOOD', '음식점', 3, 1),
    ('LODGING', '숙박', 4, 1);

INSERT INTO `data_sources`
    (`code`, `display_name`, `provider_name`, `attribution_text`, `display_order`, `is_active`)
VALUES
    ('KTO', '한국관광공사 TourAPI', '한국관광공사',
     '관광정보·사진: 한국관광공사 TourAPI', 1, 1);
