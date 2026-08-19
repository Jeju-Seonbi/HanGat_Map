package com.example.hangat;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HangatApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(HangatApplication.class, args);
    }

    /**
     * backend/.env 파일을 시스템 프로퍼티로 로드한다 (yaml의 ${...} 치환용).
     * - .env가 없으면 조용히 넘어간다 (배포 환경은 실제 환경변수 사용)
     * - 이미 설정된 실제 환경변수가 항상 우선한다
     */
    private static void loadDotenv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }
}
