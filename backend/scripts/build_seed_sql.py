# -*- coding: utf-8 -*-
"""
시드 SQL 생성기 - 실측 산출물 → src/main/resources/seed-dev.sql

원천 (저장소 외부 - 정동현 로컬 실측 산출물):
  - pois.json            : TourAPI 전수 스냅샷 (1,300곳)
  - b_cnctrRate_full_*.json : 관광공사 집중률 30일 실측 (제주시+서귀포, 13,440행)

전략 (v1):
  - 집중률에 이름이 '정확 일치'하는 관광지 중 데모 스팟 우선 + 집중률 커버 순 50곳
  - 예보는 실측 전체 기간(30일) 그대로 - calm-places를 기간 내 아무 날짜로나 검증 가능
  - INSERT IGNORE 라 매 부팅 재실행돼도 유니크 제약이 중복을 걸러줌 (멱등)

실행: python scripts/build_seed_sql.py
"""
import json
import io
import sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

BACKEND = Path(__file__).resolve().parent.parent
SOURCE_ROOT = Path(r"C:\Users\jdh\Desktop\한갓지도")
POIS = SOURCE_ROOT / "frontend/src/data/pois.json"
CNCTR = [SOURCE_ROOT / "w0/results/b_cnctrRate_full_50110.json",
         SOURCE_ROOT / "w0/results/b_cnctrRate_full_50130.json"]
OUT = BACKEND / "src/main/resources/seed-dev.sql"

PLACE_LIMIT = 50
# 심사 데모 시나리오에 등장하는 스팟 - 반드시 포함
DEMO_SPOTS = ["성산일출봉", "비자림", "혼인지", "광치기해변", "다랑쉬오름(월랑봉)",
              "새별오름", "용두암", "천지연폭포", "함덕해수욕장", "협재해수욕장"]

REGION = {"제주시내": "JEJU_CITY", "서귀포시내": "SEOGWIPO", "동부": "EAST", "서부": "WEST", "남부": "SOUTH"}
CATEGORY = {"관광지": "TOURIST", "식당": "RESTAURANT", "카페": "CAFE", "숙소": "LODGING"}


def esc(value: str) -> str:
    return value.replace("'", "''")


def main() -> None:
    pois = json.load(open(POIS, encoding="utf-8"))
    rates = []
    for path in CNCTR:
        rates += json.load(open(path, encoding="utf-8"))

    covered_names = {row["tAtsNm"] for row in rates}

    # 관광지 + 집중률 정확 일치만 (rateSource가 공사 예보인 것만 시드 - 권역 추정은 2차)
    tourist = [p for p in pois if p.get("type") == "관광지" and p["name"] in covered_names]

    # 데모 스팟 우선, 나머지는 이름순으로 채워 50곳
    demo = [p for p in tourist if any(d in p["name"] for d in DEMO_SPOTS)]
    rest = sorted((p for p in tourist if p not in demo), key=lambda p: p["name"])
    picked = (demo + rest)[:PLACE_LIMIT]

    place_rows = []
    forecast_rows = []
    for i, p in enumerate(picked, start=1):
        content_id = int(p["id"][1:]) if p["id"].startswith("t") else "NULL"
        img = f"'{esc(p['img'])}'" if p.get("img") else "NULL"
        place_rows.append(
            f"({i}, {content_id}, '{esc(p['name'])}', '{REGION[p['region']]}', "
            f"'{CATEGORY[p['type']]}', '{esc(p.get('addr') or '')}', "
            f"{p['lat']:.7f}, {p['lng']:.7f}, {img}, 0, 0, NOW(), NOW())"
        )
        for row in rates:
            if row["tAtsNm"] == p["name"]:
                date = f"{row['baseYmd'][:4]}-{row['baseYmd'][4:6]}-{row['baseYmd'][6:]}"
                forecast_rows.append(f"({i}, '{date}', {float(row['cnctrRate'])}, NOW(), NOW())")

    lines = [
        "-- 자동 생성: backend/scripts/build_seed_sql.py - 직접 수정 금지",
        "-- 원천: TourAPI 실측 스냅샷 + 관광공사 집중률 30일 실측 (2026-08-05 호출)",
        "-- INSERT IGNORE: 유니크 제약(content_id, place_id+base_date)으로 재실행 시 중복 스킵",
        "",
        "INSERT IGNORE INTO place (id, content_id, name, region, category, address, "
        "latitude, longitude, image_url, good_price_store, hidden_gem, create_date, update_date) VALUES",
        ",\n".join(place_rows) + ";",
        "",
        "INSERT IGNORE INTO congestion_forecast (place_id, base_date, rate, create_date, update_date) VALUES",
        ",\n".join(forecast_rows) + ";",
        "",
    ]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"장소 {len(place_rows)}곳 / 예보 {len(forecast_rows)}행 → {OUT.relative_to(BACKEND)}")
    print("데모 스팟 포함:", [p["name"] for p in demo][:12])


if __name__ == "__main__":
    main()
