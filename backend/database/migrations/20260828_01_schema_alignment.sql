-- Hangat MariaDB schema alignment plan (MariaDB 10.6)
-- IMPORTANT:
--   1. This file is reviewed manual SQL. It is not executed automatically.
--   2. Stop the application and take a verified backup before execution.
--   3. Run 20260828_verify.sql before and after this file.
--   4. The statements target the schema snapshot confirmed on 2026-08-28.
--   5. MariaDB DDL causes implicit commits; this file is not transactionally atomic.
--   6. Do not disable FOREIGN_KEY_CHECKS to force the migration through.

-- Preflight evidence expected for the current local schema:
SELECT DATABASE() AS target_catalog;
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'courses', 'course_items', 'places', 'place_source_mappings',
      'regions', 'place_categories', 'data_sources'
  )
ORDER BY table_name;

-- The current schema uses Hibernate-generated constraint names. Fail fast if the
-- snapshot no longer matches instead of guessing or disabling FK checks.
SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('places', 'place_source_mappings', 'course_items')
ORDER BY table_name, constraint_type, constraint_name;

-- ---------------------------------------------------------------------------
-- 1. External data source master
-- ---------------------------------------------------------------------------
-- Charset/collation inherit the active database defaults. The verify script
-- confirms that data_sources.code and place_source_mappings.source_code match
-- before the FK is considered valid.
CREATE TABLE data_sources (
    code VARCHAR(30) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    homepage_url VARCHAR(1000) NULL,
    api_url VARCHAR(1000) NULL,
    license_name VARCHAR(100) NULL,
    license_url VARCHAR(1000) NULL,
    attribution_text VARCHAR(300) NOT NULL,
    disclaimer_text VARCHAR(500) NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_data_sources PRIMARY KEY (code),
    CONSTRAINT uk_data_sources_display_name UNIQUE (display_name),
    CONSTRAINT chk_data_sources_active CHECK (is_active IN (0, 1)),
    INDEX idx_data_sources_provider_name (provider_name),
    INDEX idx_data_sources_display_order (display_order)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 2. Drop only the FKs that block coordinated PK/FK type alignment.
--    These names come from the confirmed current schema snapshot.
-- ---------------------------------------------------------------------------
ALTER TABLE course_items
    DROP FOREIGN KEY FKa64cargjwoto5c4jin9k3cbh7,
    DROP FOREIGN KEY FKjojhr5ovv1u13tdyy6ihxy4on;

ALTER TABLE place_source_mappings
    DROP FOREIGN KEY FK4vmoug1ufqlxtegir4v6uthmn;

ALTER TABLE places
    DROP FOREIGN KEY FK2lkjoxuwr4yu8n2asamaimeug,
    DROP FOREIGN KEY FKsic9b3be5q0a38vb3aii7yfhs;

-- ---------------------------------------------------------------------------
-- 3. Reference tables
-- ---------------------------------------------------------------------------
ALTER TABLE regions
    MODIFY COLUMN id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN code VARCHAR(20) NOT NULL,
    MODIFY COLUMN name VARCHAR(30) NOT NULL,
    MODIFY COLUMN center_lat DECIMAL(10,7) NULL DEFAULT NULL,
    MODIFY COLUMN center_lng DECIMAL(10,7) NULL DEFAULT NULL,
    MODIFY COLUMN kma_grid_x SMALLINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN kma_grid_y SMALLINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN display_order TINYINT UNSIGNED NOT NULL DEFAULT 1,
    MODIFY COLUMN is_active BOOLEAN NOT NULL DEFAULT 1,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_regions_center_lat
        CHECK (center_lat BETWEEN -90 AND 90 OR center_lat IS NULL),
    ADD CONSTRAINT chk_regions_center_lng
        CHECK (center_lng BETWEEN -180 AND 180 OR center_lng IS NULL),
    ADD CONSTRAINT chk_regions_active CHECK (is_active IN (0, 1));

ALTER TABLE place_categories
    MODIFY COLUMN id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN code VARCHAR(30) NOT NULL,
    MODIFY COLUMN name VARCHAR(50) NOT NULL,
    MODIFY COLUMN display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    MODIFY COLUMN is_active BOOLEAN NOT NULL DEFAULT 1,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_place_categories_active CHECK (is_active IN (0, 1)),
    ADD INDEX idx_place_categories_display_order (display_order);

-- Project decision: place_categories.parent_id is excluded from this migration.

-- ---------------------------------------------------------------------------
-- 4. Places
-- ---------------------------------------------------------------------------
ALTER TABLE places
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN region_id SMALLINT UNSIGNED NOT NULL,
    MODIFY COLUMN primary_category_id SMALLINT UNSIGNED NOT NULL,
    MODIFY COLUMN name VARCHAR(200) NOT NULL,
    MODIFY COLUMN normalized_name VARCHAR(200) NOT NULL,
    MODIFY COLUMN road_address VARCHAR(300) NULL DEFAULT NULL,
    MODIFY COLUMN lot_address VARCHAR(300) NULL DEFAULT NULL,
    MODIFY COLUMN latitude DECIMAL(10,7) NULL DEFAULT NULL,
    MODIFY COLUMN longitude DECIMAL(10,7) NULL DEFAULT NULL,
    MODIFY COLUMN phone VARCHAR(30) NULL DEFAULT NULL,
    MODIFY COLUMN operating_hours_text VARCHAR(500) NULL DEFAULT NULL,
    MODIFY COLUMN rest_day_text VARCHAR(300) NULL DEFAULT NULL,
    MODIFY COLUMN parking_available BOOLEAN NULL DEFAULT NULL,
    MODIFY COLUMN toilet_available BOOLEAN NULL DEFAULT NULL,
    MODIFY COLUMN business_status
        ENUM('OPEN','TEMP_CLOSED','CLOSED','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    MODIFY COLUMN is_good_price BOOLEAN NOT NULL DEFAULT 0,
    MODIFY COLUMN is_hidden_gem BOOLEAN NOT NULL DEFAULT 0,
    MODIFY COLUMN rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN review_count INT UNSIGNED NOT NULL DEFAULT 0,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN good_price_base_date DATE NULL DEFAULT NULL AFTER is_good_price,
    ADD COLUMN hidden_gem_score DECIMAL(6,3) NULL DEFAULT NULL AFTER is_hidden_gem,
    ADD COLUMN hidden_gem_algorithm_version VARCHAR(30) NULL DEFAULT NULL
        AFTER hidden_gem_score,
    ADD COLUMN hidden_gem_calculated_at DATETIME NULL DEFAULT NULL
        AFTER hidden_gem_algorithm_version,
    ADD CONSTRAINT chk_places_latitude
        CHECK (latitude BETWEEN -90 AND 90 OR latitude IS NULL),
    ADD CONSTRAINT chk_places_longitude
        CHECK (longitude BETWEEN -180 AND 180 OR longitude IS NULL),
    ADD CONSTRAINT chk_places_parking
        CHECK (parking_available IN (0, 1) OR parking_available IS NULL),
    ADD CONSTRAINT chk_places_toilet
        CHECK (toilet_available IN (0, 1) OR toilet_available IS NULL),
    ADD CONSTRAINT chk_places_good_price CHECK (is_good_price IN (0, 1)),
    ADD CONSTRAINT chk_places_hidden_gem CHECK (is_hidden_gem IN (0, 1)),
    ADD CONSTRAINT chk_places_hidden_gem_score
        CHECK (hidden_gem_score BETWEEN 0 AND 100 OR hidden_gem_score IS NULL),
    ADD CONSTRAINT chk_places_hidden_gem_metadata
        CHECK (
            hidden_gem_score IS NULL
            OR (
                hidden_gem_algorithm_version IS NOT NULL
                AND hidden_gem_calculated_at IS NOT NULL
            )
        ),
    ADD CONSTRAINT chk_places_rating CHECK (rating_avg BETWEEN 0 AND 5),
    ADD INDEX idx_places_name (name),
    ADD INDEX idx_places_normalized_name (normalized_name),
    ADD INDEX idx_places_coordinates (latitude, longitude),
    ADD INDEX idx_places_region_category (region_id, primary_category_id),
    ADD INDEX idx_places_business_status (business_status),
    ADD INDEX idx_places_good_price (is_good_price),
    ADD INDEX idx_places_hidden_gem (is_hidden_gem, hidden_gem_score),
    ADD INDEX idx_places_hidden_gem_algorithm (hidden_gem_algorithm_version);

-- The ERD index notes mention places.is_active, but the official places column
-- definition and current Entity do not contain that column. Indexes that depend
-- on places.is_active are deferred instead of inventing a schema column here.

-- ---------------------------------------------------------------------------
-- 5. Source mappings
-- ---------------------------------------------------------------------------
ALTER TABLE place_source_mappings
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN place_id BIGINT UNSIGNED NOT NULL,
    MODIFY COLUMN source_code VARCHAR(30) NOT NULL,
    MODIFY COLUMN source_place_id VARCHAR(100) NOT NULL,
    MODIFY COLUMN source_url VARCHAR(1000) NULL DEFAULT NULL,
    MODIFY COLUMN source_updated_at DATETIME NULL DEFAULT NULL,
    MODIFY COLUMN last_synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN data_hash CHAR(64) NULL DEFAULT NULL,
    MODIFY COLUMN is_active BOOLEAN NOT NULL DEFAULT 1,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_place_source_mappings_active CHECK (is_active IN (0, 1)),
    ADD INDEX idx_place_source_mappings_source_active (source_code, is_active),
    ADD INDEX idx_place_source_mappings_last_synced (last_synced_at);

-- ---------------------------------------------------------------------------
-- 6. Courses
-- ---------------------------------------------------------------------------
-- Legacy create_date/update_date are retained for one compatibility phase.
-- They become nullable so the current Entity can write created_at/updated_at.
-- Drop them only in a later approved migration after runtime verification.
ALTER TABLE courses
    MODIFY COLUMN create_date DATETIME(6) NULL DEFAULT NULL,
    MODIFY COLUMN update_date DATETIME(6) NULL DEFAULT NULL,
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN user_id BIGINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN preset_id BIGINT UNSIGNED NULL DEFAULT NULL,
    ADD COLUMN parent_course_id BIGINT UNSIGNED NULL DEFAULT NULL AFTER preset_id,
    MODIFY COLUMN course_type ENUM('USER','SAMPLE') NOT NULL DEFAULT 'USER',
    MODIFY COLUMN generation_reason
        ENUM('INITIAL','USER_REGENERATE','WEATHER_REPLAN','SAMPLE_BATCH')
        NOT NULL DEFAULT 'INITIAL',
    MODIFY COLUMN status
        ENUM('GENERATING','READY','SAVED','FAILED','EXPIRED','DELETED')
        NOT NULL DEFAULT 'GENERATING',
    MODIFY COLUMN title VARCHAR(100) NULL DEFAULT NULL,
    MODIFY COLUMN start_date DATE NOT NULL,
    MODIFY COLUMN end_date DATE NOT NULL,
    MODIFY COLUMN people SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    MODIFY COLUMN budget_total INT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN transport
        ENUM('RENTAL_CAR','PUBLIC_TRANSIT','TAXI','WALK_BIKE') NOT NULL,
    MODIFY COLUMN input_fingerprint CHAR(64) NULL DEFAULT NULL,
    MODIFY COLUMN algorithm_version VARCHAR(30) NULL DEFAULT NULL,
    MODIFY COLUMN estimated_cost_min INT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN estimated_cost_max INT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN average_congestion_rate DECIMAL(5,2) NULL DEFAULT NULL,
    MODIFY COLUMN generation_error_code VARCHAR(50) NULL DEFAULT NULL,
    MODIFY COLUMN generation_completed_at DATETIME NULL DEFAULT NULL,
    MODIFY COLUMN saved_at DATETIME NULL DEFAULT NULL,
    MODIFY COLUMN deleted_at DATETIME NULL DEFAULT NULL,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_courses_sample_shape
        CHECK (
            course_type <> 'SAMPLE'
            OR (preset_id IS NOT NULL AND user_id IS NULL AND title IS NOT NULL)
        ),
    ADD CONSTRAINT chk_courses_saved_shape
        CHECK (
            status <> 'SAVED'
            OR (
                user_id IS NOT NULL
                AND title IS NOT NULL
                AND CHAR_LENGTH(TRIM(title)) >= 1
                AND saved_at IS NOT NULL
            )
        ),
    ADD CONSTRAINT chk_courses_title
        CHECK (title IS NULL OR CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 100),
    ADD CONSTRAINT chk_courses_date_range CHECK (start_date <= end_date),
    ADD CONSTRAINT chk_courses_people CHECK (people BETWEEN 1 AND 100),
    ADD CONSTRAINT chk_courses_cost_range
        CHECK (
            estimated_cost_min IS NULL
            OR estimated_cost_max IS NULL
            OR estimated_cost_min <= estimated_cost_max
        ),
    ADD CONSTRAINT chk_courses_congestion
        CHECK (
            average_congestion_rate BETWEEN 0 AND 100
            OR average_congestion_rate IS NULL
        ),
    ADD CONSTRAINT chk_courses_deleted_shape
        CHECK (
            (status = 'DELETED' AND deleted_at IS NOT NULL)
            OR (status <> 'DELETED' AND deleted_at IS NULL)
        ),
    ADD INDEX idx_courses_user_status_saved (user_id, status, saved_at),
    ADD INDEX idx_courses_preset (preset_id),
    ADD INDEX idx_courses_parent (parent_course_id),
    ADD INDEX idx_courses_course_type_status (course_type, status),
    ADD INDEX idx_courses_status (status),
    ADD INDEX idx_courses_title (title),
    ADD INDEX idx_courses_user_status_start (user_id, status, start_date),
    ADD INDEX idx_courses_input_fingerprint (input_fingerprint),
    ADD INDEX idx_courses_algorithm_version (algorithm_version),
    ADD INDEX idx_courses_saved_at (saved_at),
    ADD INDEX idx_courses_deleted_at (deleted_at),
    ADD CONSTRAINT fk_courses_parent
        FOREIGN KEY (parent_course_id) REFERENCES courses (id)
        ON DELETE SET NULL ON UPDATE RESTRICT;

-- Project decisions: guest_access_hash and expires_at are excluded.
-- TODO: add user_id and preset_id FKs only after users/course_presets migrations exist.

-- ---------------------------------------------------------------------------
-- 7. Course items
-- ---------------------------------------------------------------------------
ALTER TABLE course_items
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN course_id BIGINT UNSIGNED NOT NULL,
    MODIFY COLUMN place_id BIGINT UNSIGNED NOT NULL,
    MODIFY COLUMN day_no SMALLINT UNSIGNED NOT NULL,
    MODIFY COLUMN position SMALLINT UNSIGNED NOT NULL,
    MODIFY COLUMN visit_date DATE NOT NULL,
    MODIFY COLUMN start_time TIME NULL DEFAULT NULL,
    MODIFY COLUMN end_time TIME NULL DEFAULT NULL,
    MODIFY COLUMN item_source
        ENUM('USER_FIXED','AI_RECOMMENDED','REPLACEMENT')
        NOT NULL DEFAULT 'AI_RECOMMENDED',
    MODIFY COLUMN inbound_distance_m INT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN inbound_travel_minutes SMALLINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN planned_congestion_forecast_id BIGINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN planned_weather_forecast_id BIGINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN replaced_from_place_id BIGINT UNSIGNED NULL DEFAULT NULL,
    MODIFY COLUMN recommendation_reason_code VARCHAR(30) NULL DEFAULT NULL,
    MODIFY COLUMN recommendation_reason VARCHAR(300) NULL DEFAULT NULL,
    MODIFY COLUMN memo VARCHAR(300) NULL DEFAULT NULL,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN recommendation_score DECIMAL(8,4) NULL DEFAULT NULL
        AFTER planned_weather_forecast_id,
    ADD CONSTRAINT chk_course_items_day_no CHECK (day_no >= 1),
    ADD CONSTRAINT chk_course_items_position CHECK (position >= 1),
    ADD CONSTRAINT chk_course_items_time
        CHECK (start_time IS NULL OR end_time IS NULL OR start_time < end_time),
    ADD INDEX idx_course_items_course_visit (course_id, visit_date),
    ADD INDEX idx_course_items_visit_date (visit_date);

-- ---------------------------------------------------------------------------
-- 8. Recreate official FKs with explicit stable names and delete policies.
-- ---------------------------------------------------------------------------
ALTER TABLE places
    ADD CONSTRAINT fk_places_region
        FOREIGN KEY (region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_places_primary_category
        FOREIGN KEY (primary_category_id) REFERENCES place_categories (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE place_source_mappings
    ADD CONSTRAINT fk_place_source_mappings_place
        FOREIGN KEY (place_id) REFERENCES places (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_place_source_mappings_source
        FOREIGN KEY (source_code) REFERENCES data_sources (code)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE course_items
    ADD CONSTRAINT fk_course_items_course
        FOREIGN KEY (course_id) REFERENCES courses (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_course_items_place
        FOREIGN KEY (place_id) REFERENCES places (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_course_items_replaced_from_place
        FOREIGN KEY (replaced_from_place_id) REFERENCES places (id)
        ON DELETE SET NULL ON UPDATE RESTRICT;

-- TODO (later migrations):
--   planned_congestion_forecast_id -> congestion_forecasts.id
--   planned_weather_forecast_id    -> weather_forecasts.id
-- These tables do not exist in the confirmed current schema, so no placeholder
-- FK is created here.
