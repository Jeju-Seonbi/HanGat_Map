# 한갓지도 백엔드 (hangat-api)

Spring Boot 3.4.4 · Java 17 · Gradle

## 로컬 실행

```bash
cp .env.example .env   # 값 채우기 (.env는 커밋 금지)
./gradlew bootRun      # 기본 dev 프로필, http://localhost:8080
```

- 헬스체크: `GET /actuator/health`
- 프로필: `application.yaml`(active 지정) + `application-dev.yaml` / `application-prod.yaml`
- 비밀값은 전부 `${ENV}` 치환 - 로컬은 `.env`, 운영(OCI)은 서버 환경변수
