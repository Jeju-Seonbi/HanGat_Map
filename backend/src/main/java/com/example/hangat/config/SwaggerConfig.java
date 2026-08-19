package com.example.hangat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("한갓지도 API")
                        .description("""
                                제주 오버투어리즘 분산 코스 추천 서비스 API 문서.

                                - 혼잡 예보는 **날짜 단위**로만 제공한다 (시간대 단위 예측 없음)
                                - 인증(JWT)은 회원 기능 도입 시 활성화 예정
                                """)
                        .version("v0.1"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 도입 후 사용 - 발급된 토큰을 입력하세요.")));
    }
}
