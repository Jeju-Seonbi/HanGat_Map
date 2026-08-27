-- Read-only verification for the Hangat MariaDB migration.
-- This file contains SELECT/SHOW statements only.

SELECT DATABASE() AS target_catalog, VERSION() AS mariadb_version;

-- 1. Required tables
SELECT required.table_name,
       CASE WHEN actual.table_name IS NULL THEN 'MISSING' ELSE 'EXISTS' END AS status
FROM (
    SELECT 'data_sources' AS table_name
    UNION ALL SELECT 'regions'
    UNION ALL SELECT 'place_categories'
    UNION ALL SELECT 'places'
    UNION ALL SELECT 'place_source_mappings'
    UNION ALL SELECT 'courses'
    UNION ALL SELECT 'course_items'
) required
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required.table_name
ORDER BY required.table_name;

-- 2. Full column contract: type, unsigned, length/precision, nullability/default
SELECT table_name,
       ordinal_position,
       column_name,
       column_type,
       is_nullable,
       column_default,
       character_maximum_length,
       numeric_precision,
       numeric_scale,
       character_set_name,
       collation_name,
       column_key,
       extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'data_sources', 'regions', 'place_categories', 'places',
      'place_source_mappings', 'courses', 'course_items'
  )
ORDER BY table_name, ordinal_position;

-- 3. Legacy timestamps must still exist but be nullable in phase 1.
SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'courses'
  AND column_name IN ('create_date', 'update_date', 'created_at', 'updated_at')
ORDER BY column_name;

-- 4. Required new columns
SELECT expected.table_name,
       expected.column_name,
       CASE WHEN actual.column_name IS NULL THEN 'MISSING' ELSE 'EXISTS' END AS status,
       actual.column_type,
       actual.is_nullable
FROM (
    SELECT 'courses' AS table_name, 'parent_course_id' AS column_name
    UNION ALL SELECT 'course_items', 'recommendation_score'
    UNION ALL SELECT 'places', 'good_price_base_date'
    UNION ALL SELECT 'places', 'hidden_gem_score'
    UNION ALL SELECT 'places', 'hidden_gem_algorithm_version'
    UNION ALL SELECT 'places', 'hidden_gem_calculated_at'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
ORDER BY expected.table_name, expected.column_name;

-- 5. PK, FK, UNIQUE and CHECK constraints
SELECT tc.table_name,
       tc.constraint_name,
       tc.constraint_type,
       kcu.column_name,
       kcu.referenced_table_name,
       kcu.referenced_column_name,
       rc.update_rule,
       rc.delete_rule
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.key_column_usage kcu
  ON kcu.constraint_schema = tc.constraint_schema
 AND kcu.table_name = tc.table_name
 AND kcu.constraint_name = tc.constraint_name
LEFT JOIN information_schema.referential_constraints rc
  ON rc.constraint_schema = tc.constraint_schema
 AND rc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name IN (
      'data_sources', 'regions', 'place_categories', 'places',
      'place_source_mappings', 'courses', 'course_items'
  )
ORDER BY tc.table_name, tc.constraint_type, tc.constraint_name,
         kcu.ordinal_position;

SELECT tc.table_name, cc.constraint_name, cc.check_clause
FROM information_schema.check_constraints cc
JOIN information_schema.table_constraints tc
  ON tc.constraint_schema = cc.constraint_schema
 AND tc.constraint_name = cc.constraint_name
WHERE cc.constraint_schema = DATABASE()
  AND tc.table_name IN (
      'data_sources', 'regions', 'place_categories', 'places',
      'place_source_mappings', 'courses', 'course_items'
  )
ORDER BY tc.table_name, cc.constraint_name;

-- 6. Indexes and unique ordering
SELECT table_name,
       index_name,
       non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN (
      'data_sources', 'regions', 'place_categories', 'places',
      'place_source_mappings', 'courses', 'course_items'
  )
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- 7. FK character/collation compatibility for source_code
SELECT table_name, column_name, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
      (table_name = 'data_sources' AND column_name = 'code')
      OR
      (table_name = 'place_source_mappings' AND column_name = 'source_code')
  )
ORDER BY table_name;

-- 8. Exact project-approved reference seed
SELECT expected.code,
       CASE
           WHEN actual.code IS NULL THEN 'MISSING'
           WHEN actual.name = expected.name
            AND actual.center_lat IS NULL
            AND actual.center_lng IS NULL
            AND actual.kma_grid_x IS NULL
            AND actual.kma_grid_y IS NULL
            AND actual.display_order = expected.display_order
            AND actual.is_active = 1 THEN 'MATCH'
           ELSE 'MISMATCH'
       END AS status,
       actual.name,
       actual.center_lat,
       actual.center_lng,
       actual.kma_grid_x,
       actual.kma_grid_y,
       actual.display_order,
       actual.is_active
FROM (
    SELECT 'EAST' AS code, '동부' AS name, 1 AS display_order
    UNION ALL SELECT 'WEST', '서부', 2
    UNION ALL SELECT 'SOUTH', '남부', 3
    UNION ALL SELECT 'NORTH', '북부', 4
) expected
LEFT JOIN regions actual ON actual.code = expected.code
ORDER BY expected.display_order;

SELECT expected.code,
       CASE
           WHEN actual.code IS NULL THEN 'MISSING'
           WHEN actual.name = expected.name
            AND actual.display_order = expected.display_order
            AND actual.is_active = 1 THEN 'MATCH'
           ELSE 'MISMATCH'
       END AS status,
       actual.name,
       actual.display_order,
       actual.is_active
FROM (
    SELECT 'TOURIST' AS code, '관광지' AS name, 1 AS display_order
    UNION ALL SELECT 'CAFE', '카페', 2
    UNION ALL SELECT 'FOOD', '음식점', 3
    UNION ALL SELECT 'LODGING', '숙박', 4
) expected
LEFT JOIN place_categories actual ON actual.code = expected.code
ORDER BY expected.display_order;

SELECT expected.code,
       CASE
           WHEN actual.code IS NULL THEN 'MISSING'
           WHEN actual.display_name = expected.display_name
            AND actual.provider_name = expected.provider_name
            AND actual.attribution_text = expected.attribution_text
            AND actual.display_order = 1
            AND actual.is_active = 1 THEN 'MATCH'
           ELSE 'MISMATCH'
       END AS status,
       actual.display_name,
       actual.provider_name,
       actual.attribution_text,
       actual.display_order,
       actual.is_active
FROM (
    SELECT 'KTO' AS code,
           '한국관광공사 TourAPI' AS display_name,
           '한국관광공사' AS provider_name,
           '관광정보·사진: 한국관광공사 TourAPI' AS attribution_text
) expected
LEFT JOIN data_sources actual ON actual.code = expected.code;

-- 9. Duplicate and unexpected reference identities
SELECT code, COUNT(*) AS row_count
FROM regions
GROUP BY code
HAVING COUNT(*) <> 1;

SELECT code, COUNT(*) AS row_count
FROM place_categories
GROUP BY code
HAVING COUNT(*) <> 1;

SELECT code, COUNT(*) AS row_count
FROM data_sources
GROUP BY code
HAVING COUNT(*) <> 1;

-- 10. Human-readable final DDL evidence
SHOW CREATE TABLE data_sources;
SHOW CREATE TABLE regions;
SHOW CREATE TABLE place_categories;
SHOW CREATE TABLE places;
SHOW CREATE TABLE place_source_mappings;
SHOW CREATE TABLE courses;
SHOW CREATE TABLE course_items;
