package com.example.hangat.course;

import com.example.hangat.course.model.CongestionApiResponseDto;
import com.example.hangat.course.model.CongestionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Service
public class CongestionApiService {

    @Value("${congestion-api.base-url}")
    private String baseUrl;

    @Value("${congestion-api.service-key}")
    private String serviceKey;

    private final RestClient restClient = RestClient.create();

    public List<CongestionDto> getCongestionData(
            String signguCd,
            String touristAttractionName
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 30)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "Hangat")
                .queryParam("areaCd", "50")
                .queryParam("signguCd", "{signguCd}")
                .queryParam("tAtsNm", "{touristAttractionName}")
                .queryParam("_type", "json")
                .encode()
                .buildAndExpand(
                        serviceKey,
                        signguCd,
                        touristAttractionName
                )
                .toUri();

        CongestionApiResponseDto result = restClient.get()
                .uri(uri)
                .retrieve()
                .body(CongestionApiResponseDto.class);

        return result.getResponse()
                .getBody()
                .getItems()
                .getItem();
    }
}
