# 한갓지도 백엔드 (hangat-api)

Spring Boot 3.4.4 · Java 17 · Gradle

## 로컬 실행

```bash
cp .env.example .env    # 값 채우기 (.env는 커밋 금지)
docker compose up -d    # 로컬 MariaDB (직접 설치했다면 생략, .env로 접속 정보만 맞추기)
./gradlew bootRun       # 기본 dev 프로필, http://localhost:8080
```

- 테스트(`./gradlew test`)는 인메모리 H2로 돌아서 DB 없이도 항상 통과

- 헬스체크: `GET /actuator/health`
- 프로필: `application.yaml`(active 지정) + `application-dev.yaml` / `application-prod.yaml`
- 비밀값은 전부 `${ENV}` 치환 - 로컬은 `.env`, 운영(OCI)은 서버 환경변수
