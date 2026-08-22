package com.example.hangat.course;

import com.example.hangat.course.model.TourApiResponseDto;
import com.example.hangat.course.model.TourPlaceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Service
public class TourApiService {

    @Value("${tour-api.base-url}")
    private String baseUrl;

    @Value("${tour-api.service-key}")
    private String serviceKey;

    private final RestClient restClient = RestClient.create();

    public List<TourPlaceDto> getTourPlaces() {

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "Hangat")
                .queryParam("_type", "json")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 10)
                .queryParam("areaCode", 39)
                .encode()
                .buildAndExpand(serviceKey)
                .toUri();

        TourApiResponseDto result = restClient.get()
                .uri(uri)
                .retrieve()
                .body(TourApiResponseDto.class);

        return result.getResponse()
                .getBody()
                .getItems()
                .getItem();
    }
}
