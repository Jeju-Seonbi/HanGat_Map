package com.example.hangat.course;

import com.example.hangat.course.model.TourApiResponseDto;
import com.example.hangat.course.model.TourPlaceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TourApiService {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 3;
    static final int MAX_RAW_CANDIDATES = PAGE_SIZE * MAX_PAGES;

    private final String baseUrl;
    private final String serviceKey;
    private final RestClient restClient;

    protected TourApiService() {
        this(RestClient.create(), null, null);
    }

    @Autowired
    public TourApiService(
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.service-key}") String serviceKey
    ) {
        this(RestClient.create(), baseUrl, serviceKey);
    }

    TourApiService(RestClient restClient, String baseUrl, String serviceKey) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public List<TourPlaceDto> getTourPlaces() {
        Map<String, TourPlaceDto> uniquePlaces = new LinkedHashMap<>();
        int fetchedCount = 0;

        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            TourApiResponseDto.Body body = fetchPage(pageNo);
            List<TourPlaceDto> pageItems = pageItems(body);
            if (pageItems.isEmpty()) {
                break;
            }

            fetchedCount += pageItems.size();
            for (TourPlaceDto place : pageItems) {
                if (place == null || place.getContentId() == null || place.getContentId().isBlank()) {
                    continue;
                }
                uniquePlaces.putIfAbsent(place.getContentId(), place);
                if (uniquePlaces.size() >= MAX_RAW_CANDIDATES) {
                    return List.copyOf(uniquePlaces.values());
                }
            }

            Integer totalCount = body.getTotalCount();
            if (totalCount != null && fetchedCount >= totalCount) {
                break;
            }
        }

        return List.copyOf(uniquePlaces.values());
    }

    private TourApiResponseDto.Body fetchPage(int pageNo) {
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "Hangat")
                .queryParam("_type", "json")
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("arrange", "C")
                .queryParam("areaCode", 39)
                .encode()
                .buildAndExpand(serviceKey)
                .toUri();

        TourApiResponseDto result = restClient.get()
                .uri(uri)
                .retrieve()
                .body(TourApiResponseDto.class);

        if (result == null || result.getResponse() == null) {
            return null;
        }
        return result.getResponse().getBody();
    }

    private List<TourPlaceDto> pageItems(TourApiResponseDto.Body body) {
        if (body == null
                || body.getItems() == null
                || body.getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(body.getItems().getItem());
    }
}
