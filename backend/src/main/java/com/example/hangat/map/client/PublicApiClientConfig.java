package com.example.hangat.map.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 공공 API 전용 RestClient.
 *
 * <p>팀 공통 {@code config/RestClientConfig}에 얹지 않고 맵 도메인 안에 두는 이유:
 * 타임아웃 성격이 다르다. 날씨는 응답이 작고 빠르지만, 관광정보 목록은 한 번에 1,000건씩 받아
 * 본문이 수백 KB라 읽기 시간이 더 필요하다.
 */
@Configuration
public class PublicApiClientConfig {

    @Bean
    public RestClient publicApiRestClient(PublicApiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        // 1,000건 페이지는 응답이 커서 날씨(5초)보다 넉넉하게 잡는다
        factory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .baseUrl(properties.tourBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
