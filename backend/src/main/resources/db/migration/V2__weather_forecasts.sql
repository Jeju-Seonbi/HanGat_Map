-- V2: 권역 날씨 예보(테이블 명세서 17.0) + course_items 날씨 스냅숏 FK 전환
--
-- 발표 시각(base_at)별 append 이력이다 - UPSERT로 덮어쓰면 "저장 시점 예보 vs 최신 예보" 비교(MY_008)가
-- 불가능해진다. congestion_forecasts(16.0)와 같은 구조.
-- 1단계(2026-09-03)는 DAILY 행만 적재한다. HOURLY 전용 컬럼(temperature, precipitation_mm, snow_cm,
-- wind_speed, humidity)은 명세서와 스키마를 맞추기 위해 두고 NULL로 남긴다.
-- UNSIGNED·CHECK는 팀 컨벤션대로 재현하지 않는다(값 범위는 적재 코드가 거른다).

CREATE TABLE `weather_forecasts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `region_id` smallint(6) NOT NULL,
  `forecast_at` datetime(6) NOT NULL,
  `base_at` datetime(6) NOT NULL,
  `granularity` enum('DAILY','HOURLY') NOT NULL,
  `sky_code` varchar(20) DEFAULT NULL,
  `precipitation_type` enum('NONE','RAIN','RAIN_SNOW','SNOW','SHOWER','UNKNOWN') DEFAULT NULL,
  `temperature` decimal(4,1) DEFAULT NULL,
  `temp_min` decimal(4,1) DEFAULT NULL,
  `temp_max` decimal(4,1) DEFAULT NULL,
  `rain_probability` tinyint(4) DEFAULT NULL,
  `precipitation_mm` decimal(7,2) DEFAULT NULL,
  `snow_cm` decimal(6,2) DEFAULT NULL,
  `wind_speed` decimal(5,2) DEFAULT NULL,
  `humidity` tinyint(4) DEFAULT NULL,
  `source_code` varchar(30) NOT NULL,
  `fetched_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weather_region_forecast_base_gran` (`region_id`,`forecast_at`,`base_at`,`granularity`),
  KEY `idx_weather_region_forecast` (`region_id`,`forecast_at`,`base_at`),
  KEY `idx_weather_base_at` (`base_at`),
  KEY `fk_weather_source` (`source_code`),
  CONSTRAINT `fk_weather_region` FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`),
  CONSTRAINT `fk_weather_source` FOREIGN KEY (`source_code`) REFERENCES `data_sources` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- course_items.planned_weather_forecast_id 는 V1부터 있었지만 참조 테이블이 없어 FK가 빠져 있었다.
-- 명세서 FK 관계 34.0: ON DELETE SET NULL - 예보 행이 지워지면 스냅숏은 '날씨 정보 없음'으로 돌아간다.
ALTER TABLE `course_items`
  ADD KEY `fk_course_items_planned_weather` (`planned_weather_forecast_id`),
  ADD CONSTRAINT `fk_course_items_planned_weather`
    FOREIGN KEY (`planned_weather_forecast_id`) REFERENCES `weather_forecasts` (`id`) ON DELETE SET NULL;
