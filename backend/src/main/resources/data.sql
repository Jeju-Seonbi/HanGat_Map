-- 맵 도메인 마스터 코드 데이터 (담당: 이후경)
--
-- 더미 데이터가 아니라 places.region_id / primary_category_id 가 참조하는 FK 대상이다.
-- 이 행들이 없으면 장소를 한 건도 INSERT 할 수 없다(둘 다 NOT NULL FK).
-- 공공 API가 주지 않는 값이라 우리가 정의한다 - 설계서 §5.1 참고.
--
-- dev 프로필에서 매 부팅 재실행된다(spring.sql.init.mode=always).
-- ★ places 는 DELETE 하지 않는다 - 2단계부터 KTO 실데이터가 들어가는데 매 부팅 지워버린다.
--   대신 places 에 행이 생긴 뒤에는 아래 마스터 DELETE 가 FK 제약에 걸리므로,
--   실데이터 적재를 시작할 때 sql.init.mode 를 끄거나 이 파일을 걷어낸다.

DELETE FROM place_categories;
DELETE FROM regions;

-- ─────────────────────────────────────────────────────────────
-- 권역 4개
--
-- 제주 관광 관용 구분(행정구역 기준). KTO 는 제주시/서귀포시 2개만 주므로 자체 정의한다.
--   북부 = 제주시 동(洞)      동부 = 조천·구좌·성산읍, 표선면, 남원읍
--   남부 = 서귀포시 동(洞)    서부 = 애월·한림·대정읍, 한경면, 안덕면
--   ※ 우도면은 동부로 묶고, 추자면은 본섬 밖이라 어느 권역에도 넣지 않는다
--
-- center_lat/lng = KTO 제주 2,147건을 위 규칙으로 분류한 뒤 구한 실측 중앙값(2026-08-23).
--   평균이 아니라 중앙값을 쓴다 - 동부는 조천~성산 폭이 넓어 평균이 서쪽으로 끌린다.
--
-- kma_grid_x/y = NULL. 기상청 격자는 날씨 담당(jdh) 몫이고, 현재 구현은 제주 전역을
--   단일 격자(nx=52, ny=38)로 처리한다. 권역별 격자가 필요해지면 그때 채운다.
--   ★ 검증 안 된 값을 넣지 않는다(설계서 §1.2 데이터 정직성).
--
-- display_order 는 UNIQUE 제약이 있어 1~4 로 서로 달라야 한다.
-- ─────────────────────────────────────────────────────────────
INSERT INTO regions (code, name, center_lat, center_lng, kma_grid_x, kma_grid_y, display_order, is_active, created_at, updated_at) VALUES
  ('NORTH', '북부', 33.4950010, 126.5169050, NULL, NULL, 1, TRUE, NOW(), NOW()),
  ('EAST',  '동부', 33.4592800, 126.7843930, NULL, NULL, 2, TRUE, NOW(), NOW()),
  ('SOUTH', '남부', 33.2487280, 126.5142330, NULL, NULL, 3, TRUE, NOW(), NOW()),
  ('WEST',  '서부', 33.3434170, 126.3124120, NULL, NULL, 4, TRUE, NOW(), NOW());

-- ─────────────────────────────────────────────────────────────
-- 장소 카테고리 7개
--
-- 서로 다른 두 출처를 하나로 모으는 사전이다.
--   KTO contenttypeid(숫자)  →  code
--   소상공인 indsSclsCd(문자) →  code
--
-- KTO 제주 실측 건수(2026-08-23) 기준 매핑:
--   12 관광지 563 + 14 문화시설 98 + 15 축제공연 28 + 28 레포츠 137  →  TOURIST (826)
--   39 음식점 716                                                    →  FOOD
--   32 숙박 210                                                      →  LODGING
--   38 쇼핑 395                                                      →  SHOPPING
--   ※ 25 여행코스는 제주 0건이라 매핑 대상 없음
--
-- 소상공인 상가정보:
--   I21201 카페  →  CAFE      G20405 편의점  →  CONVENIENCE
--   G20404 슈퍼마켓 →  MART
--
-- 프론트 필터(spot/food/dine/cafe/cvs/stay/mart)와의 대응은 설계서 §2.1 표를 따른다.
--   food = is_good_price 플래그이지 카테고리가 아니다.
--   SHOPPING 은 화면 필터에 없다 - 데이터는 적재하되 노출은 나중에 결정한다.
-- ─────────────────────────────────────────────────────────────
INSERT INTO place_categories (code, name, display_order, is_active, created_at, updated_at) VALUES
  ('TOURIST',     '관광지',  1, TRUE, NOW(), NOW()),
  ('FOOD',        '음식점',  2, TRUE, NOW(), NOW()),
  ('CAFE',        '카페',    3, TRUE, NOW(), NOW()),
  ('LODGING',     '숙소',    4, TRUE, NOW(), NOW()),
  ('CONVENIENCE', '편의점',  5, TRUE, NOW(), NOW()),
  ('MART',        '마트',    6, TRUE, NOW(), NOW()),
  ('SHOPPING',    '쇼핑',    7, TRUE, NOW(), NOW());
