package com.example.hangat.map.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 접속 설정 - 설계서 §3.2
 *
 * <p>{@code serviceKey}는 날씨(jdh)가 쓰는 것과 <b>같은 키</b>다. 포털은 계정당 인증키 1개를 주고
 * 활용신청한 API 전부에 그 키가 통한다. 그래서 {@code .env}의 {@code SERVICE_KEY} 하나를 공유한다.
 *
 * <p>★ 반드시 <b>Decoding 키</b>를 넣는다. Encoding 키를 넣으면 {@link PublicApiClient}가
 * 한 번 더 인코딩해 이중 인코딩이 되고 코드 30(미등록 키)이 난다.
 */
@ConfigurationProperties(prefix = "public-api")
public record PublicApiProperties(

        /** 공공데이터포털 인증키(Decoding). */
        String serviceKey,

        /** 한국관광공사 계열 공통 호스트. KorService2·집중률·방문자수가 모두 이 아래에 있다. */
        String tourBaseUrl
) {
}
